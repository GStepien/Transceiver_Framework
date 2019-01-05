/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

public interface ConcurrentTask extends Runnable {

    long getID();

    TaskStatus getTaskStatus();
    boolean isRunning();
    boolean isNotStarted();
    boolean isTerminating();
    boolean isTerminated();

    // Returns false if and only if current state is TERMINATING or TERMINATED
    // and true if current state is RUNNING.
    // In every other case an NotStartedException is thrown.
    // Note: Also raises interrupt flag of running thread
//    default public boolean terminate() throws NotStartedException{
//        return this.terminate(false);
//    }

    boolean terminate(boolean interrupt) throws NotStartedException;

    enum TaskStatus {

        RUNNING, NOT_STARTED, TERMINATING, TERMINATED

    }
}
