/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

// TODO GenericMuxMultiTransceiverTask & GenericDemuxMultiTransceiverTask

// Mux = Multiplexer = From multiple (transmitters) to single target (receiver)
public class GenericMuxMultiReceiverTask<DATA_IN_TYPE> 
	extends 
		GenericMultiReceiverTask<
			DATA_IN_TYPE,
                ReceiverTask<DATA_IN_TYPE>> {

    public abstract static class AbstractTargetReceiverTask<DATA_IN_TYPE> extends AbstractReceiverTask<Pair<Integer, DATA_IN_TYPE>> {

        private final int m_numMuxTransmitters;

        public AbstractTargetReceiverTask(Integer inDataQueueCapacity, Long timeoutInterval, int numMuxTransmitters) {
            super(inDataQueueCapacity, timeoutInterval);

            if(numMuxTransmitters <= 0){
                throw new IllegalArgumentException("Number of demux transmitters must be positive.");
            }
            this.m_numMuxTransmitters = numMuxTransmitters;
        }

        public int getNumMuxTransmitters(){
            return this.m_numMuxTransmitters;
        }

        @Override
        protected void processDataElement(Pair<Integer, DATA_IN_TYPE> dataElement) throws InterruptedException {
            assert(dataElement != null);

            int sourceIndex = dataElement.getLeft();
            DATA_IN_TYPE data = dataElement.getRight();

            if(sourceIndex < 0 || sourceIndex >= this.m_numMuxTransmitters){
                throw new IllegalArgumentException();
            }
            this.processDataElementFromMux(sourceIndex, data);

        }

        protected abstract void processDataElementFromMux(int muxIndex, DATA_IN_TYPE dataElement) throws InterruptedException;
    }

    protected static class MuxReceiverTask<DATA_IN_TYPE> extends AbstractReceiverTask<DATA_IN_TYPE>{

        private static final Logger LOGGER = Logger.getLogger(MuxReceiverTask.class.getName());

        final BlockingQueue<DATA_IN_TYPE> m_inQueue;

        public MuxReceiverTask(Integer inDataQueueCapacity, Long timeoutInterval) {
            super(inDataQueueCapacity, timeoutInterval);
            if(inDataQueueCapacity == null){
                this.m_inQueue = new LinkedBlockingQueue<>();
            }
            else{
                if(inDataQueueCapacity < 1){
                    throw new IllegalArgumentException();
                }

                this.m_inQueue = new ArrayBlockingQueue<>(inDataQueueCapacity, true);
            }
        }

        @Override
        protected void processDataElement(DATA_IN_TYPE dataElement) throws InterruptedException {
            this.m_inQueue.put(dataElement);
        }

        @Override
        protected void postWork(){
            super.postWork();
            assert(this.terminateCalledWithInterrupt() != null);
            if(this.terminateCalledWithInterrupt()) {
                ArrayList<DATA_IN_TYPE> remainingData = new ArrayList<>(this.m_inQueue.size());
                this.m_inQueue.drainTo(remainingData);
                if (remainingData.size() > 0) {
                    LOGGER.warning("Discarding " + remainingData.size() + " elements from input queue due to interrupt-termination.");
                }
                assert(this.m_inQueue.isEmpty());
            }
            //else{
                // Sequential/sync Mux transmitter task responsible for draining queue after the receiver has terminated
            //}
        }
    }

    protected abstract class AbstractMuxTransmitterTask extends AbstractTransmitterTask<Pair<Integer, DATA_IN_TYPE>> {
        protected final int m_numMuxReceivers;

        public AbstractMuxTransmitterTask(int maxDataChunkSize, int numMuxReceivers) {
            super(maxDataChunkSize);
            if(numMuxReceivers <= 0){
                throw new IllegalArgumentException("Number of mux receivers must be positive.");
            }
            this.m_numMuxReceivers = numMuxReceivers;
        }


        protected boolean allReceiversEmpty() {
            boolean result = true;
            MuxReceiverTask<DATA_IN_TYPE> muxReceiver;
            for (int i = 0; i < this.m_numMuxReceivers && result; i++) {
                muxReceiver = GenericMuxMultiReceiverTask.this.m_muxReceiverList.get(i);
                result = muxReceiver.m_inQueue.isEmpty();
            }
            return result;
        }
    }

    protected class SequentialMuxTransmitterTask extends AbstractMuxTransmitterTask{

    	// No need for volatile, only accessed in getNextDataChunk by executing Thread
        private int m_nextIndex;

        public SequentialMuxTransmitterTask(int maxDataChunkSize, int numMuxReceivers) {
            super(maxDataChunkSize, numMuxReceivers);
            this.m_nextIndex = 0;
        }

        @Override
        protected Collection<Pair<Integer, DATA_IN_TYPE>> getNextDataChunk() {
            assert(GenericMuxMultiReceiverTask.this.m_muxReceiverList.size() == GenericMuxMultiReceiverTask.this.getNumInternalTasks());
            assert(GenericMuxMultiReceiverTask.this.m_muxReceiverList.size() == this.m_numMuxReceivers);
            Integer nextMuxReceiverIndex = this.getNextMuxReceiverIndex();

            if(nextMuxReceiverIndex == null){
                return new ArrayList<>(0);
            }

            if(nextMuxReceiverIndex < 0 || nextMuxReceiverIndex >= GenericMuxMultiReceiverTask.this.getNumInternalTasks()){
                throw new IllegalArgumentException();
            }

            MuxReceiverTask<DATA_IN_TYPE> muxReceiver =  GenericMuxMultiReceiverTask.this.m_muxReceiverList.get(nextMuxReceiverIndex);

            assert(GenericMuxMultiReceiverTask.this.getInternalTaskByIndex(nextMuxReceiverIndex) == muxReceiver);

            List<DATA_IN_TYPE> dataChunk = new LinkedList<>();
            muxReceiver.m_inQueue.drainTo(dataChunk, this.getMaxDataChunkSize());

            List<Pair<Integer, DATA_IN_TYPE>> result = new ArrayList<>(dataChunk.size());
            for(DATA_IN_TYPE data : dataChunk){
                result.add(new ImmutablePair<>(nextMuxReceiverIndex, data));
            }

            return result;
        }

        private Integer getNextMuxReceiverIndex() {
            assert(this.m_nextIndex >= 0 && this.m_nextIndex < this.m_numMuxReceivers);
            int result = this.m_nextIndex;

            this.m_nextIndex = (this.m_nextIndex + 1) % this.m_numMuxReceivers;

            return result;
        }


        @Override
        protected void postWork(){
            assert(this.terminateCalledWithInterrupt() != null);
            MuxReceiverTask<DATA_IN_TYPE> muxReceiver;
            if(this.terminateCalledWithInterrupt()) {
                int size = 0;
                for(int i=0; i < this.m_numMuxReceivers; i++) {
                    muxReceiver = GenericMuxMultiReceiverTask.this.m_muxReceiverList.get(i);
                    assert (GenericMuxMultiReceiverTask.this.getInternalTaskByIndex(i) == muxReceiver);
                    assert(muxReceiver.isTerminated());
                    size += muxReceiver.m_inQueue.size();
                }
                ArrayList<DATA_IN_TYPE> remainingData = new ArrayList<>(size);
                for(int i=0; i < this.m_numMuxReceivers; i++) {
                    muxReceiver = GenericMuxMultiReceiverTask.this.m_muxReceiverList.get(i);
                    muxReceiver.m_inQueue.drainTo(remainingData);
                }

                if (remainingData.size() > 0) {
                    LOGGER.warning("Discarding " + remainingData.size() + " elements from input queue due to interrupt-termination.");
                }
            }
            else {
                boolean oneNonEmpty = true;
                while (oneNonEmpty) {
                    oneNonEmpty = false;
                    for (int i = 0; i < this.m_numMuxReceivers; i++) {
                        muxReceiver = GenericMuxMultiReceiverTask.this.m_muxReceiverList.get(i);
                        assert (GenericMuxMultiReceiverTask.this.getInternalTaskByIndex(i) == muxReceiver);
                        assert(muxReceiver.isTerminated());
                        if (muxReceiver.m_inQueue.size() > 0) {
                            oneNonEmpty = true;
                        }
                    }
                    try {
                        this.forwardToReceiver(this.getNextDataChunk());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("No interrupt should happen at this point.", e);
                    } catch (IllegalStatusException e) {
                        throw new RuntimeException("Should not happen here.", e);
                    }
                }
            }
            super.postWork();
            assert(this.allReceiversEmpty());
        }
    }

    private static <DATA_IN_TYPE> List<ReceiverTask<DATA_IN_TYPE>> createInternalReceiverTasks(
            int numInternalReceivers,
            Integer inDataQueueCapacity,
            Long timeoutInterval){
        if(numInternalReceivers <= 0){
            throw new IllegalArgumentException();
        }
        List<ReceiverTask<DATA_IN_TYPE>> result = new ArrayList<>(numInternalReceivers);
        for(int i = 0; i < numInternalReceivers; i++){
            result.add(new MuxReceiverTask<>(inDataQueueCapacity, timeoutInterval));
        }
        return result;
    }

    protected final UnmodifiableList<MuxReceiverTask<DATA_IN_TYPE>> m_muxReceiverList;
    private final ExecutorService m_executorService;
    private final AbstractTargetReceiverTask<DATA_IN_TYPE> m_targetReceiver;
    private final AbstractMuxTransmitterTask m_muxTransmitter;

    private static final Logger LOGGER = Logger.getLogger(GenericMuxMultiReceiverTask.class.getName());

    @SuppressWarnings("unchecked")
    public GenericMuxMultiReceiverTask(
            AbstractTargetReceiverTask<DATA_IN_TYPE> targetReceiver,
            int numInternalReceivers,
            int maxDataChunkSize,
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            ExecutorService executorService,
            Object... additionalMuxTransmitterArgs) {
        super(createInternalReceiverTasks(numInternalReceivers, inDataQueueCapacity, timeoutInterval),
                executorService);
        this.m_executorService = executorService;

        if(targetReceiver == null){
            throw new NullPointerException();
        }
        this.m_targetReceiver = targetReceiver;
        if(targetReceiver.isRunning() || targetReceiver.hasInConnection()){
            throw new IllegalArgumentException();
        }
        if(numInternalReceivers != targetReceiver.getNumMuxTransmitters()){
            throw new IllegalArgumentException();
        }

        assert(numInternalReceivers == this.getNumInternalTasks());
        List<MuxReceiverTask<DATA_IN_TYPE>> muxReceiverList = new ArrayList<>(numInternalReceivers);
        for(int i = 0; i < numInternalReceivers; i++){
            ReceiverTask<? super DATA_IN_TYPE> receiverTask = this.getInternalTaskByIndex(i);
            assert(receiverTask instanceof MuxReceiverTask);
            muxReceiverList.add((MuxReceiverTask<DATA_IN_TYPE>) receiverTask);
        }
        this.m_muxReceiverList = (UnmodifiableList<MuxReceiverTask<DATA_IN_TYPE>>) UnmodifiableList.unmodifiableList(muxReceiverList);

        AbstractMuxTransmitterTask muxTransmitterTask = this.createMuxTransmitterTask(maxDataChunkSize, numInternalReceivers, additionalMuxTransmitterArgs);
        if(muxTransmitterTask == null){
            throw new NullPointerException();
        }

        if(muxTransmitterTask.isRunning() || muxTransmitterTask.hasOutConnection()){
            throw new IllegalStateException();
        }
        this.m_muxTransmitter = muxTransmitterTask;
        try {
            this.m_muxTransmitter.setOutConnection(this.m_targetReceiver);
        } catch (UnacceptedConcurrentTaskException e) {
            throw new RuntimeException("This should not have happened.", e);
        }
    }

    protected AbstractMuxTransmitterTask createMuxTransmitterTask(int maxDataChunkSize, int numMuxReceivers, Object... additionalArgs){
        if(additionalArgs != null && additionalArgs.length != 0){
            throw new IllegalArgumentException();
        }

        return new SequentialMuxTransmitterTask(maxDataChunkSize, numMuxReceivers);
    }

    @Override
    protected void preWork() {
        super.preWork(); // Starts mux receivers
        assert(this.m_muxTransmitter.isNotStarted());
        assert(this.m_muxTransmitter.hasOutConnection());
        // There seem to be an error during "rebuild" compilation if the cast below (which in itself SHOULD be unnecessary) is not included - a compiler bug?
        assert(this.m_muxTransmitter.getTargetReceiverTask() == (ReceiverTask<Pair<Integer, DATA_IN_TYPE>>)this.m_targetReceiver);

        this.m_executorService.submit(this.m_muxTransmitter);
        while(this.m_muxTransmitter.isNotStarted()){
            Thread.yield();
        }

        assert(this.m_targetReceiver.isNotStarted());
        assert(this.m_targetReceiver.hasInConnection());
        assert(this.m_targetReceiver.getSourceTransmitterTask() == this.m_muxTransmitter);
        this.m_executorService.submit(this.m_targetReceiver);
        while(this.m_targetReceiver.isNotStarted()){
            Thread.yield();
        }
    }

    @Override
    protected void postWork() {
        assert(this.terminateCalledWithInterrupt() != null);
        super.postWork(); // Terminates mux receivers
        assert(this.m_muxTransmitter.isRunning());
        try {
            this.m_muxTransmitter.terminate(this.terminateCalledWithInterrupt());
        } catch (NotStartedException e) {
            throw new IllegalStateException("This should not happen.", e);
        }
        while(!this.m_muxTransmitter.isTerminated()){
            Thread.yield();
        }

        assert(this.m_targetReceiver.isRunning());
        try {
            this.m_targetReceiver.terminate(this.terminateCalledWithInterrupt());
        } catch (NotStartedException e) {
            throw new IllegalStateException("This should not happen.", e);
        }
        while(!this.m_targetReceiver.isTerminated()){
            Thread.yield();
        }
    }
}
