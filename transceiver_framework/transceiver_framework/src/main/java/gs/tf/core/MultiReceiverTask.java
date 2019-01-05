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

public interface MultiReceiverTask<
	DATA_IN_TYPE, 
	RECEIVER_TYPE extends ReceiverTask<? super DATA_IN_TYPE>>
	extends MultiTask<RECEIVER_TYPE> {

    int getNumInConnections();
    int getNumMissingInConnections();
    boolean hasAllInConnections();

    Lock getInConnectionLock();
    // Returns a list of this instance's internal receivers to which internal transmitters of the provided multi transmitter task were connected
    ArrayList<ReceiverTask<? super DATA_IN_TYPE>> addInConnection(MultiTransmitterTask<? extends DATA_IN_TYPE, ?> multiTransmitterTask) throws UnacceptedConcurrentTaskException;
    // Returns this instance's internal receiver to which the provided transmitter task was connected
    // Note: If transmitterTask already connected to a receiver, then it MUST be the receiver returned by the next call to getNextDisconnectedReceiverTask()
    //       - a call to addInConnection(transmitterTask) then simply serves as a method to update this instance's internal fields
    // Note2: If transmitterTask is a TransceiverTask, then the result of its ((TransceiverTask<?, ? extends DATA_IN_TYPE>)transmitterTask).asTransmitterTask()
    //        method is used as the transmitter which is to be connected to one if the internal receiver tasks.
    // Note3: If the ReceiverTask recTask acquired via getNextDisconnectedReceiverTask() (i.e., the internal receiver to which the provided transmitterTask is to
    //        be connected to) is a TransceiverTask, then the result of its ((TransceiverTask<? super DATA_IN_TYPE, ?>recTask).asReceiverTask())
    //        method is used as the receiver to which the provided transmitterTask is to be connected to.
    ReceiverTask<? super DATA_IN_TYPE> addInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask) throws UnacceptedConcurrentTaskException;

    ReceiverTask<? super DATA_IN_TYPE> getNextDisconnectedReceiverTask();
    UnmodifiableList<Long> getDisconnectedInternalReceiverIDList();
}
