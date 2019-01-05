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

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
// Demux = Demultiplexer = From single source to multiple targets (transmitters)
public class GenericDemuxMultiTransmitterTask<DATA_OUT_TYPE> 
	extends 
		GenericMultiTransmitterTask<
			DATA_OUT_TYPE,
                TransmitterTask<DATA_OUT_TYPE>> {

    public abstract static class AbstractSourceTransmitterTask<DATA_OUT_TYPE> extends AbstractTransmitterTask<Pair<Integer, DATA_OUT_TYPE>> {

        private final int m_numDemuxTransmitters;
        // No volatile necessary, access only via Thread executing this task
        private int m_nextOutIndex;

        public AbstractSourceTransmitterTask(int maxDataChunkSize, int numDemuxTransmitters) {
            super(maxDataChunkSize);
            if(numDemuxTransmitters <= 0){
                throw new IllegalArgumentException("Number of demux transmitters must be positive.");
            }
            this.m_numDemuxTransmitters = numDemuxTransmitters;
            this.m_nextOutIndex = 0;
        }

        public int getNumDemuxTransmitters(){
            return this.m_numDemuxTransmitters;
        }

        @Override
        protected Collection<Pair<Integer, DATA_OUT_TYPE>> getNextDataChunk() {
            assert(this.m_nextOutIndex >= 0 && this.m_nextOutIndex < this.m_numDemuxTransmitters);
            Collection<DATA_OUT_TYPE> data = this.getNextDataChunkForDemux(this.m_nextOutIndex);

            int dataNum = data == null ? 0 : data.size();
            List<Pair<Integer, DATA_OUT_TYPE>> result = new ArrayList<>(dataNum);
            if(data != null) {
                for (DATA_OUT_TYPE element : data) {
                    result.add(new ImmutablePair<>(this.m_nextOutIndex, element));
                }
            }

            this.m_nextOutIndex = (this.m_nextOutIndex + 1) % this.m_numDemuxTransmitters;

            return result;
        }

        protected abstract Collection<DATA_OUT_TYPE> getNextDataChunkForDemux(int demuxIndex);
    }

    private class DemuxReceiverTask extends AbstractReceiverTask<Pair<Integer, DATA_OUT_TYPE>> {
        private final Long m_timeoutInterval;
        // No volatile necessary, access only via Thread executing this task
        private boolean m_lastWasTimeout;
        private final int m_numDemuxTransmitters;

        public DemuxReceiverTask(Integer inDataQueueCapacity, Long timeoutInterval, int numDemuxTransmitters) {
            super(inDataQueueCapacity, timeoutInterval);
            this.m_timeoutInterval = timeoutInterval;
            this.m_lastWasTimeout = false;
            this.m_numDemuxTransmitters = numDemuxTransmitters;
        }

        @Override
        protected void processDataElement(Pair<Integer, DATA_OUT_TYPE> dataElement) throws InterruptedException {
            assert(dataElement != null);
            int demuxTransmitterIndex = dataElement.getLeft();
            assert(dataElement.getLeft() >= 0 && dataElement.getLeft() < this.m_numDemuxTransmitters);
            BlockingQueue<DATA_OUT_TYPE> outQueue = 
            		GenericDemuxMultiTransmitterTask.this.m_demuxTransmitterList.get(demuxTransmitterIndex).m_outQueue;
            if(this.m_timeoutInterval == null) {
            	outQueue.put(dataElement.getRight());
            }
            else{
                boolean timeout = this.m_lastWasTimeout;
                if(outQueue.offer(dataElement.getRight())){
                	timeout = false;
                }
                else if(!this.m_lastWasTimeout){
                	timeout = !outQueue.offer(
                					dataElement.getRight(), 
                					this.m_timeoutInterval, 
                					TimeUnit.MILLISECONDS);
                }
                
                if(timeout){
                    LOGGER.warning("Discarding input element to "+demuxTransmitterIndex+"-th demux transmitter's "
                    		+ "input queue due to timeout in "+
                            this.getClass().getName()+", running in thread with ID "+this.getRunThreadID()+" and name "+
                            this.getRunThreadName()+ ".");
                }
                
                this.m_lastWasTimeout = timeout;
            }
        }
    }

    private static class DemuxTransmitterTask<DATA_OUT_TYPE> extends AbstractTransmitterTask<DATA_OUT_TYPE>{

    	final BlockingQueue<DATA_OUT_TYPE> m_outQueue;

        public DemuxTransmitterTask(int maxDataChunkSize, Integer inDataQueueCapacity) {
            super(maxDataChunkSize);
            if(inDataQueueCapacity == null){
                this.m_outQueue = new LinkedBlockingQueue<>();
            }
            else{
                if(inDataQueueCapacity < 1){
                    throw new IllegalArgumentException();
                }
                this.m_outQueue = new ArrayBlockingQueue<>(inDataQueueCapacity, true);
            }
        }

        @Override
        protected Collection<DATA_OUT_TYPE> getNextDataChunk() {
            assert(this.m_outQueue != null);
            List<DATA_OUT_TYPE> dataChunk = new LinkedList<>();
            this.m_outQueue.drainTo(dataChunk, this.getMaxDataChunkSize());

            return dataChunk;
        }

        @Override
        protected void postWork(){
            assert(this.m_outQueue != null);
            assert(this.terminateCalledWithInterrupt() != null);
            if(this.terminateCalledWithInterrupt()) {
                ArrayList<DATA_OUT_TYPE> remainingData = new ArrayList<>(this.m_outQueue.size());
                this.m_outQueue.drainTo(remainingData);
                if (remainingData.size() > 0) {
                    LOGGER.warning("Discarding " + remainingData.size() + " elements from input queue due to interrupt-termination.");
                }
            }
            else{
                while(this.m_outQueue.size() > 0){
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
            assert(this.m_outQueue.isEmpty());
        }
    }

    private final UnmodifiableList<DemuxTransmitterTask<DATA_OUT_TYPE>> m_demuxTransmitterList;
    private final AbstractSourceTransmitterTask<DATA_OUT_TYPE> m_sourceTransmitter;
    private final ExecutorService m_executorService;
    private final DemuxReceiverTask m_demuxReceiver;

    private static final Logger LOGGER = Logger.getLogger(GenericDemuxMultiTransmitterTask.class.getName());

    private static <DATA_OUT_TYPE> List<TransmitterTask<DATA_OUT_TYPE>> createInternalTransmitterTasks(
            int numInternalTransmitters,
            int maxDataChunkSize,
            Integer inDataQueueCapacity) {
        if(numInternalTransmitters <= 0){
            throw new IllegalArgumentException();
        }
        List<TransmitterTask<DATA_OUT_TYPE>> result = new ArrayList<>(numInternalTransmitters);
        for(int i = 0; i < numInternalTransmitters; i++){
            result.add(new DemuxTransmitterTask<>(maxDataChunkSize, inDataQueueCapacity));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public GenericDemuxMultiTransmitterTask(AbstractSourceTransmitterTask<DATA_OUT_TYPE> sourceTransmitter,
                                            int numInternalTransmitters,
                                            int maxDataChunkSize,
                                            Integer inDataQueueCapacity,
                                            Long timeoutInterval,
                                            ExecutorService executorService) {
        super(createInternalTransmitterTasks(
                numInternalTransmitters,
                maxDataChunkSize,
                inDataQueueCapacity),
                executorService);

        this.m_executorService = executorService;
        if(sourceTransmitter == null){
            throw new NullPointerException();
        }
        this.m_sourceTransmitter = sourceTransmitter;
        if(sourceTransmitter.isRunning() || sourceTransmitter.hasOutConnection()){
            throw new IllegalArgumentException();
        }
        if(numInternalTransmitters != sourceTransmitter.getNumDemuxTransmitters()){
            throw new IllegalArgumentException();
        }

        assert(numInternalTransmitters == this.getNumInternalTasks());
        List<DemuxTransmitterTask<DATA_OUT_TYPE>> demuxTransmitterList = new ArrayList<>(numInternalTransmitters);
        for(int i = 0; i < numInternalTransmitters; i++){
            TransmitterTask<? extends DATA_OUT_TYPE> transmitterTask = this.getInternalTaskByIndex(i);
            assert(transmitterTask instanceof DemuxTransmitterTask);
            demuxTransmitterList.add((DemuxTransmitterTask<DATA_OUT_TYPE>) transmitterTask);
        }
        this.m_demuxTransmitterList = (UnmodifiableList<DemuxTransmitterTask<DATA_OUT_TYPE>>) UnmodifiableList.unmodifiableList(demuxTransmitterList);

        this.m_demuxReceiver = new DemuxReceiverTask(inDataQueueCapacity, timeoutInterval, numInternalTransmitters);
        try {
            this.m_sourceTransmitter.setOutConnection(this.m_demuxReceiver);
        } catch (UnacceptedConcurrentTaskException e) {
            throw new RuntimeException("This should not have happened.", e);
        }
        assert(numInternalTransmitters == this.m_demuxTransmitterList.size());
    }

    @Override
    protected void preWork() {
        assert(this.isRunning());
        if(!this.hasAllOutConnections()){
            throw new RuntimeException(new MissingConnectionException());
        }
        assert(this.m_sourceTransmitter.isNotStarted());
        assert(this.m_sourceTransmitter.hasOutConnection());
        assert(this.m_sourceTransmitter.getTargetReceiverTask() == this.m_demuxReceiver);

        this.m_executorService.submit(this.m_sourceTransmitter);
        while(this.m_sourceTransmitter.isNotStarted()){
            Thread.yield();
        }

        assert(this.m_demuxReceiver.isNotStarted());
        assert(this.m_demuxReceiver.hasInConnection());
        assert(this.m_demuxReceiver.getSourceTransmitterTask() == this.m_sourceTransmitter);
        this.m_executorService.submit(this.m_demuxReceiver);
        while(this.m_demuxReceiver.isNotStarted()){
            Thread.yield();
        }
        super.preWork(); // Starts demux transmitters
    }

    @Override
    protected void postWork() {
        assert(this.m_sourceTransmitter.isRunning());
        assert(this.terminateCalledWithInterrupt() != null);
        try {
            this.m_sourceTransmitter.terminate(this.terminateCalledWithInterrupt());
        } catch (NotStartedException e) {
            throw new IllegalStateException("This should not happen here.", e);
        }
        while(!this.m_sourceTransmitter.isTerminated()){
            Thread.yield();
        }

        assert(this.m_demuxReceiver.isRunning());
        try {
            this.m_demuxReceiver.terminate(this.terminateCalledWithInterrupt());
        } catch (NotStartedException e) {
            throw new IllegalStateException("This should not happen here.", e);
        }
        while(!this.m_demuxReceiver.isTerminated()){
            Thread.yield();
        }
        super.postWork(); // Terminates demux transmitters
    }

}
