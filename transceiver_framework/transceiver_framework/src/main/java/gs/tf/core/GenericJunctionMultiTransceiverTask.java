/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.bidimap.UnmodifiableBidiMap;
import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

// Note that the junction blocks if one of the target MultiReceivers do not fetch any data
// TODO: Implement own inQueue for JunctionTransmitterTask and let JunctionTransceiver task forward data to it
public class GenericJunctionMultiTransceiverTask<DATA_IN_OUT_TYPE> 
	extends GenericMultiTransceiverTask<
		DATA_IN_OUT_TYPE, 
		DATA_IN_OUT_TYPE,
        TransceiverTask<DATA_IN_OUT_TYPE, DATA_IN_OUT_TYPE>> {

    private static class JunctionTransceiverTask<DATA_IN_OUT_TYPE> extends AbstractTransceiverTask<DATA_IN_OUT_TYPE, DATA_IN_OUT_TYPE> {
        private static final Logger LOGGER = Logger.getLogger(JunctionTransceiverTask.class.getName());

        private final BlockingQueue<DATA_IN_OUT_TYPE> m_inQueue1, m_inQueue2;
        private final Long m_timeoutInterval;

        // No volatile necessary, access only via Thread executing this task
        private boolean m_lastWasTimeout1;
        private boolean m_lastWasTimeout2;
        
        private volatile GenericJunctionMultiTransceiverTask<DATA_IN_OUT_TYPE> m_enclosingJunctionMultiTransceiver;
        
        public JunctionTransceiverTask(Integer inDataQueueCapacity, Long timeoutInterval, int maxDataChunkSize, ExecutorService executorService) {
            super(inDataQueueCapacity, timeoutInterval, maxDataChunkSize, executorService);

            this.m_timeoutInterval = timeoutInterval;
            this.m_lastWasTimeout1 = false;
            this.m_lastWasTimeout2 = false;
            this.m_enclosingJunctionMultiTransceiver = null;
            
            if(inDataQueueCapacity == null){
                this.m_inQueue1 = new LinkedBlockingQueue<>();
                this.m_inQueue2 = new LinkedBlockingQueue<>();
            }
            else{
                if(inDataQueueCapacity < 1){
                    throw new IllegalArgumentException();
                }

                this.m_inQueue1 = new ArrayBlockingQueue<>(inDataQueueCapacity, true);
                this.m_inQueue2 = new ArrayBlockingQueue<>(inDataQueueCapacity, true);
            }
        }
        
        public void setEnclosingJunctionMultiTransceiver(
        		GenericJunctionMultiTransceiverTask<DATA_IN_OUT_TYPE> enclosingJunctionMultiTransceiver) {
        	assert(enclosingJunctionMultiTransceiver != null);
        	assert(enclosingJunctionMultiTransceiver.m_transmitterToTransceiverMap.values().contains(this));
        	this.m_enclosingJunctionMultiTransceiver = enclosingJunctionMultiTransceiver;
        }
        
        @Override
        protected void processDataElement(DATA_IN_OUT_TYPE dataElement) throws InterruptedException {
            if(this.m_timeoutInterval == null) {
                this.m_inQueue1.put(dataElement);
                this.m_inQueue2.put(dataElement);
            }
            else{
                boolean timeout = this.m_lastWasTimeout1;
                if(this.m_inQueue1.offer(dataElement)){
                	timeout = false;
                }
                else if(!this.m_lastWasTimeout1){
                    timeout = !this.m_inQueue1.offer(
                    		dataElement, 
                    		this.m_timeoutInterval, 
                    		TimeUnit.MILLISECONDS);
                }
                if(timeout){
                    LOGGER.warning("Discarding input element to first input queue due to timeout in "+
                            this.getClass().getName()+", running in thread with ID "+this.getRunThreadID()+" and name "+
                            this.getRunThreadName()+ ".");
                }
                this.m_lastWasTimeout1 = timeout;

                timeout = this.m_lastWasTimeout2;
                if(this.m_inQueue2.offer(dataElement)){
                	timeout = false;
                }
                else if(!this.m_lastWasTimeout2){
                    timeout = !this.m_inQueue2.offer(
                    		dataElement, 
                    		this.m_timeoutInterval, 
                    		TimeUnit.MILLISECONDS);
                }
                if(timeout){
                    LOGGER.warning("Discarding input element to second input queue due to timeout in "+
                            this.getClass().getName()+", running in thread with ID "+this.getRunThreadID()+" and name "+
                            this.getRunThreadName()+ ".");
                }
                this.m_lastWasTimeout2 = timeout;
            }
        }

        @Override
        protected Collection<DATA_IN_OUT_TYPE> getNextDataChunk() {
            List<DATA_IN_OUT_TYPE> dataChunk = new LinkedList<>();
            this.m_inQueue1.drainTo(dataChunk, this.getMaxDataChunkSize());

            return dataChunk;
        }

        private Collection<DATA_IN_OUT_TYPE> getNextDataChunk2() {
            List<DATA_IN_OUT_TYPE> dataChunk = new LinkedList<>();
            this.m_inQueue2.drainTo(dataChunk, this.getMaxDataChunkSize());

            return dataChunk;
        }
        
        @Override
        protected void preWork() {
        	assert(this.m_enclosingJunctionMultiTransceiver != null);
        	super.preWork();
        }
        
        @Override
        protected void midPostWork(){
            super.midPostWork();
            assert(this.terminateCalledWithInterrupt() != null);
            if(this.terminateCalledWithInterrupt()) {
                ArrayList<DATA_IN_OUT_TYPE> remainingData = new ArrayList<>(this.m_inQueue1.size());
                this.m_inQueue1.drainTo(remainingData);
                int size1 = remainingData.size();

                remainingData.clear();
                this.m_inQueue2.drainTo(remainingData);
                int size2 = remainingData.size();

                StringBuilder sb = new StringBuilder();
                if(size1 > 0 || size2 > 0) {
                    sb.append("Discarding ");
                    if (size1 > 0) {
                        sb.append(size1).append(" elements in first queue ");
                        if (size2 > 0) {
                            sb.append("and ").append(size2).append(" elements in second queue ");
                        }

                    }
                    else {
                        assert(size2 > 0);
                        sb.append(size2).append(" elements in second queue ");
                    }
                    sb.append("due to interrupt-termination.");
                    LOGGER.warning(sb.toString());
                }
            }
            else{
                while(this.m_inQueue1.size() > 0){
                    try {
                        this.forwardToReceiver(this.getNextDataChunk());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("No interrupt should happen at this point.", e);
                    } catch (IllegalStatusException e) {
                        throw new RuntimeException("Should not happen here.", e);
                    }
                }
                GenericJunctionMultiTransceiverTask<DATA_IN_OUT_TYPE>.JunctionTransmitterTask junctionTransmitter = this.m_enclosingJunctionMultiTransceiver.m_transmitterToTransceiverMap.getKey(this);
                while(this.m_inQueue2.size() > 0){
                	if(!junctionTransmitter.isTerminated()) {
                		Thread.yield(); // JunctionTransmitterTask is responsible for draining this unless it is already terminated
                	}
                	else {
                		ArrayList<DATA_IN_OUT_TYPE> remainingData2 = new ArrayList<>(this.m_inQueue2.size());
                        this.m_inQueue2.drainTo(remainingData2);
                        int size2 = remainingData2.size();

                        StringBuilder sb = new StringBuilder();
                        if(size2 > 0) {
                            sb.append("Discarding ")
                            	.append(size2)
                            	.append(" elements from second queue due to the MultiTransmitterTask ")
                            	.append("managing this instance's JunctionTransmitterTasks having already terminated.");                               
                            
                            LOGGER.warning(sb.toString());
                        }
                	}
            	}
            }
            assert(this.m_inQueue1.isEmpty() && this.m_inQueue2.isEmpty());
        }

        @Override
        protected void postWork(){
            super.postWork();
        }
    }

    private class JunctionTransmitterTask extends AbstractTransmitterTask<DATA_IN_OUT_TYPE> {

        public JunctionTransmitterTask(int maxDataChunkSize) {
            super(maxDataChunkSize);
        }

        @Override
        protected Collection<DATA_IN_OUT_TYPE> getNextDataChunk() {
            assert(GenericJunctionMultiTransceiverTask.this.m_transmitterToTransceiverMap.containsKey(this));

            JunctionTransceiverTask<DATA_IN_OUT_TYPE> assignedTransceiver =
                    GenericJunctionMultiTransceiverTask.this.m_transmitterToTransceiverMap.get(this);

            return assignedTransceiver.getNextDataChunk2();
        }

        @Override
        protected void postWork(){
            assert(this.terminateCalledWithInterrupt() != null);
            JunctionTransceiverTask<DATA_IN_OUT_TYPE> assignedTransceiver =
                    GenericJunctionMultiTransceiverTask.this.m_transmitterToTransceiverMap.get(this);
            BlockingQueue<DATA_IN_OUT_TYPE> queue = assignedTransceiver.m_inQueue2;
            if(!assignedTransceiver.isTerminated()) {
            	throw new IllegalStateException(new IllegalStatusException("Assignd JunctionTransceiverTask must "
            			+ "be already terminated when its corresponding JunctionTransmitterTask gets terminated."));
            }
            
            assert(queue.isEmpty());
            /* This part is not necessary, as JunctionTransceiver should either discard its m_inQueue2
             * entries upon termination with interrupt or wait until this instance has fetched all of them
            if(this.terminateCalledWithInterrupt()) {

                ArrayList<DATA_IN_OUT_TYPE> remainingData = new ArrayList<>(queue.size());
                queue.drainTo(remainingData);
                if (queue.size() > 0) {
                    GenericJunctionMultiTransceiverTask.LOGGER.warning("Discarding " + remainingData.size() +
                            " motif candidates due to interrupt-termination.");
                }
            }
            else{
                while(queue.size() > 0){
                    try {
                        this.forwardToReceiver(assignedTransceiver.getNextDataChunk2());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("No interrupt should happen at this point.", e);
                    } catch (IllegalStatusException e) {
                        throw new RuntimeException("Should not happen here.", e);
                    }
                }
            }*/
            super.postWork();
            //assert(queue.isEmpty());
        }
    }

    private final UnmodifiableBidiMap<JunctionTransmitterTask, JunctionTransceiverTask<DATA_IN_OUT_TYPE>> m_transmitterToTransceiverMap;
    private final UnmodifiableList<JunctionTransmitterTask> m_transmitterList;
    
    private final Object TRANSMITTER_FETCH_LOCK;
    // No volatile required - access only via synchronization via TRANSMITTER_FETCH_LOCK
    private MultiTransmitterTask<DATA_IN_OUT_TYPE, ?> m_multiTransmitter;
    
    private final ExecutorService m_executorService;

    private static final Logger LOGGER = Logger.getLogger(GenericJunctionMultiTransceiverTask.class.getName());

    private static<DATA_IN_OUT_TYPE> List<TransceiverTask<DATA_IN_OUT_TYPE, DATA_IN_OUT_TYPE>> createInternalTransceiverTasks(
            int numInternalTransceivers,
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int maxDataChunkSize,
            ExecutorService executorService){
        if(numInternalTransceivers <= 0){
            throw new IllegalArgumentException();
        }

        List<TransceiverTask<DATA_IN_OUT_TYPE, DATA_IN_OUT_TYPE>> result = new ArrayList<>(numInternalTransceivers);
        for(int i = 0; i < numInternalTransceivers; i++){
            result.add(new JunctionTransceiverTask<>(inDataQueueCapacity, timeoutInterval, maxDataChunkSize, executorService));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public GenericJunctionMultiTransceiverTask(
            int numInternalTransceivers,
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int maxDataChunkSize,
            ExecutorService executorService) {
        super(createInternalTransceiverTasks(
                numInternalTransceivers,
                inDataQueueCapacity,
                timeoutInterval,
                maxDataChunkSize,
                executorService),
                executorService);
        this.TRANSMITTER_FETCH_LOCK = new Object();
        this.m_multiTransmitter = null;
        this.m_executorService = executorService;
        BidiMap<JunctionTransmitterTask, JunctionTransceiverTask<DATA_IN_OUT_TYPE>> transmitterToTransceiverMap = new DualHashBidiMap<>();
        JunctionTransmitterTask transmitter;
        
        assert(numInternalTransceivers == this.getNumInternalTasks());
        List<JunctionTransmitterTask> transmitterList = new ArrayList<>(numInternalTransceivers);

        for(int i = 0; i < numInternalTransceivers; i++){
            TransmitterTask<? extends DATA_IN_OUT_TYPE> transmitterTask = this.getInternalTaskByIndex(i);
            assert(transmitterTask instanceof JunctionTransceiverTask);
            assert(transmitterTask == this.getInternalTaskByIndex(i));
            transmitter = new JunctionTransmitterTask(maxDataChunkSize);
            transmitterToTransceiverMap.put(transmitter,
                    (JunctionTransceiverTask<DATA_IN_OUT_TYPE>)transmitterTask);
            transmitterList.add(transmitter);
        }
        this.m_transmitterToTransceiverMap =
                (UnmodifiableBidiMap<JunctionTransmitterTask, JunctionTransceiverTask<DATA_IN_OUT_TYPE>>) UnmodifiableBidiMap.unmodifiableBidiMap(transmitterToTransceiverMap);
        this.m_transmitterList =
                (UnmodifiableList<JunctionTransmitterTask>) UnmodifiableList.unmodifiableList(transmitterList);
        for(int i = 0; i < numInternalTransceivers; i++){
            TransmitterTask<? extends DATA_IN_OUT_TYPE> transmitterTask = this.getInternalTaskByIndex(i);
            ((JunctionTransceiverTask<DATA_IN_OUT_TYPE>)transmitterTask).setEnclosingJunctionMultiTransceiver(this);
        }
    }

    public MultiTransmitterTask<DATA_IN_OUT_TYPE, ?> createMultiTransmitterFromJunction() throws JunctionTransmitterAlreadyFetchedException {
    	synchronized (this.TRANSMITTER_FETCH_LOCK) {
			// this.m_multiTransmitter set by createMultiTransmitterFromJunction
    		MultiTransmitterTask<DATA_IN_OUT_TYPE, ?> result = createMultiTransmitterFromJunction(this);
    		assert(this.m_multiTransmitter != null);
    		assert(this.m_multiTransmitter == result);
		}
    	
    	return this.m_multiTransmitter;
    }
    
    public static <DATA_OUT_TYPE> MultiTransmitterTask<DATA_OUT_TYPE, ?> createMultiTransmitterFromJunction(
            GenericJunctionMultiTransceiverTask<DATA_OUT_TYPE> junctionMultiTransceiverTask) throws JunctionTransmitterAlreadyFetchedException {
        if(junctionMultiTransceiverTask == null){
            throw new NullPointerException();
        }

        synchronized (junctionMultiTransceiverTask.TRANSMITTER_FETCH_LOCK) {
            if (junctionMultiTransceiverTask.m_multiTransmitter != null) {
                throw new JunctionTransmitterAlreadyFetchedException();
            }

            MultiTransmitterTask<DATA_OUT_TYPE, ?> result = new GenericMultiTransmitterTask<>(
                    junctionMultiTransceiverTask.m_transmitterList,
                    junctionMultiTransceiverTask.m_executorService);

            junctionMultiTransceiverTask.m_multiTransmitter = result;
            return result;
        }
    }

    @Override
    protected void preWork() {
    	// Note: Waiting for the fetched MultiTransmitterTask to start will result
    	// in a deadlock in the driver, as each ClosedMultiTaskChain is started subsequently there
    	
        super.preWork();
        synchronized (this.TRANSMITTER_FETCH_LOCK) {
        	if(this.m_multiTransmitter == null){
        		throw new IllegalStateException("Multi transmitter of multi transceiver junction not fetched.");
        	}
        }
    }
}