/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface MultiReceiverTaskChain<
		DATA_IN_TYPE,
		ROOT_TASK_TYPE extends ReceiverTask<? super DATA_IN_TYPE>,
		ROOT_TYPE extends MultiReceiverTask<? super DATA_IN_TYPE, ? extends ROOT_TASK_TYPE>,
		LEAF_TASK_TYPE extends ConcurrentTask,
		LEAF_TYPE extends MultiTask<? extends LEAF_TASK_TYPE>,
		MULTI_RECEIVER_VIEW_TASK_TYPE extends ReceiverTask<? super DATA_IN_TYPE>> 
		extends
			MultiTaskChain<ROOT_TASK_TYPE, ROOT_TYPE, LEAF_TASK_TYPE, LEAF_TYPE>,
			MultiReceiverTask<DATA_IN_TYPE, MULTI_RECEIVER_VIEW_TASK_TYPE>{
				
}
