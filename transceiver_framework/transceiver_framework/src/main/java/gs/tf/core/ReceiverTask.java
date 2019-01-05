/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import java.util.Collection;
import java.util.concurrent.locks.Lock;

public interface ReceiverTask<DATA_IN_TYPE> extends ConcurrentTask {

    Lock getInConnectionLock();
	void setInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask) throws UnacceptedConcurrentTaskException;
    TransmitterTask<? extends DATA_IN_TYPE> getSourceTransmitterTask();
    void addToInDataQueue(Collection<? extends DATA_IN_TYPE> dataSet) throws InterruptedException, IllegalStatusException;
    void addToInDataQueue(DATA_IN_TYPE dataElement) throws InterruptedException, IllegalStatusException;
    boolean hasInConnection();
    int getInQueueSize();
}
