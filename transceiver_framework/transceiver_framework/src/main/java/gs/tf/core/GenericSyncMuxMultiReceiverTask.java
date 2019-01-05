/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class GenericSyncMuxMultiReceiverTask<DATA_IN_TYPE extends Comparable<DATA_IN_TYPE>> extends GenericMuxMultiReceiverTask<DATA_IN_TYPE> {
    private static final Logger LOGGER = Logger.getLogger(GenericSyncMuxMultiReceiverTask.class.getName());
    
    // TODO: Better abstraction and less redundancy between SyncMuxTransmitterTask and SequentialMuxTransmitterTask
    //       - move more common functionality into AbstractMuxTransmitterTask (e.g., postWork())
    protected class SyncMuxTransmitterTask extends AbstractMuxTransmitterTask{


        private DATA_IN_TYPE m_lastElement;
        private final Map<Integer, DATA_IN_TYPE> m_singleElementBuffer; // i-th entry refers to i-th receiver mux
        private Long m_discardTimeoutInterval;
        private long m_lastEmitTime;
        private final Random m_rnd;

        public SyncMuxTransmitterTask(int maxDataChunkSize, int numMuxReceivers, Long discardTimeoutInterval, Long rnd_seed) {
            super(maxDataChunkSize, numMuxReceivers);
            this.m_lastElement = null;
            this.m_singleElementBuffer = new HashMap<>();
            if(discardTimeoutInterval != null && discardTimeoutInterval < 0){
                throw new IllegalArgumentException();
            }
            this.m_discardTimeoutInterval = discardTimeoutInterval;
            this.m_lastEmitTime = -1;

            if(rnd_seed == null){
                this.m_rnd = new Random();
            }
            else{
                this.m_rnd = new Random(rnd_seed);
            }
        }


        @Override
        protected Collection<Pair<Integer, DATA_IN_TYPE>> getNextDataChunk() {
            assert(GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.size() == GenericSyncMuxMultiReceiverTask.this.getNumInternalTasks());
            assert(GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.size() == m_numMuxReceivers);
            DATA_IN_TYPE smallestElement = null;
            ArrayList<Pair<Integer, DATA_IN_TYPE>> smallestElements = new ArrayList<>(this.m_numMuxReceivers);

            DATA_IN_TYPE currentElement;

            assert(this.m_numMuxReceivers == GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.size());
            MuxReceiverTask<DATA_IN_TYPE> muxReceiver;
            boolean hasKey, emptyQueueEncountered = false;
            boolean allEmpty = true;
            for(int recNum = 0; recNum < this.m_numMuxReceivers; recNum++){
                muxReceiver = GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.get(recNum);
                assert(this.isRunning() || muxReceiver.isTerminated());

                assert(GenericSyncMuxMultiReceiverTask.this.getInternalTaskByIndex(recNum) == muxReceiver);

                hasKey = this.m_singleElementBuffer.containsKey(recNum);

                if(!hasKey && muxReceiver.m_inQueue.size() == 0){
                    emptyQueueEncountered = true;
                }
                else{
                    allEmpty = false;
                    if(hasKey){
                        currentElement = this.m_singleElementBuffer.get(recNum);

                    }
                    else {
                        assert(muxReceiver.m_inQueue.size() > 0);
                        currentElement = muxReceiver.m_inQueue.poll();
                        assert(currentElement != null);
                        this.m_singleElementBuffer.put(recNum, currentElement);
                    }

                    if(smallestElement == null){
                        assert(smallestElements.isEmpty());
                        smallestElement = currentElement;
                        smallestElements.add(new ImmutablePair<>(recNum, currentElement));
                    }
                    else if(currentElement.compareTo(smallestElement) == 0){
                        smallestElements.add(new ImmutablePair<>(recNum, currentElement));
                    }
                    else if (currentElement.compareTo(smallestElement) < 0){
                        smallestElements.clear();
                        smallestElement = currentElement;
                        smallestElements.add(new ImmutablePair<>(recNum, currentElement));
                    }
                }
            }

            Pair<Integer, DATA_IN_TYPE> bestResultPair;
            smallestElement = null;
            Integer bestResult = null;

            if(smallestElements.size() > 0) {
                bestResultPair = smallestElements.get(this.m_rnd.nextInt(smallestElements.size()));
                smallestElement = bestResultPair.getRight();
                bestResult = bestResultPair.getLeft();
            }
            else{
                assert(allEmpty);
            }

            if(this.m_discardTimeoutInterval != null &&
            		// 1. No timeout if no queue is empty
            		// 2. No timeout if this code line reached for the first time,
            		// 3. No timeout ALL queues are empty
                    (!emptyQueueEncountered || this.m_lastEmitTime < 0 || smallestElement == null)){
                this.m_lastEmitTime = System.currentTimeMillis();
            }

            if((!emptyQueueEncountered || this.isTerminating()) && !allEmpty) { // If terminating -> output remaining elements
                assert (smallestElement != null);

                if (this.m_lastElement == null || smallestElement.compareTo(this.m_lastElement) >= 0) {
                    this.m_lastElement = smallestElement;
                    assert(this.m_singleElementBuffer.containsKey(bestResult));
                    this.m_singleElementBuffer.remove(bestResult);
                } else {
                    throw new IllegalStateException("Data in input queues is not ordered.");
                }
                List<Pair<Integer, DATA_IN_TYPE>> result = new ArrayList<>(1);
                result.add(new ImmutablePair<>(bestResult, smallestElement));
                return result;
            }
            else{
                if (this.m_discardTimeoutInterval != null && smallestElement != null &&
                        System.currentTimeMillis() - this.m_lastEmitTime >= this.m_discardTimeoutInterval) {
                    LOGGER.warning("Discarding currently oldest element due to timeout.");
                    // Do not set m_lastElement to smallest element, since at this point, a value older than the discarded one
                    // might still occur
                    if (this.m_lastElement == null || smallestElement.compareTo(this.m_lastElement) >= 0) {
                        assert (this.m_singleElementBuffer.containsKey(bestResult));
                        this.m_singleElementBuffer.remove(bestResult);
                        // No reset of lastEmitTime! ->
                        // Timout for all subsequent values until one of the three conditions above are met
                        //this.m_lastEmitTime = System.currentTimeMillis();
                    } else {
                        throw new IllegalStateException("Data in input queues is not ordered.");
                    }
                }
                return null;
            }
        }

        @Override
        protected void postWork(){
            assert(this.terminateCalledWithInterrupt() != null);
            if(this.terminateCalledWithInterrupt()) {
                int numElems = this.m_singleElementBuffer.values().size();
                MuxReceiverTask<DATA_IN_TYPE> muxReceiver;
                for(int recNum = 0; recNum < this.m_numMuxReceivers; recNum++) {
                    muxReceiver = GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.get(recNum);
                    assert (GenericSyncMuxMultiReceiverTask.this.getInternalTaskByIndex(recNum) == muxReceiver);
                    assert(muxReceiver.isTerminated());
                    numElems += muxReceiver.m_inQueue.size();
                }

                ArrayList<DATA_IN_TYPE> remainingData = new ArrayList<>(numElems);
                for(int recNum = 0; recNum < this.m_numMuxReceivers; recNum++) {
                    muxReceiver = GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.get(recNum);
                    muxReceiver.m_inQueue.drainTo(remainingData);
                }
                remainingData.addAll(this.m_singleElementBuffer.values());
                this.m_singleElementBuffer.clear();

                if (remainingData.size() > 0) {
                    LOGGER.warning("Discarding " + remainingData.size() + " elements from input queues due to interrupt-termination.");
                }
            }
            else{
                MuxReceiverTask<DATA_IN_TYPE> muxReceiver;
                boolean oneNonEmpty = true;

                while(oneNonEmpty) {
                    oneNonEmpty = !this.m_singleElementBuffer.isEmpty();
                    for (int i = 0; i < this.m_numMuxReceivers && !oneNonEmpty; i++) {
                        muxReceiver = GenericSyncMuxMultiReceiverTask.this.m_muxReceiverList.get(i);
                        assert (GenericSyncMuxMultiReceiverTask.this.getInternalTaskByIndex(i) == muxReceiver);
                        assert(muxReceiver.isTerminated());

                        if (muxReceiver.m_inQueue.size() > 0) {
                            oneNonEmpty = true;
                        }
                    }
                    if(oneNonEmpty) {
                        try {
                            this.forwardToReceiver(this.getNextDataChunk());
                        } catch (InterruptedException e) {
                            throw new IllegalStateException("No interrupt should happen at this point.", e);
                        } catch (IllegalStatusException e) {
                            throw new RuntimeException("Should not happen here.", e);
                        }
                    }
                }
            }
            super.postWork();
            assert(this.allReceiversEmpty());
        }
    }

    // Note: The data in each of the input queues must be ordered s.th. retrieved elements are in ascending order!
    public GenericSyncMuxMultiReceiverTask(AbstractTargetReceiverTask<DATA_IN_TYPE> targetReceiver,
                                           int numInternalReceivers,
                                           int maxDataChunkSize,
                                           Integer inDataQueueCapacity,
                                           Long timeoutInterval,
                                           ExecutorService executorService,
                                           Object... additionalMuxTransmitterArgs) {
        super(targetReceiver, numInternalReceivers, maxDataChunkSize, inDataQueueCapacity, timeoutInterval, executorService, additionalMuxTransmitterArgs);
    }

    @Override
    protected AbstractMuxTransmitterTask createMuxTransmitterTask(int maxDataChunkSize, int numMuxReceivers, Object... additionalArgs){
        if(additionalArgs == null){
            throw new NullPointerException();
        }
        else if(additionalArgs.length != 2) {
            throw new NullPointerException();
        }
        else if(!(additionalArgs[0] instanceof Long) || !(additionalArgs[1] instanceof Long)) {
            throw new IllegalArgumentException();
        }

        return new SyncMuxTransmitterTask(maxDataChunkSize, numMuxReceivers, (Long) additionalArgs[0], (Long) additionalArgs[1]);
    }
}
