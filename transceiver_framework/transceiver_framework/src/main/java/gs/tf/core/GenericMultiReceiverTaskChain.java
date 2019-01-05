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

public class GenericMultiReceiverTaskChain<
	DATA_IN_TYPE,
	RECEIVER_TASK_TYPE extends ReceiverTask<DATA_IN_TYPE>,
	MULTI_RECEIVER_TASK_TYPE extends MultiReceiverTask<DATA_IN_TYPE, ? extends RECEIVER_TASK_TYPE>>
    extends
        GenericMultiTaskChain<
                    RECEIVER_TASK_TYPE,
                    MULTI_RECEIVER_TASK_TYPE,
                    ConcurrentTask,
                MultiTask<? extends ConcurrentTask>>
    implements 
    	MultiReceiverTaskChain<
    		DATA_IN_TYPE, 
    		RECEIVER_TASK_TYPE,
    		MULTI_RECEIVER_TASK_TYPE,
    		ConcurrentTask,
    		MultiTask<? extends ConcurrentTask>,
    		ReceiverTask<? super DATA_IN_TYPE>> {
    	
	public GenericMultiReceiverTaskChain(Collection<? extends MultiTask<?>> multiTasks,
                                         Map<? extends MultiTask<?>, ? extends Collection<? extends MultiTask<?>>> multiTaskConnectionDAG,
                                         ExecutorService executorService) throws UnacceptedConcurrentTaskException {
        super(multiTasks, multiTaskConnectionDAG, executorService);

        // No good reason to syntactically forbid this. Also: Superclass constructor ensures that root is receiver
        /*if(this.getLeafMultiTask() instanceof MultiTransmitterTask){
            // Use GenericMultiTransceiverTaskChain if this is the case.
            throw new IllegalArgumentException("Leaf may not be a MultiTransmitterTask");
        }*/
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
		return this.getRootMultiTask().hasAllConnections();
	}

	@Override
	public int getNumInternalTasks() {
		return this.getRootMultiTask().getNumInternalTasks();
	}

	@Override
	public ReceiverTask<? super DATA_IN_TYPE> getInternalTaskByID(long taskID) {
		return this.getRootMultiTask().getInternalTaskByID(taskID);
	}

	@Override
	public ReceiverTask<? super DATA_IN_TYPE> getInternalTaskByIndex(int index) {
		return this.getRootMultiTask().getInternalTaskByIndex(index);
	}

	@Override
	public UnmodifiableSet<Long> getInternalTaskIDSet() {
		return this.getRootMultiTask().getInternalTaskIDSet();
	}
}
