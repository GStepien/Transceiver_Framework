/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import gs.tf.core.ConcurrentTask;
import org.apache.commons.collections4.set.UnmodifiableSet;

public interface MultiTask<TASK_TYPE extends ConcurrentTask> extends ConcurrentTask {
	
	boolean hasAllConnections();
	int getNumInternalTasks();

    TASK_TYPE getInternalTaskByID(long taskID);
    TASK_TYPE getInternalTaskByIndex(int index);
    UnmodifiableSet<Long> getInternalTaskIDSet();
}
