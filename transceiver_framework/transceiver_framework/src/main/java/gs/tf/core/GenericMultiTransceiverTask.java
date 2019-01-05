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
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

public class GenericMultiTransceiverTask<
	DATA_IN_TYPE, 
	DATA_OUT_TYPE,
	TRANSCEIVER_TYPE extends TransceiverTask<? super DATA_IN_TYPE, ? extends DATA_OUT_TYPE>>
	extends 
		AbstractMultiTask<TRANSCEIVER_TYPE>
	implements 
		MultiTransceiverTask<DATA_IN_TYPE, DATA_OUT_TYPE, TRANSCEIVER_TYPE> {

	// Those are merely used as data structures - starting and terminating tasks performed by superclass AbstractMultiTask
    private final GenericMultiReceiverTask<DATA_IN_TYPE, TRANSCEIVER_TYPE> m_multiReceiver;
    private final GenericMultiTransmitterTask<DATA_OUT_TYPE, TRANSCEIVER_TYPE> m_multiTransmitter;

    public GenericMultiTransceiverTask(Collection<TRANSCEIVER_TYPE> transceiverTasks,
                                       ExecutorService executorService) {
    	super(transceiverTasks, executorService);
        this.m_multiReceiver = new GenericMultiReceiverTask<>(transceiverTasks,
                executorService);
        this.m_multiTransmitter = new GenericMultiTransmitterTask<>(transceiverTasks,
                executorService);
    }

    @Override
    public final int getNumInConnections() {
        return this.m_multiReceiver.getNumInConnections();
    }

    @Override
    public final int getNumMissingInConnections() {
        return this.m_multiReceiver.getNumMissingInConnections();
    }

    @Override
    public final boolean hasAllInConnections() {
        return this.m_multiReceiver.hasAllInConnections();
    }

    @Override
    public final Lock getInConnectionLock(){
        return this.m_multiReceiver.getInConnectionLock();
    }

    @Override
    public final ArrayList<ReceiverTask<? super DATA_IN_TYPE>> addInConnection(MultiTransmitterTask<? extends DATA_IN_TYPE, ?> multiTransmitterTask) throws UnacceptedConcurrentTaskException {
        return this.m_multiReceiver.addInConnection(multiTransmitterTask);
    }

    @Override
    public final ReceiverTask<? super DATA_IN_TYPE> addInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask) throws UnacceptedConcurrentTaskException {
        return this.m_multiReceiver.addInConnection(transmitterTask);
    }

    @Override
    public final ReceiverTask<? super DATA_IN_TYPE> getNextDisconnectedReceiverTask() {
        return this.m_multiReceiver.getNextDisconnectedReceiverTask();
    }

    @Override
    public final UnmodifiableList<Long> getDisconnectedInternalReceiverIDList() {
        return this.m_multiReceiver.getDisconnectedInternalReceiverIDList();
    }

    @Override
    public final int getNumOutConnections() {
        return this.m_multiTransmitter.getNumOutConnections();
    }

    @Override
    public final int getNumMissingOutConnections() {
        return this.m_multiTransmitter.getNumMissingOutConnections();
    }

    @Override
    public final boolean hasAllOutConnections() {
        return this.m_multiTransmitter.hasAllOutConnections();
    }

    @Override
    public final Lock getOutConnectionLock(){
        return this.m_multiTransmitter.getOutConnectionLock();
    }

    @Override
    public final ArrayList<TransmitterTask<? extends DATA_OUT_TYPE>> addOutConnection(MultiReceiverTask<? super DATA_OUT_TYPE, ?> multiReceiverTask) throws UnacceptedConcurrentTaskException {
        return this.m_multiTransmitter.addOutConnection(multiReceiverTask);
    }

    @Override
    public final TransmitterTask<? extends DATA_OUT_TYPE> addOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask) throws UnacceptedConcurrentTaskException {
        return this.m_multiTransmitter.addOutConnection(receiverTask);
    }

    @Override
    public final TransmitterTask<? extends DATA_OUT_TYPE> getNextDisconnectedTransmitterTask() {
        return this.m_multiTransmitter.getNextDisconnectedTransmitterTask();
    }

    @Override
    public final UnmodifiableList<Long> getDisconnectedInternalTransmitterIDList() {
        return this.m_multiTransmitter.getDisconnectedInternalTransmitterIDList();
    }

    @Override
    public final boolean hasAllInOutConnections(){
        return this.hasAllInConnections() && this.hasAllOutConnections();
    }

	@Override
	public boolean hasAllConnections() {		
		return this.hasAllInOutConnections();
	}
}
