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
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public abstract class AbstractTransmitterTask<DATA_OUT_TYPE> extends AbstractConcurrentTask implements TransmitterTask<DATA_OUT_TYPE> {
    private final ReentrantLock CONNECTION_LOCK;
 // No volatile required - access only via CONNECTION_LOCK    
    private ReceiverTask<? super DATA_OUT_TYPE> m_receiverTask = null;
    private final int m_maxDataChunkSize;

    private static final Logger LOGGER = Logger.getLogger(AbstractTransmitterTask.class.getName());

    public AbstractTransmitterTask(int maxDataChunkSize) {
        super();

        if(maxDataChunkSize < 1){
            throw new IllegalArgumentException();
        }
        this.m_maxDataChunkSize = maxDataChunkSize;
        this.CONNECTION_LOCK = new ReentrantLock(true);
    }

    @Override
    public final Lock getOutConnectionLock(){
        return this.CONNECTION_LOCK;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void setOutConnection(ReceiverTask<? super DATA_OUT_TYPE> receiverTask) throws UnacceptedConcurrentTaskException {
        if(!this.isNotStarted()){
            throw new RuntimeException(new IllegalStatusException("Connections should only be set when tasks have not started yet."));
        }

        if (receiverTask == null) {
            throw new NullPointerException();
        }

        ReceiverTask<? super DATA_OUT_TYPE> connectTo;

        if (receiverTask instanceof TransceiverTask) {
            // If receiver task also extends AbstractTransceiverTask, this ensures that
            // m_receiverTask will be set to the AbstractTransceiverTask's internal receiver task
            connectTo = ((TransceiverTask<? super DATA_OUT_TYPE, ?>) receiverTask).asReceiverTask();
        } else {
            connectTo = receiverTask;
        }

        boolean ourLock;
        if((ourLock = this.CONNECTION_LOCK.tryLock()) && connectTo.getInConnectionLock().tryLock()) {
            try {
                if (this.m_receiverTask == null) {
                    this.m_receiverTask = connectTo;

                    // Check if receiver already connected
                    TransmitterTask<?> otherTransTask = connectTo.getSourceTransmitterTask();
                    if (otherTransTask == null) {
                        connectTo.setInConnection(this);
                    } else if (otherTransTask != this) {
                        throw new AlreadyConnectedException("The target is already connected to a different object.");
                    }
                } else if (this.m_receiverTask != connectTo) {
                    throw new AlreadyConnectedException("This instance is already connected to a different receiver.");
                } else {
                    LOGGER.warning("Multiple setOutConnection calls with same target receiver argument.");
                }
            }
            finally {
                connectTo.getInConnectionLock().unlock();
                this.CONNECTION_LOCK.unlock();
            }
        }
        else{
            if(ourLock){ // If our lock was acquired, then the other's lock acquisition must have failed
                this.CONNECTION_LOCK.unlock();
            } // If our lock was NOT acquired, then its guaranteed that no attempt at acquiring the other's lock was performed

            throw new IllegalStateException("Concurrent connection attempt!");
        }

    }

    @Override
    public final ReceiverTask<? super DATA_OUT_TYPE> getTargetReceiverTask() {
    	this.CONNECTION_LOCK.lock();
    	try {
    		return this.m_receiverTask;
    	}
    	finally {
    		this.CONNECTION_LOCK.unlock();
    	}
    }

    // Blocking
    // TODO make forward methods private, remove while(this.m_receiverTask.isNotStarted()) (this is already done in preWork()
    protected final synchronized void forwardToReceiver(Collection<? extends DATA_OUT_TYPE> dataSet) throws InterruptedException, IllegalStatusException {
    	if(!this.hasOutConnection()) {
    		throw new IllegalStateException("Instance not connected yet.");
    	}
    	
    	// Receiver might not have started yet
    	// (No connection lock synchronization required because implicit 
        // happens-after synchronization happened in hasOutConnection())
    	while(this.m_receiverTask.isNotStarted()) {
    		Thread.yield();
    	}
    	
        if (!this.isRunning() && !this.isTerminating()) {
            throw new IllegalStatusException();
        }
        if(dataSet == null) {
            LOGGER.warning("Null data set provided. Discarding.");
        }
        this.m_receiverTask.addToInDataQueue(dataSet);
    }

    // Blocking
    protected final synchronized void forwardToReceiver(DATA_OUT_TYPE dataElement) throws InterruptedException, IllegalStatusException {
    	if(!this.hasOutConnection()) {
    		throw new IllegalStateException("Instance not connected yet.");
    	}
    	
    	// Receiver might not have started yet
    	// (No connection lock synchronization required because implicit 
        // happens-after synchronization happened in hasOutConnection())
    	while(this.m_receiverTask.isNotStarted()) {
    		Thread.yield();
    	}
    	
        if (!this.isRunning() && !this.isTerminating()) {
            throw new IllegalStatusException();
        }

        if(dataElement == null) {
            LOGGER.warning("Null data element provided. Discarding.");
        }
        this.m_receiverTask.addToInDataQueue(dataElement);
    }

    @Override
    public final boolean hasOutConnection(){
    	// Synchronization of non-volatile m_receiverTask happens in getTargetReceiverTask()        
        return(this.getTargetReceiverTask() != null);
    }

    @Override
    protected void preWork() {
        assert(this.isRunning());
        if(!this.hasOutConnection()){
            throw new RuntimeException(new MissingConnectionException("Instance has no out connection."));
        }
        
        // No connection lock synchronization required because implicit 
        // happens-after synchronization happened in hasOutConnection()
        assert(!(this.m_receiverTask instanceof AbstractTransceiverTask));
        // Wait for receiver to start
        while(this.m_receiverTask.isNotStarted()){
            Thread.yield();
        }
    }

    protected int getMaxDataChunkSize(){
        return this.m_maxDataChunkSize;
    }
    // Non blocking: Return either null or a collection of size zero to indicate no data available
    // Result size may not succeed getMaxDataChunkSize
    protected abstract Collection<DATA_OUT_TYPE> getNextDataChunk();

    // Blocking.
    @Override
    protected final void doWorkChunk() {
        Collection<DATA_OUT_TYPE> nextChunk = this.getNextDataChunk();

        if(nextChunk != null && nextChunk.size() > this.m_maxDataChunkSize){
            throw new IllegalStateException();
        }

        if(nextChunk != null && nextChunk.size() > 0) {
            try {
                this.forwardToReceiver(nextChunk);
            } catch (InterruptedException e) {
                Boolean terminatedWithInterrupt = this.terminateCalledWithInterrupt();
                if(terminatedWithInterrupt == null || !terminatedWithInterrupt){
                    throw new IllegalStateException("Thread should only be interrupted during termination.");
                }
                else{
                    LOGGER.warning("Could not forward full chunk of size "+nextChunk.size()+" to receiver, due to " +
                            "interrupt-termination.");
                    return;
                }
            } catch (IllegalStatusException e) {
                throw new IllegalStateException(e);
            }
        }
        else{
            Thread.yield();
        }
    }

    @Override
    protected void postWork() {
        assert(this.isTerminating());
        // No connection lock synchronization required because implicit 
        // happens-after synchronization happened in hasInConnection() in preWork() 
        // (which is executed by the same thread)       
        if(!this.m_receiverTask.isRunning()){
            throw new RuntimeException(new IllegalStatusException("This task is terminating while the output receiver is not running."));
        }
    }

    @Override
    public String toString(){
        return super.toString() + ", transmitting to: "+(this.hasOutConnection() ? this.getTargetReceiverTask().getID() : null);
    }
}
