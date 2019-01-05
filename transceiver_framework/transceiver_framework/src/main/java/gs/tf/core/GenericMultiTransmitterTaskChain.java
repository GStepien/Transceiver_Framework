/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.collections4.set.UnmodifiableSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

public class GenericMultiTransmitterTaskChain<
	DATA_OUT_TYPE,
	TRANSMITTER_TASK_TYPE extends TransmitterTask<DATA_OUT_TYPE>,
	MULTI_TRANSMITTER_TASK_TYPE extends MultiTransmitterTask<DATA_OUT_TYPE, ? extends TRANSMITTER_TASK_TYPE>>
    extends
        GenericMultiTaskChain<
                    ConcurrentTask,
                MultiTask<? extends ConcurrentTask>,
                    TRANSMITTER_TASK_TYPE,
                    MULTI_TRANSMITTER_TASK_TYPE>
    implements 
    	MultiTransmitterTaskChain<
	    	DATA_OUT_TYPE,
	    	ConcurrentTask,
			MultiTask<? extends ConcurrentTask>,
			TRANSMITTER_TASK_TYPE,
			MULTI_TRANSMITTER_TASK_TYPE,
			TransmitterTask<? extends DATA_OUT_TYPE>>{

	public GenericMultiTransmitterTaskChain(Collection<? extends MultiTask<?>> multiTasks,
                                            Map<? extends MultiTask<?>, ? extends Collection<? extends MultiTask<?>>> multiTaskConnectionDAG,
                                            ExecutorService executorService) throws UnacceptedConcurrentTaskException {
        super(multiTasks, multiTaskConnectionDAG, executorService);

        // No reason to forbid this. Also: Superclass constructor ensures that leaf is transmitter
        /*if(this.getRootMultiTask() instanceof MultiReceiverTask){
            // Use GenericMultiTransceiverTaskChain if this is the case.
            throw new IllegalArgumentException("Root may not be a MultiReceiverTask");
        }*/
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
	public boolean hasAllConnections() {
		return this.getLeafMultiTask().hasAllConnections();
	}

	@Override
	public int getNumInternalTasks() {
		return this.getLeafMultiTask().getNumInternalTasks();
	}

	@Override
	public TransmitterTask<? extends DATA_OUT_TYPE> getInternalTaskByID(long taskID) {
		return this.getLeafMultiTask().getInternalTaskByID(taskID);
	}

	@Override
	public TransmitterTask<? extends DATA_OUT_TYPE> getInternalTaskByIndex(int index) {
		return this.getLeafMultiTask().getInternalTaskByIndex(index);
	}

	@Override
	public UnmodifiableSet<Long> getInternalTaskIDSet() {
		return this.getLeafMultiTask().getInternalTaskIDSet();
	}
}
