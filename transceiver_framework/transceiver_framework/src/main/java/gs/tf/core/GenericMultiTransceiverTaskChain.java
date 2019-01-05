/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.collections4.set.UnmodifiableSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

public class GenericMultiTransceiverTaskChain<
		DATA_IN_TYPE, 
		DATA_OUT_TYPE,
		RECEIVER_TASK_TYPE extends ReceiverTask<DATA_IN_TYPE>,
		MULTI_RECEIVER_TASK_TYPE extends MultiReceiverTask<DATA_IN_TYPE, ? extends RECEIVER_TASK_TYPE>,
		TRANSMITTER_TASK_TYPE extends TransmitterTask<DATA_OUT_TYPE>,
		MULTI_TRANSMITTER_TASK_TYPE extends MultiTransmitterTask<DATA_OUT_TYPE, ? extends TRANSMITTER_TASK_TYPE>>
        extends
        GenericMultiTaskChain<
                        RECEIVER_TASK_TYPE,
                        MULTI_RECEIVER_TASK_TYPE,
                        TRANSMITTER_TASK_TYPE,
                        MULTI_TRANSMITTER_TASK_TYPE>
        implements 
        	MultiTransceiverTaskChain<
	        	DATA_IN_TYPE,
	        	DATA_OUT_TYPE,
	        	RECEIVER_TASK_TYPE,
	        	MULTI_RECEIVER_TASK_TYPE,
	        	TRANSMITTER_TASK_TYPE,
	        	MULTI_TRANSMITTER_TASK_TYPE,
	        	TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>> {

	private final UnmodifiableList<TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>> 
		m_transceiverViews;
    private final UnmodifiableSet<Long> 
    	m_transcieverViewIDSet;
	private final UnmodifiableMap<Long, TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>> 
		m_idToTransceiverViewMap;
    
	public GenericMultiTransceiverTaskChain(Collection<? extends MultiTask<?>> multiTasks,
                                            Map<
                                            	? extends MultiTask<?>, 
                                    			? extends Collection<? extends MultiTask<?>>> multiTaskConnectionDAG,
                                            ExecutorService executorService) throws UnacceptedConcurrentTaskException {
        super(multiTasks, multiTaskConnectionDAG, executorService);
        
        int numRootTasks = this.getRootMultiTask().getNumInternalTasks();
        if(numRootTasks != this.getLeafMultiTask().getNumInternalTasks()) {
        	throw new IllegalArgumentException("Number of internal tasks of leaf multi task and root multitask must match!");
        }
        
        List<TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>> transceiverViews = 
        		new ArrayList<>(numRootTasks);
        Set<Long> transceiverViewIDSet = new HashSet<>();
        Map<Long, TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>> idToTransceiverViewMap = 
        		new HashMap<>();
        TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE> currentTransceiver;
        
        for(int i = 0; i < numRootTasks; i++) {
        	currentTransceiver = new DelegatorTransceiverTask<
        			DATA_IN_TYPE, 
        			RECEIVER_TASK_TYPE, 
        			DATA_OUT_TYPE, 
        			TRANSMITTER_TASK_TYPE>(
	        			this.getRootMultiTask().getInternalTaskByIndex(i),
	        			this.getLeafMultiTask().getInternalTaskByIndex(i));
        	
        	assert(!transceiverViewIDSet.contains(currentTransceiver.getID()));
        	transceiverViewIDSet.add(currentTransceiver.getID());
        	
        	assert(!idToTransceiverViewMap.containsKey(currentTransceiver.getID()));
        	idToTransceiverViewMap.put(currentTransceiver.getID(), currentTransceiver);
        	
        	transceiverViews.add(currentTransceiver);
        }
        this.m_transceiverViews = 
        		(UnmodifiableList<TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>>) 
        			UnmodifiableList.unmodifiableList(transceiverViews);
        this.m_transcieverViewIDSet = 
        		(UnmodifiableSet<Long>) 
        			UnmodifiableSet.unmodifiableSet(transceiverViewIDSet);
        this.m_idToTransceiverViewMap = 
        		(UnmodifiableMap<Long, TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>>) 
        			UnmodifiableMap.unmodifiableMap(idToTransceiverViewMap);
        
        assert(this.m_transcieverViewIDSet.size() == m_transceiverViews.size());
        assert(this.m_transcieverViewIDSet.size() > 0);
        assert(this.m_idToTransceiverViewMap.keySet().containsAll(this.m_transcieverViewIDSet));
        assert(this.m_transcieverViewIDSet.containsAll(this.m_idToTransceiverViewMap.keySet()));
        assert(this.checkTaskMap());
	}
	
	private boolean checkTaskMap(){
        for(Long key : this.m_idToTransceiverViewMap.keySet()){
            if(key != this.m_idToTransceiverViewMap.get(key).getID()){
                return false;
            }
        }
        return true;
    }

	@Override
	public int getNumInConnections() {
		return this.getRootMultiTask().getNumInConnections();
	}

	@Override
	public int getNumMissingInConnections() {
		return this.getRootMultiTask().getNumMissingInConnections();
	}

	@Override
	public boolean hasAllInConnections() {
		return this.getRootMultiTask().hasAllInConnections();
	}

	@Override
	public Lock getInConnectionLock() {
		return this.getRootMultiTask().getInConnectionLock();
	}

	@Override
	public ArrayList<ReceiverTask<? super DATA_IN_TYPE>> addInConnection(
			MultiTransmitterTask<? extends DATA_IN_TYPE, ?> multiTransmitterTask)
			throws UnacceptedConcurrentTaskException {
		return this.getRootMultiTask().addInConnection(multiTransmitterTask);
	}

	@Override
	public ReceiverTask<? super DATA_IN_TYPE> addInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask)
			throws UnacceptedConcurrentTaskException {
		return this.getRootMultiTask().addInConnection(transmitterTask);
	}

	@Override
	public ReceiverTask<? super DATA_IN_TYPE> getNextDisconnectedReceiverTask() {
		return this.getRootMultiTask().getNextDisconnectedReceiverTask();
	}

	@Override
	public UnmodifiableList<Long> getDisconnectedInternalReceiverIDList() {
		return this.getRootMultiTask().getDisconnectedInternalReceiverIDList();
	}

	@Override
	public boolean hasAllConnections() {
		return this.hasAllInOutConnections();
	}

	@Override
	public int getNumInternalTasks() {
		return this.m_transceiverViews.size();
	}
  
	@Override
	public TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE> getInternalTaskByID(long taskID) {
		   if(!this.m_transcieverViewIDSet.contains(taskID)){
	            throw new IndexOutOfBoundsException();
	        }
	        assert(this.m_idToTransceiverViewMap.containsKey(taskID));
	        return this.m_idToTransceiverViewMap.get(taskID);
	}

	@Override
	public TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE> getInternalTaskByIndex(int index) {
		if(index < 0 || index >= this.getNumInternalTasks()){
			throw new IndexOutOfBoundsException();
		}
		assert(this.m_transceiverViews.size() > index);
		return this.m_transceiverViews.get(index);
	}

	@Override
	public UnmodifiableSet<Long> getInternalTaskIDSet() {
		return this.m_transcieverViewIDSet;
	}

	@Override
	public int getNumOutConnections() {
		return this.getLeafMultiTask().getNumOutConnections();
	}

	@Override
	public int getNumMissingOutConnections() {
		return this.getLeafMultiTask().getNumMissingOutConnections();
	}

	@Override
	public boolean hasAllOutConnections() {
		return this.getLeafMultiTask().hasAllOutConnections();
	}

	@Override
	public Lock getOutConnectionLock() {
		return this.getLeafMultiTask().getOutConnectionLock();
	}

	@Override
	public ArrayList<TransmitterTask<? extends DATA_OUT_TYPE>> addOutConnection(
			MultiReceiverTask<? super DATA_OUT_TYPE, ?> multiReceiverTask) throws UnacceptedConcurrentTaskException {
		return this.getLeafMultiTask().addOutConnection(multiReceiverTask);
	}

	@Override
	public TransmitterTask<? extends DATA_OUT_TYPE> addOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask)
			throws UnacceptedConcurrentTaskException {
		return this.getLeafMultiTask().addOutConnection(receiverTask);
	}

	@Override
	public TransmitterTask<? extends DATA_OUT_TYPE> getNextDisconnectedTransmitterTask() {
		return this.getLeafMultiTask().getNextDisconnectedTransmitterTask();
	}

	@Override
	public UnmodifiableList<Long> getDisconnectedInternalTransmitterIDList() {
		return this.getLeafMultiTask().getDisconnectedInternalTransmitterIDList();
	}

	@Override
	public boolean hasAllInOutConnections() {
		return this.getLeafMultiTask().hasAllOutConnections() &&
				this.getRootMultiTask().hasAllInConnections();
	}
}
