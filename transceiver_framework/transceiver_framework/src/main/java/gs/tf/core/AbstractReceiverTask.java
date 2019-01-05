/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public abstract class AbstractReceiverTask<DATA_IN_TYPE>
	extends 
		AbstractConcurrentTask 
	implements 
		ReceiverTask<DATA_IN_TYPE> {

    private static final Logger LOGGER = Logger.getLogger(AbstractReceiverTask.class.getName());

    private final ReentrantLock CONNECTION_LOCK;
    // No volatile required - access only via CONNECTION_LOCK
    private TransmitterTask<? extends DATA_IN_TYPE> m_transmitterTask;
    private final BlockingQueue<DATA_IN_TYPE> m_inQueue;

    private final Object IN_QUEUE_LOCK;

    private final Long m_timeoutInterval;
    // No volatile required - access only via IN_QUEUE_LOCK
    private boolean m_lastWasTimeout;

    // If *dataQueueCapacity is null, the respective internal BlockingQueue is initialized as LinkedBlockingQueue.
    // Otherwise it is initialized as an ArrayBlockingQueue with the provided capacity.
    public AbstractReceiverTask(Integer inDataQueueCapacity, Long timeoutInterval) {
        super();

        if(inDataQueueCapacity == null){
            this.m_inQueue = new LinkedBlockingQueue<>();
        }
        else{
            if(inDataQueueCapacity < 1){
                throw new IllegalArgumentException();
            }

            this.m_inQueue = new ArrayBlockingQueue<>(inDataQueueCapacity, true);
        }
        
        this.IN_QUEUE_LOCK = new Object();

        if(timeoutInterval != null && timeoutInterval < 0){
            throw new IllegalArgumentException();
        }
        this.m_lastWasTimeout = false;
        this.m_timeoutInterval = timeoutInterval;
        this.CONNECTION_LOCK = new ReentrantLock(true);
    }

    @Override
    public final Lock getInConnectionLock(){
        return this.CONNECTION_LOCK;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void setInConnection(TransmitterTask<? extends DATA_IN_TYPE> transmitterTask) throws UnacceptedConcurrentTaskException {
        if(!this.isNotStarted()){
            throw new RuntimeException(new IllegalStatusException("Connections should only be set when tasks have not started yet."));
        }

        if (transmitterTask == null) {
            throw new NullPointerException();
        }

        TransmitterTask<? extends DATA_IN_TYPE> connectFrom;

        if (transmitterTask instanceof TransceiverTask) {
            // If transmitter extends AbstractTransmitterTask, this ensures that
            // m_transmitterTask will be set to the abstract transceiver's internal transmitter task
            connectFrom = ((TransceiverTask<?, ? extends DATA_IN_TYPE>) transmitterTask).asTransmitterTask();
        } else {
            connectFrom = transmitterTask;
        }

        boolean ourLock;
        if((ourLock = this.CONNECTION_LOCK.tryLock()) && connectFrom.getOutConnectionLock().tryLock()){
            try {
                if (this.m_transmitterTask == null) {
                    this.m_transmitterTask = connectFrom;

                    ReceiverTask<?> otherRecTask = connectFrom.getTargetReceiverTask();
                    if (otherRecTask == null) {
                        connectFrom.setOutConnection(this);
                    } else if (otherRecTask != this) {
                        throw new AlreadyConnectedException("The source is already connected to a different object.");
                    }
                } else if (this.m_transmitterTask != connectFrom) {
                    throw new AlreadyConnectedException("This instance is already connected.");
                } else {
                    LOGGER.warning("Multiple setInConnection calls with same source transmitter argument.");
                }
            }
            finally {
                connectFrom.getOutConnectionLock().unlock();
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
    public final TransmitterTask<? extends DATA_IN_TYPE> getSourceTransmitterTask() {
    	this.CONNECTION_LOCK.lock();
    	try {
    		return this.m_transmitterTask;
    	}
    	finally {
    		this.CONNECTION_LOCK.unlock();
    	}
    }

    // Blocking.
    @Override
    public final void addToInDataQueue(Collection<? extends DATA_IN_TYPE> dataSet) throws InterruptedException, IllegalStatusException {
        synchronized (this.IN_QUEUE_LOCK) {
            if(dataSet == null){
                throw new NullPointerException();
            }
            if (!this.isRunning()){
                throw new IllegalStatusException();
            }
            if(dataSet.size() > 0) {
                if(this.m_timeoutInterval == null) {
                    for (DATA_IN_TYPE dataElement : dataSet) {
                        this.m_inQueue.put(dataElement);
                    }
                }
                else{
                    boolean timeout = this.m_lastWasTimeout;
                    int offered = 0;
                    for (DATA_IN_TYPE dataElement : dataSet) {
                    	if(this.m_inQueue.offer(dataElement)) {
                    		timeout = false;
                    	}
                    	else if(!this.m_lastWasTimeout){
                    		timeout = !this.m_inQueue.offer(
                    				dataElement, 
                    				this.m_timeoutInterval, 
                    				TimeUnit.MILLISECONDS);                    		
                    	}

                    	if(!timeout){
                    		offered++;
                    	}
                    	else {
                    		LOGGER.warning("Discarding "+(dataSet.size() - offered)+ " input elements due to timeout in "+
                    				this.getClass().getName()+", running in thread with ID "+this.getRunThreadID()+" and name "+
                    				this.getRunThreadName()+ ".");
                    		break;
                    	}                	
                    }
                    this.m_lastWasTimeout = timeout;
                }
            }
        }
    }

    // Blocking.
    @Override
    public final void addToInDataQueue(DATA_IN_TYPE dataElement) throws InterruptedException, IllegalStatusException {
        synchronized (this.IN_QUEUE_LOCK) {
            List<DATA_IN_TYPE> list = new ArrayList<>(1);
            list.add(dataElement);
            this.addToInDataQueue(list);
        }
    }

    @Override
    public final boolean hasInConnection(){
    	// Synchronization of non-volatile m_transmitterTask happens in getSourceTransmitterTask()
        return(this.getSourceTransmitterTask() != null);
    }

    @Override
    protected void preWork(){
        assert(this.isRunning());
        if(!this.hasInConnection()){
            throw new RuntimeException(new MissingConnectionException("Instance has no in connection."));
        }
        // No connection lock synchronization required because implicit 
        // happens-after synchronization happened in hasInConnection()
        assert(!(this.m_transmitterTask instanceof AbstractTransceiverTask));

        // Check predecessors to ensure no cycles present
        // ... but only if this receiver is a leaf/sink (i.e., not part of a transceiver)
        if(!(this instanceof AbstractTransceiverTask.InternalReceiverTask) &&
        		this.checkPredecessorsForCycles(new HashSet<>())) {
        	throw new IllegalStateException("Transceiver structure contains at least one cycle.");
        }
    }

    // Returns true if cycle found
    boolean checkPredecessorsForCycles(Set<? super ConcurrentTask> visitedSuccessors) {
    	if(visitedSuccessors.contains(this)) {
    		return true;
    	}
    	else {
    		assert(this.getSourceTransmitterTask() != null);
    		// No connection lock synchronization required because implicit 
            // happens-after synchronization happened in getSourceTransmitterTask()
            if(this.m_transmitterTask == null) {
    			throw new NullPointerException("No transmitter connected to this receiver yet.");
    		}
    		else if(visitedSuccessors.contains(this.m_transmitterTask)){
    			return true;
    		}
    		else {
    			if(this.m_transmitterTask instanceof AbstractTransceiverTask.InternalTransmitterTask) {
                    // Transmitter transmitting to this receiver is not a root/source (i.e. it is part of a transceiver)
    				visitedSuccessors.add(this);
        			visitedSuccessors.add(this.m_transmitterTask);
        			
        			AbstractTransceiverTask.InternalTransmitterTask<?> trans = 
        					(AbstractTransceiverTask.InternalTransmitterTask<?>) this.m_transmitterTask;
        			assert(trans.getEnclosingTransceiverTask().getInternalReceiverTask() != null);
        			return trans.getEnclosingTransceiverTask().getInternalReceiverTask().checkPredecessorsForCycles(visitedSuccessors);
    			}
    			else {
    				return false;
    			}
    		}
    	}
    }
    
    // Blocking, but only for short time: Until m_inQueue is unlocked.
    @Override
    protected final void doWorkChunk(){
        if(this.m_inQueue.size() > 0) {
            DATA_IN_TYPE nextElem;
            try {
                nextElem = this.m_inQueue.take();
            } catch (InterruptedException e) {
                Boolean terminatedWithInterrupt = this.terminateCalledWithInterrupt();
                if(terminatedWithInterrupt == null || !terminatedWithInterrupt){
                    throw new IllegalStateException("Thread should only be interrupted during termination.");
                }
                else{
                    LOGGER.warning("Discarding unprocessed element due to interrupt-termination.");
                    return;
                }
            }

            try {
                this.processDataElement(nextElem);
            } catch (InterruptedException e) {
                Boolean terminatedWithInterrupt = this.terminateCalledWithInterrupt();
                if(terminatedWithInterrupt == null || !terminatedWithInterrupt){
                    throw new IllegalStateException("Thread should only be interrupted during termination.");
                }
                else{
                    LOGGER.warning("Discarding unprocessed element due to interrupt-termination.");
                    return;
                }
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
        if(!this.m_transmitterTask.isTerminated()){
            throw new RuntimeException(new IllegalStatusException("This task is terminating before the input transmitter terminated."));
        }

        assert(this.terminateCalledWithInterrupt() != null);
        if(!this.terminateCalledWithInterrupt()) {
            while (this.m_inQueue.size() > 0) {
                DATA_IN_TYPE nextElem;
                try {
                    nextElem = this.m_inQueue.take();
                } catch (InterruptedException e) {
                    throw new IllegalStateException("No interrupt should happen at this point.", e);
                }
                try {
                    this.processDataElement(nextElem);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("No interrupt should happen at this point.", e);
                }
            }
        }
        else{
            ArrayList<DATA_IN_TYPE> remainingData = new ArrayList<>(this.m_inQueue.size());
            this.m_inQueue.drainTo(remainingData);
            if(remainingData.size() > 0) {
                LOGGER.warning("Discarding " + remainingData.size() + " elements due to interrupt-termination.");
            }
            assert(this.m_inQueue.isEmpty());
        }
    }

    @Override
    public final int getInQueueSize(){
        return this.m_inQueue.size();
    }

    // No blocking.
    protected abstract void processDataElement(DATA_IN_TYPE dataElement) throws InterruptedException;

    @Override
    public String toString(){
        return super.toString() + ", receiving from: "+(this.hasInConnection() ? this.getSourceTransmitterTask().getID() : null);
    }
}
