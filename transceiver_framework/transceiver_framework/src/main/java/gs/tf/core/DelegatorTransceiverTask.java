/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

public class DelegatorTransceiverTask<
	DATA_IN_TYPE, 
	RECEIVER_TASK_TYPE extends ReceiverTask<DATA_IN_TYPE>,
	DATA_OUT_TYPE,
	TRANSMITTER_TASK_TYPE extends TransmitterTask<DATA_OUT_TYPE>> 
	extends 
		AbstractConcurrentTask 
	implements 
		TransceiverTask<DATA_IN_TYPE, DATA_OUT_TYPE> {

	private final RECEIVER_TASK_TYPE m_receiver;
	private final TRANSMITTER_TASK_TYPE m_transmitter;
	final boolean m_hasLifeCycle;	
	final ExecutorService m_executorService;
	
	public DelegatorTransceiverTask(
			RECEIVER_TASK_TYPE receiverTask,
			TRANSMITTER_TASK_TYPE transmitterTask) {
		this(receiverTask, transmitterTask, false, null);
	}
	
	public DelegatorTransceiverTask(
			RECEIVER_TASK_TYPE receiverTask,
			TRANSMITTER_TASK_TYPE transmitterTask,
			boolean hasLifeCycle,
			ExecutorService executorService) {
		super();
		
		if(receiverTask == null || transmitterTask == null) {
			throw new NullPointerException();
		}
		this.m_hasLifeCycle = hasLifeCycle;
		
		if(hasLifeCycle) {
			if(!receiverTask.isNotStarted() || !transmitterTask.isNotStarted()) {
				throw new RuntimeException(new IllegalStatusException("At least one of the provided tasks has already started. "
						+ "This delegator instance has a life cycle (hasLifeCycle was true) and - in turn - "
						+ "should have full control over the life cycle of the provided task instances."));
			}
			if(executorService == null) {
				throw new NullPointerException();
			}
		}
		this.m_executorService = executorService;
		this.m_receiver = receiverTask;
		this.m_transmitter = transmitterTask;
	}
	
	protected RECEIVER_TASK_TYPE getInternalReceiverTask(){
		return this.m_receiver;
	}
	
	protected TRANSMITTER_TASK_TYPE getInternalTransmitterTask(){
		return this.m_transmitter;
	}
	
	@Override
	public Lock getInConnectionLock() {
		return this.m_receiver.getInConnectionLock();
	}

	@Override
	public void setInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask)
			throws UnacceptedConcurrentTaskException {
		this.m_receiver.setInConnection(transmitterTask);		
	}

	@Override
	public TransmitterTask<? extends DATA_IN_TYPE> getSourceTransmitterTask() {
		return this.m_receiver.getSourceTransmitterTask();
	}

	@Override
	public void addToInDataQueue(Collection<? extends DATA_IN_TYPE> dataSet)
			throws InterruptedException, IllegalStatusException {
		this.m_receiver.addToInDataQueue(dataSet);
	}

	@Override
	public void addToInDataQueue(DATA_IN_TYPE dataElement) throws InterruptedException, IllegalStatusException {
		this.m_receiver.addToInDataQueue(dataElement);
	}

	@Override
	public boolean hasInConnection() {
		return this.m_receiver.hasInConnection();
	}

	@Override
	public int getInQueueSize() {
		return this.m_receiver.getInQueueSize();
	}

	@Override
	public Lock getOutConnectionLock() {
		return this.m_transmitter.getOutConnectionLock();
	}

	@Override
	public void setOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask)
			throws UnacceptedConcurrentTaskException {
		this.m_transmitter.setOutConnection(receiverTask);
	}

	@Override
	public ReceiverTask<? super DATA_OUT_TYPE> getTargetReceiverTask() {
		return this.m_transmitter.getTargetReceiverTask();
	}

	@Override
	public boolean hasOutConnection() {
		return this.m_transmitter.hasOutConnection();
	}

	@Override
	public boolean hasInOutConnections() {
		return this.m_receiver.hasInConnection() && this.m_transmitter.hasOutConnection();
	}

	@Override
	@SuppressWarnings("unchecked")
	public TransmitterTask<DATA_OUT_TYPE> asTransmitterTask() {
		if(this.m_transmitter instanceof TransceiverTask) {
			return ((TransceiverTask<?, DATA_OUT_TYPE>) this.m_transmitter).asTransmitterTask();
		}
		else {
			return this.m_transmitter;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public ReceiverTask<DATA_IN_TYPE> asReceiverTask() {
		if(this.m_receiver instanceof TransceiverTask) {
			return ((TransceiverTask<DATA_IN_TYPE, ?>) this.m_receiver).asReceiverTask();
		}
		else {
			return this.m_receiver;
		}
	}

	@Override
	protected void preWork() {
		if(!this.m_hasLifeCycle) {
			throw new IllegalStateException("This instance was initialized with 'hasLifeCycle == false'.");
		}
		assert(this.isRunning());

        if(!this.hasInOutConnections()){
            throw new RuntimeException(new MissingConnectionException("Instance has missing connections."));
        }

        this.m_executorService.submit(this.m_receiver);
        this.m_executorService.submit(this.m_transmitter);
        while(this.m_receiver.isNotStarted() || this.m_transmitter.isNotStarted()){
            Thread.yield();
        }
	}

	@Override
	protected void doWorkChunk() {
		assert(this.m_hasLifeCycle);
		InterruptedException interruptException = null;
		synchronized (WAIT_LOCK) {
			try {
				WAIT_LOCK.wait();
			} catch (InterruptedException e) {
				interruptException = e;
			}
		}
		if(interruptException != null) {
			Boolean terminatedWithInterrupt = this.terminateCalledWithInterrupt();
			if (terminatedWithInterrupt == null || !terminatedWithInterrupt) {
				throw new IllegalStateException("Thread should only be interrupted during termination.", interruptException);
			}
		}
	}

	@Override
	protected void postWork() {
		assert(this.m_hasLifeCycle);
		assert(this.m_receiver.isRunning());
		if(!this.m_receiver.getSourceTransmitterTask().isTerminated()){
			throw new RuntimeException(
					new IllegalStatusException("This task is terminating before the input transmitter terminated."));
		}

		assert(this.m_transmitter.isRunning());
		if(!this.m_transmitter.getTargetReceiverTask().isRunning()){
			throw new RuntimeException(
					new IllegalStatusException("This task is terminating while the output receiver is not running."));
		}

		try {
			assert(this.terminateCalledWithInterrupt() != null);
			this.m_receiver.terminate(this.terminateCalledWithInterrupt());
			assert (this.m_receiver.isTerminating() || this.m_receiver.isTerminated());
			while (!this.m_receiver.isTerminated()) {
				Thread.yield();
			}

			this.midPostWork();

			this.m_transmitter.terminate(this.terminateCalledWithInterrupt());
			assert (this.m_transmitter.isTerminating() || this.m_transmitter.isTerminated());
			while (!this.m_transmitter.isTerminated()) {
				Thread.yield();
			}
		}
		catch(NotStartedException e){
			throw new IllegalStateException("This should not happen.", e);
		}

		assert(this.m_receiver.getInQueueSize() == 0);
	}
	
	// Called after receiver terminated, before transmitter terminated in postWork
    // Override if required for cleanup
    protected void midPostWork(){}
    
    @Override
    public String toString(){
        return super.toString() + "\n" +
                "\t" + " Internal receiver: \n\t\t" + this.m_receiver.toString() + "\n" +
                "\t" + " Internal transmitter: \n\t\t" + this.m_transmitter.toString();
    }
		
}