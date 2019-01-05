/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface ClosedMultiTaskChain
	extends 
		MultiTaskChain<
			TransmitterTask<?>,
                MultiTransmitterTask<?,?>,
			ReceiverTask<?>,
                MultiReceiverTask<?,?>> {

}
