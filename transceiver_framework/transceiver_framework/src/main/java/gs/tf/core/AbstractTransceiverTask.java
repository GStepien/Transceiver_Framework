/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import java.util.Collection;
import java.util.concurrent.ExecutorService;

public abstract class AbstractTransceiverTask<DATA_IN_TYPE, DATA_OUT_TYPE>
	extends 
		DelegatorTransceiverTask<
			DATA_IN_TYPE, 
			AbstractTransceiverTask.InternalReceiverTask<DATA_IN_TYPE>,
			DATA_OUT_TYPE,
			AbstractTransceiverTask.InternalTransmitterTask<DATA_OUT_TYPE>> {

    static class InternalReceiverTask<DATA_IN_TYPE> extends AbstractReceiverTask<DATA_IN_TYPE>{

    	private volatile AbstractTransceiverTask<DATA_IN_TYPE, ?> m_enclosingInstance;
    	
        public InternalReceiverTask(
        		Integer inDataQueueCapacity, 
        		Long timeoutInterval) {
            super(inDataQueueCapacity, timeoutInterval);            
        }

        public void setEnclosingTransceiverTask(AbstractTransceiverTask<DATA_IN_TYPE, ?> enclosingInstance) {
        	assert(enclosingInstance != null);
        	assert(enclosingInstance.getInternalReceiverTask() == this);
            this.m_enclosingInstance = enclosingInstance;
        }
        
        public AbstractTransceiverTask<DATA_IN_TYPE, ?> getEnclosingTransceiverTask(){
        	return this.m_enclosingInstance;
        }
        
        @Override
        protected final void processDataElement(DATA_IN_TYPE dataElement) throws InterruptedException{
        	assert(this.m_enclosingInstance != null);
            this.m_enclosingInstance.processDataElement(dataElement);
        }
    }

    static class InternalTransmitterTask<DATA_OUT_TYPE> extends AbstractTransmitterTask<DATA_OUT_TYPE>{

    	private volatile AbstractTransceiverTask<?, DATA_OUT_TYPE> m_enclosingInstance;
    	    	
        public InternalTransmitterTask(        		
        		int maxDataChunkSize) {
            super(maxDataChunkSize);
        }
        
        public void setEnclosingTransceiverTask(AbstractTransceiverTask<?, DATA_OUT_TYPE> enclosingInstance) {
        	assert(enclosingInstance != null);
        	assert(enclosingInstance.getInternalTransmitterTask() == this);
            this.m_enclosingInstance = enclosingInstance;
        }
        
        public AbstractTransceiverTask<?, DATA_OUT_TYPE> getEnclosingTransceiverTask(){
        	return this.m_enclosingInstance;
        }
        
        @Override
        protected Collection<DATA_OUT_TYPE> getNextDataChunk() {
        	assert(this.m_enclosingInstance != null);            
            return this.m_enclosingInstance.getNextDataChunk();
        }        
    }

    public AbstractTransceiverTask(
    		Integer inDataQueueCapacity,
    		Long timeoutInterval,
    		int maxDataChunkSize,
    		ExecutorService executorService) {
    	super(new InternalReceiverTask<>(inDataQueueCapacity, timeoutInterval),
    			new InternalTransmitterTask<>(maxDataChunkSize),
    			true,
    			executorService);

    	this.getInternalReceiverTask().setEnclosingTransceiverTask(this);
    	this.getInternalTransmitterTask().setEnclosingTransceiverTask(this);    	
    }


    // Note: This method is called by internal receiver thread -
    // If this method and getNextDataChunk() access a common field, said access must be synchronized!
    protected abstract void processDataElement(DATA_IN_TYPE dataElement) throws InterruptedException;

    // Note: This method is called by internal transmitter thread 
    // Non blocking: Return either null or a collection of size zero to indicate no data available
    // Result size may not succeed getMaxDataChunkSize
    // If this method and processDataElement(...) access a common field, said access must be synchronized!
    protected abstract Collection<DATA_OUT_TYPE> getNextDataChunk();
    
    // Blocking
    // TODO make forward methods private, remove while(this.m_receiverTask.isNotStarted()) (this is already done in preWork()
    protected final synchronized void forwardToReceiver(Collection<? extends DATA_OUT_TYPE> dataSet) throws InterruptedException, IllegalStatusException {
        this.getInternalTransmitterTask().forwardToReceiver(dataSet);
    }

    // Blocking
    protected final synchronized void forwardToReceiver(DATA_OUT_TYPE dataElement) throws InterruptedException, IllegalStatusException {
       this.getInternalTransmitterTask().forwardToReceiver(dataElement);
    }

    protected int getMaxDataChunkSize(){
        return this.getInternalTransmitterTask().getMaxDataChunkSize();
    }

}
