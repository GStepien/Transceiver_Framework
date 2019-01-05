/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GenericMultiReceiverTask<
	DATA_IN_TYPE, 
	RECEIVER_TYPE extends ReceiverTask<? super DATA_IN_TYPE>>
	extends 
		AbstractMultiTask<RECEIVER_TYPE> 
	implements 
		MultiReceiverTask<DATA_IN_TYPE, RECEIVER_TYPE>{

    private final ReentrantLock CONNECTION_LOCK;

    // Only used for consistency verification, access synchronized via CONNECTION_LOCK
    private final Map<Long, Long> m_receiverIDToTransmitterIDMap;
    private final Map<Long, Long> m_receiverIDToTransmitterIDMap2;
    
    // Access synchronized via CONNECTION_LOCK
    private final List<Long> m_disconnectedReceiverIDList;

    // Provided transmitters are connected to receivers in the order they appear in the provided collection
    // (as returned by the former's iterator)
    public GenericMultiReceiverTask(Collection<RECEIVER_TYPE> receiverTasks,
                                       ExecutorService executorService) {
        super(receiverTasks, executorService);

        this.m_receiverIDToTransmitterIDMap = new HashMap<>();
        this.m_receiverIDToTransmitterIDMap2 = new HashMap<>();
        
        this.m_disconnectedReceiverIDList = new ArrayList<>(receiverTasks.size());
        
        for (ReceiverTask<? super DATA_IN_TYPE> recTask : receiverTasks) {
            if(recTask.getSourceTransmitterTask() != null){
                throw new IllegalArgumentException("Provided receiver task already connected.");
            }
            this.m_disconnectedReceiverIDList.add(recTask.getID());
        }
        this.CONNECTION_LOCK = new ReentrantLock(true);

        assert(this.m_disconnectedReceiverIDList.containsAll(this.m_taskIDSet));
        assert(this.m_taskIDSet.containsAll(this.m_disconnectedReceiverIDList));
    }

    // Result immediately outdated
    @Override
    public final int getNumInConnections() {
    	synchronized(this.CONNECTION_LOCK) {
    		int result = this.m_receiverIDToTransmitterIDMap.size();

    		assert(result <= this.getNumInternalTasks());
    		return result;
    	}
    }

    // Result immediately outdated
    @Override
    public final boolean hasAllInConnections() {
        return this.getNumInConnections() == this.getNumInternalTasks();
    }

    // Result immediately outdated
    @Override
    public final int getNumMissingInConnections() {
    	synchronized(this.CONNECTION_LOCK) {
    		assert(this.m_disconnectedReceiverIDList.size() == (this.getNumInternalTasks() - this.getNumInConnections()));
        	return(this.m_disconnectedReceiverIDList.size());
    	}
    }

    @Override
    public final Lock getInConnectionLock(){
        return this.CONNECTION_LOCK;
    }
    
    @Override
    public final ArrayList<ReceiverTask<? super DATA_IN_TYPE>> addInConnection(MultiTransmitterTask<? extends DATA_IN_TYPE, ?> multiTransmitterTask) throws UnacceptedConcurrentTaskException {
        if(!this.isNotStarted()){
            throw new RuntimeException(new IllegalStatusException("Connections should only be set when tasks have not started yet."));
        }

        if(multiTransmitterTask == null){
            throw new NullPointerException();
        }

        boolean ourLock;
        ArrayList<ReceiverTask<? super DATA_IN_TYPE>> result;
        if ((ourLock = this.CONNECTION_LOCK.tryLock()) && multiTransmitterTask.getOutConnectionLock().tryLock()) {
            try {
                int numTransmitterOutMissing = multiTransmitterTask.getNumMissingOutConnections();
                int numReceiverInMissing = this.getNumMissingInConnections();

                int numConnectionsPossible = Math.min(numTransmitterOutMissing, numReceiverInMissing);

                if (numConnectionsPossible == 0) {
                    throw new UnacceptedConcurrentTaskException("Either this or the provided MultiTask instance has no free connections left.");
                }

                result = new ArrayList<>(numConnectionsPossible);

                ReceiverTask<? super DATA_IN_TYPE> receiverTask;
                TransmitterTask<? extends DATA_IN_TYPE> transmitterTask;
                while (numConnectionsPossible > 0) {
                    transmitterTask = multiTransmitterTask.getNextDisconnectedTransmitterTask();
                    assert (transmitterTask != null);
                    assert (transmitterTask.getTargetReceiverTask() == null);

                    receiverTask = this.addInConnection(transmitterTask);
                    result.add(receiverTask);

                    // So other multitask may set its disconnected fields accordingly
                    multiTransmitterTask.addOutConnection(receiverTask);
                    numConnectionsPossible--;
                }
            }
            finally {
                multiTransmitterTask.getOutConnectionLock().unlock();
                this.CONNECTION_LOCK.unlock();
            }
        }
        else{
            if(ourLock){ // If our lock was acquired, then the other's lock acquisition must have failed
                this.CONNECTION_LOCK.unlock();
            } // If our lock was NOT acquired, then its guaranteed that no attempt at acquiring the other's lock was performed

            throw new IllegalStateException("Concurrent connection attempt!");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final ReceiverTask<? super DATA_IN_TYPE> addInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask) throws UnacceptedConcurrentTaskException {
        if(transmitterTask == null){
            throw new NullPointerException();
        }

        if(this.hasAllInConnections()){
            throw new UnacceptedConcurrentTaskException();
        }

        ReceiverTask<? super DATA_IN_TYPE> receiverTask;
        boolean ourLock;

        if((ourLock = this.CONNECTION_LOCK.tryLock()) && transmitterTask.getOutConnectionLock().tryLock()) {
            try {
                receiverTask = this.getNextDisconnectedReceiverTask();
                if (receiverTask == null) {
                    assert (this.hasAllInConnections());
                    throw new UnacceptedConcurrentTaskException();
                }
                assert (!this.hasAllInConnections());
                assert (this.m_disconnectedReceiverIDList.get(0) == receiverTask.getID());
                if(this.m_receiverIDToTransmitterIDMap.keySet().contains(receiverTask.getID()) || 
                		this.m_receiverIDToTransmitterIDMap.values().contains(transmitterTask.getID())) {
                	throw new IllegalStateException("Non unique IDs encountered.");
                }
                this.m_receiverIDToTransmitterIDMap.put(receiverTask.getID(), transmitterTask.getID());
                
                if(transmitterTask instanceof TransceiverTask){
                    transmitterTask = ((TransceiverTask<?, ? extends DATA_IN_TYPE>)transmitterTask).asTransmitterTask();

                }
                if(receiverTask instanceof TransceiverTask){
                    receiverTask = ((TransceiverTask<? super DATA_IN_TYPE, ?>)receiverTask).asReceiverTask();
                }
                if(this.m_receiverIDToTransmitterIDMap2.keySet().contains(receiverTask.getID()) || 
                		this.m_receiverIDToTransmitterIDMap2.values().contains(transmitterTask.getID())) {
                	throw new IllegalStateException("Non unique IDs encountered.");
                }
                this.m_receiverIDToTransmitterIDMap2.put(receiverTask.getID(), transmitterTask.getID());                
                
                ReceiverTask<?> transmitterReceiver = transmitterTask.getTargetReceiverTask();
                TransmitterTask<?> receiverTransmitter = receiverTask.getSourceTransmitterTask();

                // Either both are null or both refer to each other
                if (transmitterReceiver == null && receiverTransmitter == null) {
                    receiverTask.setInConnection(transmitterTask);
                } else if (transmitterReceiver != receiverTask || receiverTransmitter != transmitterTask) {
                    // The opposite case should only be possible if this method is called by some MultiTransmitterTask's
                    // addOutConnection(MultiReceiverTask...) method - then only the upper updates of
                    // m_disconnectedReceiverIDList and m_receiverIDToTransmitterIDMap required.
                    throw new IllegalArgumentException("Unexpected connection found.");
                }

                this.m_disconnectedReceiverIDList.remove(0);
            }
            finally {
                transmitterTask.getOutConnectionLock().unlock();
                this.CONNECTION_LOCK.unlock();
            }
        }
        else{
            if(ourLock){ // If our lock was acquired, then the other's lock acquisition must have failed
                this.CONNECTION_LOCK.unlock();
            } // If our lock was NOT acquired, then its guaranteed that no attempt at acquiring the other's lock was performed

            throw new IllegalStateException("Concurrent connection attempt!");
        }
        return receiverTask;
    }

    @Override
    public final RECEIVER_TYPE getNextDisconnectedReceiverTask() {
        if(this.getNumMissingInConnections() == 0){
            return null;
        }
        else{
        	synchronized(this.CONNECTION_LOCK) {
        		assert(this.m_disconnectedReceiverIDList.size() > 0);
            	long receiverID = this.m_disconnectedReceiverIDList.get(0);
            	return this.getInternalTaskByID(receiverID);
        	}
        }
    }

    @Override
    public final UnmodifiableList<Long> getDisconnectedInternalReceiverIDList() {
    	synchronized(this.CONNECTION_LOCK) {
    		return (UnmodifiableList<Long>) UnmodifiableList.unmodifiableList(this.m_disconnectedReceiverIDList);
    	}
    }

	@Override
	public boolean hasAllConnections() {		
		return this.hasAllInConnections();
	}
}
