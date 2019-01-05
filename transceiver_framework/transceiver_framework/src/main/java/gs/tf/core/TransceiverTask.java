/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface TransceiverTask<DATA_IN_TYPE, DATA_OUT_TYPE> 
	extends 
		ReceiverTask<DATA_IN_TYPE>, 
		TransmitterTask<DATA_OUT_TYPE>{
    boolean hasInOutConnections();

    // TODO Find better solution for GenericMultiTasks add*Connection method, where this is required
    // "better" as in hiding the AbstractTransceivers internal Receiver and Transmitter task
    TransmitterTask<DATA_OUT_TYPE> asTransmitterTask();
    ReceiverTask<DATA_IN_TYPE> asReceiverTask();
}
