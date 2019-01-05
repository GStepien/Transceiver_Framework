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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.collections4.set.UnmodifiableSet;

import gs.utils.Strings;

public abstract class AbstractMultiTask<TASK_TYPE extends ConcurrentTask>
	extends AbstractConcurrentTask
	implements MultiTask<TASK_TYPE> {

	protected final UnmodifiableList<? extends TASK_TYPE> m_tasks;
    private final ExecutorService m_executorService;
    protected final UnmodifiableSet<Long> m_taskIDSet;
    protected final UnmodifiableMap<Long, TASK_TYPE> m_taskIDToTaskMap;

    @SuppressWarnings("unchecked")
    public AbstractMultiTask(Collection<? extends TASK_TYPE> tasks,
			ExecutorService executorService) {
		super();

		if(executorService == null){
            throw new NullPointerException();
        }

        this.m_executorService = executorService;
		
        if (tasks == null) {
            throw new NullPointerException();
        }
        if (tasks.size() == 0) {
            throw new IllegalArgumentException();
        }
        
        Map<Long, TASK_TYPE>  taskIDToTaskMap = new HashMap<>();
        Long ID;
        for (TASK_TYPE task : tasks) {
            if (task == null) {
                throw new NullPointerException();
            }            
            if(!task.isNotStarted()){
                throw new IllegalArgumentException("Task has already started.");
            }
            ID = task.getID();
            if (taskIDToTaskMap.containsKey(ID)) {
                throw new IllegalArgumentException("Non unique task IDs.");
            }
            taskIDToTaskMap.put(ID, task);
        }
        this.m_taskIDSet = (UnmodifiableSet<Long>) UnmodifiableSet.unmodifiableSet(taskIDToTaskMap.keySet());
        this.m_taskIDToTaskMap = (UnmodifiableMap<Long, TASK_TYPE>) UnmodifiableMap.unmodifiableMap(taskIDToTaskMap);
        this.m_tasks = (UnmodifiableList<? extends TASK_TYPE>) UnmodifiableList.unmodifiableList(new ArrayList<>(tasks));
              
        assert(this.m_taskIDSet.size() == tasks.size());
        assert(this.m_taskIDSet.size() > 0);
        assert(this.m_taskIDToTaskMap.keySet().containsAll(this.m_taskIDSet));
        assert(this.m_taskIDSet.containsAll(this.m_taskIDToTaskMap.keySet()));
        assert(this.checkTaskMap());
	}
	
	private boolean checkTaskMap(){
        for(Long key : this.m_taskIDToTaskMap.keySet()){
            if(key != this.m_taskIDToTaskMap.get(key).getID()){
                return false;
            }
        }
        return true;
    }
	
	@Override
    public final int getNumInternalTasks() {
		return this.m_tasks.size();
	}

    @Override
    public final TASK_TYPE getInternalTaskByID(long taskID) {
        if(!this.m_taskIDSet.contains(taskID)){
            throw new IndexOutOfBoundsException();
        }
        assert(this.m_taskIDToTaskMap.containsKey(taskID));
        return this.m_taskIDToTaskMap.get(taskID);
    }

    @Override
    public final TASK_TYPE getInternalTaskByIndex(int index) {
        if(index < 0 || index >= this.getNumInternalTasks()){
            throw new IndexOutOfBoundsException();
        }
        assert(this.m_taskIDSet.size() > index);
        return this.m_tasks.get(index);
    }

    @Override
    public final UnmodifiableSet<Long> getInternalTaskIDSet() {
        return this.m_taskIDSet;
    }
	
	@Override
	protected void preWork() {
		assert(this.isRunning());
		if(!this.hasAllConnections()){
			throw new RuntimeException(new MissingConnectionException());
		}
		
		// start all tasks and wait for them to leave "isNotStarted state"
		List<TASK_TYPE> nonStartedTasks = new ArrayList<>(this.m_tasks);

		assert(nonStartedTasks.size() == this.m_taskIDSet.size());
		assert(nonStartedTasks.size() > 0);

		for(TASK_TYPE task : nonStartedTasks){
			if(!task.isNotStarted()) {
				throw new IllegalStateException("Tasks managed by this instance should only be started and terminated by the same.");
			}
			this.m_executorService.submit(task);
		}

		TASK_TYPE task;
		Iterator<TASK_TYPE> it;
		while(nonStartedTasks.size() > 0){
			it = nonStartedTasks.iterator();
			while(it.hasNext()){
				task = it.next();
				if(!task.isNotStarted()){
					it.remove();
				}
			}
			Thread.yield();
		}
	}

	@Override
    protected final void doWorkChunk() {
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
		// terminate all tasks and wait for them to actually terminate
        assert(this.terminateCalledWithInterrupt() != null);
        List<TASK_TYPE> runningTasks = new ArrayList<>(this.m_tasks);
        for(TASK_TYPE task : runningTasks) {
            if(!task.isRunning()) {
            	throw new IllegalStateException("Tasks managed by this instance should only be started and terminated by the same.");
            }
            
            try {
                task.terminate(this.terminateCalledWithInterrupt());
            } catch (NotStartedException e) {
                throw new RuntimeException("This should not happen.", e);
            }
        }

        TASK_TYPE task;
        Iterator<TASK_TYPE> it;
        while(runningTasks.size() > 0){
            it = runningTasks.iterator();
            while(it.hasNext()){
            	task = it.next();
                if(task.isTerminated()){
                    it.remove();
                }
            }
            Thread.yield();
        }
	}
	

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(", containing:\n");
        String[] lines;
        for(int i = 0; i < this.getNumInternalTasks(); i++){
            lines = Strings.SPLIT_LINES_PATTERN.split(this.getInternalTaskByIndex(i).toString());
            for (String line : lines) {
                sb.append("\t").append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
