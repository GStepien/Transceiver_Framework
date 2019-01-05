/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import java.util.concurrent.locks.Lock;

public interface TransmitterTask<DATA_OUT_TYPE> extends ConcurrentTask {

    Lock getOutConnectionLock();
    void setOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask) throws UnacceptedConcurrentTaskException;
    ReceiverTask<? super DATA_OUT_TYPE> getTargetReceiverTask();
    boolean hasOutConnection();

}
