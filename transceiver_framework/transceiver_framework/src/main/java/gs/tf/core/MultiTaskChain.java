/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.collections4.set.UnmodifiableSet;

import java.util.concurrent.ExecutorService;

public interface MultiTaskChain<
	ROOT_TASK_TYPE extends ConcurrentTask,
	ROOT_TYPE extends MultiTask<? extends ROOT_TASK_TYPE>,
	LEAF_TASK_TYPE extends ConcurrentTask,
	LEAF_TYPE extends MultiTask<? extends LEAF_TASK_TYPE>>
	extends 
		ConcurrentTask {
									
    MultiTask<?> getMultiTaskByID(long multiTaskID);
    UnmodifiableMap<Long, UnmodifiableList<Long>> getMultiTaskIDConnectionDAG();
    long getRootMultiTaskID();
    ROOT_TYPE getRootMultiTask();
    long getLeafMultiTaskID();
    LEAF_TYPE getLeafMultiTask();
    UnmodifiableSet<Long> getMultiTaskIDs();

    ExecutorService getExecutorService();
}
