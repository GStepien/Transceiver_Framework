/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

public interface MultiTransmitterTask<
	DATA_OUT_TYPE, 
	TRANSMITTER_TYPE extends TransmitterTask<? extends DATA_OUT_TYPE>>
	extends MultiTask<TRANSMITTER_TYPE> {

    int getNumOutConnections();
    int getNumMissingOutConnections();
    boolean hasAllOutConnections();

    Lock getOutConnectionLock();
    // Returns a list of this instance's internal transmitters that were connected to internal receivers of the provided multi receiver task
    ArrayList<TransmitterTask<? extends DATA_OUT_TYPE>> addOutConnection(MultiReceiverTask<? super DATA_OUT_TYPE, ?> multiReceiverTask) throws UnacceptedConcurrentTaskException;
    // Returns this instance's internal transmitter which was connected to the provided receiver task
    // Note: If receiverTask already connected to a transmitter, then it MUST be the transmitter returned by the next call to getNextDisconnectedTransmitterTask()
    //       - a call to addOutConnection(receiverTask) then simply serves as a method to update this instance's internal fields
    // Note2: If receiverTask is a TransceiverTask, then the result of its ((TransceiverTask<? super DATA_OUT_TYPE, ?)receiverTask).asReceiverTask()
    //        method is used as the receiver to which one if the internal transmitter tasks is connected to.
    // Note3: If the TransmitterTask transTask acquired via getNextDisconnectedTransmitterTask() (i.e., the internal transmitter which is to be connected to 
    //        the provided receiverTask) is a TransceiverTask, then the result of its ((TransceiverTask<? , ? extends DATA_OUT_TYPE>recTask).asTransmitterTask())
    //        method is used as the transmitter which is to be connected to the provided receiverTask.
    TransmitterTask<? extends DATA_OUT_TYPE> addOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask) throws UnacceptedConcurrentTaskException;

    TransmitterTask<? extends DATA_OUT_TYPE> getNextDisconnectedTransmitterTask();
    UnmodifiableList<Long> getDisconnectedInternalTransmitterIDList();

}