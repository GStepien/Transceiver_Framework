/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import gs.utils.Concurrency;

import java.util.logging.Logger;

public abstract class AbstractConcurrentTask implements ConcurrentTask {
    final Object WAIT_LOCK = new Object();

    private static final Logger LOGGER = Logger.getLogger(AbstractConcurrentTask.class.getName());
    private volatile Thread m_runThread;

    private final long m_id;
    private final Object STATUS_LOCK;
    // No volatile required -> access only via STATUS_LOCK
    private TaskStatus m_status;
    private Boolean m_terminateCalleddWithInterrupt;

    private volatile String m_threadName;
    private volatile Long m_threadID;

    public AbstractConcurrentTask(){
        this.m_id = Concurrency.getNextID();
        if(this.m_id < 0){
            throw new RuntimeException("ID overflow occurred.");
        }
        this.m_status = TaskStatus.NOT_STARTED;
        this.STATUS_LOCK = new Object();
        this.m_threadName = null;
        this.m_threadID = null;
        this.m_runThread = null;
        this.m_terminateCalleddWithInterrupt = null;
    }

    @Override
    public final long getID() {
        return m_id;
    }

    // Note: Result is immediately outdated.
    @Override
    public final TaskStatus getTaskStatus() {
        synchronized (this.STATUS_LOCK) {
            return this.m_status;
        }
    }

    // Note: Result is immediately outdated.
    @Override
    public final boolean isRunning() {
        TaskStatus status = this.getTaskStatus();
        return status == TaskStatus.RUNNING;
    }

    // Note: Result is immediately outdated.
    @Override
    public final boolean isNotStarted() {
        TaskStatus status = this.getTaskStatus();
        return status == TaskStatus.NOT_STARTED;
    }

    // Note: Result is immediately outdated.
    @Override
    public final boolean isTerminating() {
        TaskStatus status = this.getTaskStatus();
        return status == TaskStatus.TERMINATING;
    }

    // Note: Result is immediately outdated.
    @Override
    public final boolean isTerminated() {
        TaskStatus status = this.getTaskStatus();
        return status == TaskStatus.TERMINATED;
    }

    // Returns null if terminate has not been called yet
    protected Boolean terminateCalledWithInterrupt(){
    	synchronized(this.STATUS_LOCK) {
    		return this.m_terminateCalleddWithInterrupt;
    	}
    }

    @Override
    public final boolean terminate(boolean interrupt) throws NotStartedException{
        assert(this.getTaskStatus() != null);

        synchronized (this.STATUS_LOCK){
            if(this.isTerminated() || this.isTerminating()){
                return false;
            }
            else if(this.isRunning()){
                this.m_status = TaskStatus.TERMINATING;
                this.m_terminateCalleddWithInterrupt = interrupt;
                if(interrupt) {
                    this.m_runThread.interrupt();
                }
                synchronized (WAIT_LOCK) {
                    WAIT_LOCK.notifyAll();
                }

                return true;
            }
            else{
                throw new NotStartedException();
            }
        }
    }

    protected String getRunThreadName(){
        return this.m_threadName;
    }

    protected long getRunThreadID(){
        return this.m_threadID;
    }

    protected Thread getRunThread(){
        return this.m_runThread;
    }

    @Override
    public final void run(){
        assert(this.getTaskStatus() != null);
        this.m_runThread = Thread.currentThread();
        this.m_threadName = this.m_runThread.getName();
        this.m_threadID = this.m_runThread.getId();
        LOGGER.config("Entering run() of: " +
                "Classname: "+this.getClass().getName()+", ID: "+this.getID()+
                ", Thread ID: "+ this.m_threadID+", Thread name: "+ this.m_threadName);

        // Synchronized is enough here since there is only one instance
        // that should be able to start / terminate this -> the corresponding TransmittersBoss instance.
        synchronized (this.STATUS_LOCK) {
            assert (this.isNotStarted());
            this.m_status = TaskStatus.RUNNING;
            assert (this.isRunning());
        }

        this.preWork();

        while (true) {
            synchronized (this.STATUS_LOCK) {
                if (this.isTerminating()) {
                    break;
                } else {
                    assert (this.isRunning());
                }
            }

            this.doWorkChunk();
        }

        assert (this.isTerminating());
        this.postWork();


        synchronized (this.STATUS_LOCK) {
            assert (this.isTerminating());
            this.m_status = TaskStatus.TERMINATED;
        }
        assert (this.isTerminated());
    }

    protected abstract void preWork();
    protected abstract void doWorkChunk();
    protected abstract void postWork();

    @Override
    public String toString(){
        return "Classname: " + this.getClass().getName() + ", ID: "+this.getID() + ", status: " + this.getTaskStatus().toString();
    }
}
