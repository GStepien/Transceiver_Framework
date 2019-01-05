/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface MultiTransceiverTaskChain<
	DATA_IN_TYPE,
	DATA_OUT_TYPE,
	ROOT_TASK_TYPE extends ReceiverTask<? super DATA_IN_TYPE>,
	ROOT_TYPE extends MultiReceiverTask<? super DATA_IN_TYPE, ? extends ROOT_TASK_TYPE>,
	LEAF_TASK_TYPE extends TransmitterTask<? extends DATA_OUT_TYPE>,
	LEAF_TYPE extends MultiTransmitterTask<? extends DATA_OUT_TYPE, ? extends LEAF_TASK_TYPE>,
	MULTI_TRANSCEIVER_VIEW_TASK_TYPE extends TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>> 
	extends
        MultiReceiverTaskChain<
    		DATA_IN_TYPE,
    		ROOT_TASK_TYPE,
    		ROOT_TYPE,
    		LEAF_TASK_TYPE,
    		LEAF_TYPE,
    		MULTI_TRANSCEIVER_VIEW_TASK_TYPE>,
		MultiTransmitterTaskChain<
			DATA_OUT_TYPE,
			ROOT_TASK_TYPE,
			ROOT_TYPE,
			LEAF_TASK_TYPE,
			LEAF_TYPE,
			MULTI_TRANSCEIVER_VIEW_TASK_TYPE>,
        MultiTransceiverTask<DATA_IN_TYPE, DATA_OUT_TYPE, MULTI_TRANSCEIVER_VIEW_TASK_TYPE> {
		 
}