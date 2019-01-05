/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface MultiTransmitterTaskChain<
		DATA_OUT_TYPE,
		ROOT_TASK_TYPE extends ConcurrentTask,
		ROOT_TYPE extends MultiTask<? extends ROOT_TASK_TYPE>,
		LEAF_TASK_TYPE extends TransmitterTask<? extends DATA_OUT_TYPE>,
		LEAF_TYPE extends MultiTransmitterTask<? extends DATA_OUT_TYPE, ? extends LEAF_TASK_TYPE>,
		MULTI_TRANSMITTER_VIEW_TASK_TYPE extends TransmitterTask<? extends DATA_OUT_TYPE>>
		extends
			MultiTaskChain<ROOT_TASK_TYPE, ROOT_TYPE, LEAF_TASK_TYPE, LEAF_TYPE>,
			MultiTransmitterTask<DATA_OUT_TYPE, MULTI_TRANSMITTER_VIEW_TASK_TYPE>{		
			
}