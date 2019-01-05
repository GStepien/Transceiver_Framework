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

public class GenericMultiTransmitterTask<
	DATA_OUT_TYPE,
	TRANSMITTER_TYPE extends TransmitterTask<? extends DATA_OUT_TYPE>>
	extends 
		AbstractMultiTask<TRANSMITTER_TYPE> 
	implements 
		MultiTransmitterTask<DATA_OUT_TYPE, TRANSMITTER_TYPE> {

    private final ReentrantLock CONNECTION_LOCK;
    
    // Access only via synchronization over CONNECTION_LOCK
    private final Map<Long, Long> m_transmitterIDToReceiverIDMap;
    private final Map<Long, Long> m_transmitterIDToReceiverIDMap2;
    
    // Access only via synchronization over CONNECTION_LOCK
    private final List<Long> m_disconnectedTransmitterIDList;

    // Provided transmitters are connected to receivers in the order they appear in the provided collection
    // (as returned by the former's iterator)
    public GenericMultiTransmitterTask(Collection<TRANSMITTER_TYPE> transmitterTasks,
                                       ExecutorService executorService) {
        super(transmitterTasks, executorService);

        this.m_transmitterIDToReceiverIDMap = new HashMap<>();
        this.m_transmitterIDToReceiverIDMap2 = new HashMap<>();
        
        this.m_disconnectedTransmitterIDList = new ArrayList<>(transmitterTasks.size());
        
        for (TransmitterTask<? extends DATA_OUT_TYPE> transTask : transmitterTasks) {
            if(transTask.getTargetReceiverTask() != null){
                throw new IllegalArgumentException("Provided transmitter task already connected.");
            }
            this.m_disconnectedTransmitterIDList.add(transTask.getID());
        }
        
        this.CONNECTION_LOCK = new ReentrantLock(true);

        assert(this.m_disconnectedTransmitterIDList.containsAll(this.m_taskIDSet));
        assert(this.m_taskIDSet.containsAll(this.m_disconnectedTransmitterIDList));
    }


    // Result immediately outdated
    @Override
    public final int getNumOutConnections() {
    	synchronized(this.CONNECTION_LOCK) {
    		int result = this.m_transmitterIDToReceiverIDMap.size();
    		assert(result <= this.getNumInternalTasks());
    		return result;
    	}
    }

    // Result immediately outdated
    @Override
    public final boolean hasAllOutConnections() {
        return this.getNumOutConnections() == this.getNumInternalTasks();
    }

    // Result immediately outdated
    @Override
    public final int getNumMissingOutConnections(){
    	synchronized(this.CONNECTION_LOCK) {
    		assert(this.m_disconnectedTransmitterIDList.size() == (this.getNumInternalTasks() - this.getNumOutConnections()));
    		return(this.m_disconnectedTransmitterIDList.size());
    	}
    }

    @Override
    public final Lock getOutConnectionLock(){
        return this.CONNECTION_LOCK;
    }

    @Override
    public final ArrayList<TransmitterTask<? extends DATA_OUT_TYPE>> addOutConnection(MultiReceiverTask<? super DATA_OUT_TYPE, ?> multiReceiverTask) throws UnacceptedConcurrentTaskException {
        if(!this.isNotStarted()){
            throw new RuntimeException(new IllegalStatusException("Connections should only be set when tasks have not started yet."));
        }

        if(multiReceiverTask == null){
            throw new NullPointerException();
        }

        boolean ourLock;
        ArrayList<TransmitterTask<? extends DATA_OUT_TYPE>> result;
        if ((ourLock = this.CONNECTION_LOCK.tryLock()) && multiReceiverTask.getInConnectionLock().tryLock()) {
            try {
                int numReceiverInMissing = multiReceiverTask.getNumMissingInConnections();
                int numTransmitterOutMissing = this.getNumMissingOutConnections();

                int numConnectionsPossible = Math.min(numReceiverInMissing, numTransmitterOutMissing);

                if (numConnectionsPossible == 0) {
                    throw new UnacceptedConcurrentTaskException("Either this or the provided MultiTask instance has no free connections left.");
                }

                result = new ArrayList<>(numConnectionsPossible);

                TransmitterTask<? extends DATA_OUT_TYPE> transmitterTask;
                ReceiverTask<? super DATA_OUT_TYPE> receiverTask;
                while (numConnectionsPossible > 0) {
                    receiverTask = multiReceiverTask.getNextDisconnectedReceiverTask();
                    assert (receiverTask != null);
                    assert (receiverTask.getSourceTransmitterTask() == null);

                    transmitterTask = this.addOutConnection(receiverTask);
                    result.add(transmitterTask);

                    // So other multitask may set its fields accordingly
                    multiReceiverTask.addInConnection(transmitterTask);
                    numConnectionsPossible--;
                }
            }
            finally {
                multiReceiverTask.getInConnectionLock().unlock();
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
    public final TransmitterTask<? extends DATA_OUT_TYPE> addOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask) throws UnacceptedConcurrentTaskException {
        if(receiverTask == null){
            throw new NullPointerException();
        }

        TransmitterTask<? extends DATA_OUT_TYPE> transmitterTask;
        boolean ourLock;
        if((ourLock = this.CONNECTION_LOCK.tryLock()) && receiverTask.getInConnectionLock().tryLock()) {
            try {
                transmitterTask = this.getNextDisconnectedTransmitterTask();
                if (transmitterTask == null) {
                    assert (this.hasAllOutConnections());
                    throw new UnacceptedConcurrentTaskException();
                }
                assert (!this.hasAllOutConnections());
                assert (this.m_disconnectedTransmitterIDList.get(0) == transmitterTask.getID());
                if(this.m_transmitterIDToReceiverIDMap.keySet().contains(transmitterTask.getID()) ||
                		this.m_transmitterIDToReceiverIDMap.values().contains(receiverTask.getID())) {
                	throw new IllegalStateException("Non unique IDs encountered.");
                }
                this.m_transmitterIDToReceiverIDMap.put(transmitterTask.getID(), receiverTask.getID());
                
                if(receiverTask instanceof TransceiverTask){
                    receiverTask = ((TransceiverTask<? super DATA_OUT_TYPE, ?>)receiverTask).asReceiverTask();
                }
                if(transmitterTask instanceof TransceiverTask){
                    transmitterTask = ((TransceiverTask<?, DATA_OUT_TYPE>)transmitterTask).asTransmitterTask();
                }
                if(this.m_transmitterIDToReceiverIDMap2.keySet().contains(transmitterTask.getID()) ||
                		this.m_transmitterIDToReceiverIDMap2.values().contains(receiverTask.getID())) {
                	throw new IllegalStateException("Non unique IDs encountered.");
                }
                this.m_transmitterIDToReceiverIDMap2.put(transmitterTask.getID(), receiverTask.getID());
                
                TransmitterTask<?> receiverTransmitter = receiverTask.getSourceTransmitterTask();
                ReceiverTask<?> transmitterReceiver = transmitterTask.getTargetReceiverTask();

                // Either both are null or both refer to each other
                if (receiverTransmitter == null && transmitterReceiver == null) {
                    transmitterTask.setOutConnection(receiverTask);
                } else if (receiverTransmitter != transmitterTask || transmitterReceiver != receiverTask) {
                    // The opposite case should only be possible if this method is called by some MultiReceiverTask's
                    // addInConnection(MultiTransmitterTask...) method - then only the updates of
                    // m_disconnectedTransmitterIDList and m_transmitterIDToReceiverIDMap required.
                    throw new IllegalArgumentException("Unexpected connection found.");
                }

                this.m_disconnectedTransmitterIDList.remove(0);
            }
            finally {
                receiverTask.getInConnectionLock().unlock();
                this.CONNECTION_LOCK.unlock();
            }
        }
        else{
            if(ourLock){ // If our lock was acquired, then the other's lock acquisition must have failed
                this.CONNECTION_LOCK.unlock();
            } // If our lock was NOT acquired, then its guaranteed that no attempt at acquiring the other's lock was performed

            throw new IllegalStateException("Concurrent connection attempt!");
        }
        return transmitterTask;
    }

    @Override
    public final TransmitterTask<? extends DATA_OUT_TYPE> getNextDisconnectedTransmitterTask(){
        if(this.getNumMissingOutConnections() == 0){
            return null;
        }
        else{
        	synchronized(this.CONNECTION_LOCK) {
        		assert(this.m_disconnectedTransmitterIDList.size() > 0);
        		long transmitterID = this.m_disconnectedTransmitterIDList.get(0);
        		return this.getInternalTaskByID(transmitterID);
        	}
        }
    }

    @Override
    public final UnmodifiableList<Long> getDisconnectedInternalTransmitterIDList() {
    	synchronized(this.CONNECTION_LOCK) {
    		return (UnmodifiableList<Long>) UnmodifiableList.unmodifiableList(this.m_disconnectedTransmitterIDList);
    	}
    }

	@Override
	public boolean hasAllConnections() {		
		return this.hasAllOutConnections();
	}
}