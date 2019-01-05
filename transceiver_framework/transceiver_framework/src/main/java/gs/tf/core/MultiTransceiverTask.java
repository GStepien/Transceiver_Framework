/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface MultiTransceiverTask<
	DATA_IN_TYPE, 
	DATA_OUT_TYPE, 
	TRANSCEIVER_TYPE extends TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>>
	extends 
		MultiReceiverTask<
			DATA_IN_TYPE, 
			TRANSCEIVER_TYPE>, 
		MultiTransmitterTask<
			DATA_OUT_TYPE, 
			TRANSCEIVER_TYPE>{
	
    boolean hasAllInOutConnections();
    
}
