/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

import gs.utils.Strings;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.collections4.set.UnmodifiableSet;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ExecutorService;
// TODO DAG data structure with own consistency checks

// TODO DAG checks are (probably) not required anymore -> performed at ReceiverTask level now (adapt and test)
//      Check for single Root/Leaf still required though
public class GenericMultiTaskChain<
	ROOT_TASK_TYPE extends ConcurrentTask,
	ROOT_TYPE extends MultiTask<? extends ROOT_TASK_TYPE>,
	LEAF_TASK_TYPE extends ConcurrentTask,
	LEAF_TYPE extends MultiTask<? extends LEAF_TASK_TYPE>>
	extends
        AbstractConcurrentTask
	implements
        MultiTaskChain<ROOT_TASK_TYPE, ROOT_TYPE, LEAF_TASK_TYPE, LEAF_TYPE> {

    private final UnmodifiableMap<Long, UnmodifiableList<Long>> m_multiTaskIDConnectionDAG;
    private final UnmodifiableMap<Long, MultiTask<?>> m_multiTaskIDToMultiTaskMap;
    private final UnmodifiableSet<Long> m_multitaskIDSet;

    private final long m_rootMultiTaskID;
    private final ROOT_TYPE m_rootMultiTask;
    private final long m_leafMultiTaskID;
    private final LEAF_TYPE m_leafMultiTask;
    private final UnmodifiableList<ImmutablePair<Long, Long>> m_multiTaskIDConnectionsDepthFirst;
    private final UnmodifiableList<Long> m_multiTaskIDToposorted;
    private final ExecutorService m_executorService;


    private boolean checkMaps(){
        for(long key : this.m_multiTaskIDToMultiTaskMap.keySet()){
            if(key != this.m_multiTaskIDToMultiTaskMap.get(key).getID()){
                return false;
            }
        }

        long numConnections = 0;
        for(long parentID : this.m_multiTaskIDConnectionDAG.keySet()){
            if(!this.m_multitaskIDSet.contains(parentID)){
                return false;
            }
            for(long childID : this.m_multiTaskIDConnectionDAG.get(parentID)){
                if(!this.m_multitaskIDSet.contains(childID)){
                    return false;
                }
                numConnections++;
            }
        }
        return numConnections == this.m_multiTaskIDConnectionsDepthFirst.size();
    }

    private List<ImmutablePair<Long, Long>> traverseGraphDepthFirst(
    		long id, 
    		Set<Long> unvisitedNodes,
    		Map<Long, ? extends List<Long>> dag){
        List<ImmutablePair<Long, Long>> multiTaskIDConnectionsDepthFirst = new LinkedList<>();
        assert(!unvisitedNodes.contains(id));

        if(dag.containsKey(id)) {
            for (long childID : dag.get(id)) {
                unvisitedNodes.remove(childID);
                multiTaskIDConnectionsDepthFirst.add(new ImmutablePair<>(id, childID));

                multiTaskIDConnectionsDepthFirst.addAll(this.traverseGraphDepthFirst(childID, unvisitedNodes, dag));
            }
        }
        return multiTaskIDConnectionsDepthFirst;
    }

    @Override
    public final ExecutorService getExecutorService(){
        return this.m_executorService;
    }

    // Checks if DAG and returns topologically sorted list of DAG entries (or null, if not a DAG)
    private List<Long> checkIfDAG( Map<Long, ? extends List<Long>> dag, Set<Long> roots){
       // Compute child to parents map:
        Map<Long, List<Long>> childToParentsMap = new HashMap<>();
        Collection<Long> childIDs;
        for(long parentID : dag.keySet()){
            childIDs = dag.get(parentID);
            for(long childID : childIDs){
                if(!childToParentsMap.containsKey(childID)){
                    childToParentsMap.put(childID, new LinkedList<>());
                }
                childToParentsMap.get(childID).add(parentID);
            }
        }

        List<Long> multiTaskIDToposorted = new ArrayList<>(this.m_multitaskIDSet.size());
        Set<Long> currentRoots = new HashSet<>(roots);
        Set<Long> nextRoots = new HashSet<>(roots);
        while(currentRoots.size() > 0){
            for(long rootID : currentRoots){
                multiTaskIDToposorted.add(rootID);
                if(dag.containsKey(rootID)){
                    for(long childID : dag.get(rootID)){
                        assert(childToParentsMap.get(childID).contains(rootID));
                        childToParentsMap.get(childID).remove(rootID);
                        // Only one occurrence of rootID
                        assert(!childToParentsMap.get(childID).contains(rootID));
                        if(childToParentsMap.get(childID).size() == 0){
                            childToParentsMap.remove(childID);
                            nextRoots.add(childID);
                        }
                    }
                }
                nextRoots.remove(rootID);
            }
            currentRoots.clear();
            currentRoots.addAll(nextRoots);
        }
        if(childToParentsMap.size() == 0){
            return multiTaskIDToposorted;
        }
        else{
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean checkConnectivity(){
        MultiTask<?> multiTask;
        for(long id : this.m_multitaskIDSet){
            multiTask = this.m_multiTaskIDToMultiTaskMap.get(id);
            if(id != this.m_rootMultiTaskID && id != this.m_leafMultiTaskID){
                if(!((MultiTransceiverTask<?,?,?>)multiTask).hasAllInOutConnections()){
                    return false;
                }
            }
            else {
                if (id == this.m_rootMultiTaskID) {
                    if (id != this.m_leafMultiTaskID) {
                        assert (this.m_multiTaskIDConnectionDAG.containsKey(id));
                        if (!((MultiTransmitterTask<?,?>) multiTask).hasAllOutConnections()) {
                            return false;
                        }
                    } else {
                        assert (this.m_multiTaskIDConnectionDAG.size() == 0);
                        assert (this.m_multitaskIDSet.size() == 1);
                    }

                    if (multiTask instanceof MultiReceiverTask) {
                        if (((MultiReceiverTask<?,?>) multiTask).getNumInConnections() != 0) {
                            return false;
                        }
                    }
                }
                if(id == this.m_leafMultiTaskID){
                    if(id != this.m_rootMultiTaskID) {
                        if (!((MultiReceiverTask<?,?>) multiTask).hasAllInConnections()) {
                            return false;
                        }
                    }
                    if(multiTask instanceof MultiTransmitterTask){
                        if(((MultiTransmitterTask<?,?>)multiTask).getNumOutConnections() != 0){
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    protected final Map<Long, ? extends Collection<Long>> asIDDag(
            Map<? extends MultiTask<?>, ? extends Collection<? extends MultiTask<?>>> multiTaskConnectionDAG,
            Collection<? extends MultiTask<?>> multiTasks){
        if(multiTaskConnectionDAG == null){
            throw new NullPointerException();
        }

        Map<Long, List<Long>> multiTaskIDConnectionDAG = new HashMap<>();
        List<Long> idList;
        Collection<? extends MultiTask<?>> mappedToTasks;
        Set<Long> taskIDs;
        for(MultiTask<?> multiTask : multiTaskConnectionDAG.keySet()){
            if(multiTask == null){
                throw new NullPointerException();
            }
            else if(!multiTasks.contains(multiTask)){
                throw new IllegalArgumentException("Unknown multi task.");
            }
            else if(multiTaskIDConnectionDAG.keySet().contains(multiTask.getID())){
                throw new IllegalArgumentException("ID key encountered twice.");
            }
            mappedToTasks = multiTaskConnectionDAG.get(multiTask);

            if(mappedToTasks == null){
                throw new NullPointerException();
            }
            else if (mappedToTasks.size() == 0){
                continue;
            }

            idList = new ArrayList<>(mappedToTasks.size());
            taskIDs = new HashSet<>();
            for(MultiTask<?> mappedToTask : mappedToTasks){
                if(!multiTasks.contains(mappedToTask)){
                    throw new IllegalArgumentException("Unknown multi task.");
                }
                else if(taskIDs.contains(mappedToTask.getID())){
                    throw new IllegalArgumentException("Target list contains same ID twice.");
                }
                if(mappedToTask.getID() == multiTask.getID()){
                    throw new IllegalArgumentException("ID maps to itself.");
                }
                taskIDs.add(mappedToTask.getID());
                idList.add(mappedToTask.getID());
            }
            multiTaskIDConnectionDAG.put(multiTask.getID(), idList);
        }

        return multiTaskIDConnectionDAG;
    }
    
    @SuppressWarnings("unchecked")
    public GenericMultiTaskChain(Collection<? extends MultiTask<?>> multiTasks,
                                 // DAG: Directed acyclic graph
                                 // Multitasks are connected in the order of edges traversed during a depth first search
    							 //   and, if the Collection mapped to a key is ordered, subtrees are traversed in that order.
                                 // Multitasks are started and terminated in the order of a topological sorting of the nodes in this DAG
    							 //   again, if Collection mapped to a key is ordered, node children are traversed in that order
                                 Map<? extends MultiTask<?>, ? extends Collection<? extends MultiTask<?>>> multiTaskConnectionDAG,
                                 ExecutorService executorService) throws UnacceptedConcurrentTaskException {

        if(multiTasks == null || multiTaskConnectionDAG == null || executorService == null){
            throw new NullPointerException();
        }

        if(multiTasks.size() == 0){
            throw new IllegalArgumentException();
        }

        Map<Long, ? extends Collection<Long>> multiTaskIDConnectionDAG = this.asIDDag(multiTaskConnectionDAG, multiTasks);

        this.m_executorService = executorService;

        Set<Long> multiTaskIDSet = new HashSet<>();
        Map<Long, MultiTask<?>> multiTaskIDToMultiTaskMap = new HashMap<>();
        long currentId;
        for(MultiTask<?> multiTask : multiTasks) {
            if (multiTask == null) {
                throw new NullPointerException();
            }
            currentId = multiTask.getID();
            if (multiTaskIDSet.contains(currentId)) {
                throw new IllegalArgumentException("Non unique multi task IDs.");
            }
            if(!multiTask.isNotStarted()){
                throw new IllegalArgumentException("Multi task has already started.");
            }
            if(multiTask instanceof MultiTransmitterTask) {
                if (((MultiTransmitterTask<?,?>) multiTask).getNumOutConnections() != 0) {
                    throw new IllegalArgumentException("Multi transmitter task already has out connections.");
                }
            }
            else if(multiTask instanceof MultiReceiverTask){
                if(((MultiReceiverTask<?,?>)multiTask).getNumInConnections() != 0){
                    throw new IllegalArgumentException("Multi receiver task already has in connections.");
                }
            }
            else{
                throw new IllegalArgumentException("Provided MultiTask instances must extend MultiReceiverTask " +
                        "and/or MultiTransmitterTask.");
            }


            multiTaskIDSet.add(currentId);
            multiTaskIDToMultiTaskMap.put(currentId, multiTask);
        }
        this.m_multitaskIDSet = (UnmodifiableSet<Long>) UnmodifiableSet.unmodifiableSet(multiTaskIDSet);
        this.m_multiTaskIDToMultiTaskMap = (UnmodifiableMap<Long, MultiTask<?>>) UnmodifiableMap.unmodifiableMap(multiTaskIDToMultiTaskMap);

        // Process DAG
        Map<Long, UnmodifiableList<Long>> unmodDAG = new HashMap<>();
        Set<Long> leafIDs = new HashSet<>(this.m_multitaskIDSet);
        Set<Long> rootIDs = new HashSet<>(this.m_multitaskIDSet);
        for(long key : multiTaskIDConnectionDAG.keySet()){
            Collection<Long> childKeys = multiTaskIDConnectionDAG.get(key);
            if(childKeys.size() > 0){
                leafIDs.remove(key); // Key with children cannot be leaf
                rootIDs.removeAll(childKeys); // Children of another node cannot be root
                unmodDAG.put(key, (UnmodifiableList<Long>) UnmodifiableList.unmodifiableList(new ArrayList<>(childKeys)));
            }
        }
        if(leafIDs.size() != 1 || rootIDs.size() != 1){
            throw new IllegalArgumentException("Provided graph must have exactly one root and one leaf.");
        }

        Set<Long> rootNonLeafIDs = new HashSet<>(rootIDs);
        rootNonLeafIDs.removeAll(leafIDs);
        assert(unmodDAG.keySet().containsAll(rootNonLeafIDs));
        for(long rootNonLeafID : rootNonLeafIDs){
            if(!(this.m_multiTaskIDToMultiTaskMap.get(rootNonLeafID) instanceof MultiTransmitterTask)){
                throw new IllegalArgumentException("Multitask with children and no parent must implement MultiTransmitterTask.");
            }
        }
        Set<Long> leafNonRootIDs = new HashSet<>(leafIDs);
        leafNonRootIDs.removeAll(rootIDs);
        for(long leafNonRootID : leafNonRootIDs){
            if(!(this.m_multiTaskIDToMultiTaskMap.get(leafNonRootID) instanceof MultiReceiverTask)){
                throw new IllegalArgumentException("Multitask with parent and without children must implement MultiReceiverTask.");
            }
        }
        Set<Long> inbetweenIDs = new HashSet<>(this.m_multitaskIDSet);
        inbetweenIDs.removeAll(rootIDs);
        inbetweenIDs.removeAll(leafIDs);
        for(long inbetweenID : inbetweenIDs){
            if(!(this.m_multiTaskIDToMultiTaskMap.get(inbetweenID) instanceof MultiTransceiverTask)){
                throw new IllegalArgumentException("Multitask with parent and children must implement MultiTransceiverTask.");
            }
        }

        // Check if all IDs reachable from roots
        Set<Long> unvisitedNodes = new HashSet<>(this.m_multitaskIDSet);
        unvisitedNodes.removeAll(rootIDs);
        assert(rootIDs.size() == 1);
        List<ImmutablePair<Long, Long>> multiTaskIDConnectionsDepthFirst =
                this.traverseGraphDepthFirst(rootIDs.iterator().next(), unvisitedNodes, unmodDAG);
        this.m_multiTaskIDConnectionsDepthFirst = 
        		(UnmodifiableList<ImmutablePair<Long, Long>>) UnmodifiableList.unmodifiableList(
        				multiTaskIDConnectionsDepthFirst);

        if(unvisitedNodes.size() > 0){
            throw new IllegalArgumentException("Graph contains unreachable  IDs.");
        }

        List<Long> multiTaskIDToposorted = this.checkIfDAG(unmodDAG, rootIDs);

        if(multiTaskIDToposorted == null){
            throw new IllegalArgumentException("Provided graph is not a DAG.");
        }
        this.m_multiTaskIDToposorted = (UnmodifiableList<Long>) UnmodifiableList.unmodifiableList(multiTaskIDToposorted);

        this.m_leafMultiTaskID = leafIDs.iterator().next();
        this.m_rootMultiTaskID = rootIDs.iterator().next();
                
        try {
        	this.m_leafMultiTask = (LEAF_TYPE)this.m_multiTaskIDToMultiTaskMap.get(this.m_leafMultiTaskID);
        	this.m_rootMultiTask = (ROOT_TYPE)this.m_multiTaskIDToMultiTaskMap.get(this.m_rootMultiTaskID);
        }
        catch(ClassCastException e) {
        	throw new IllegalArgumentException("Provided root and/or leaf does not match type parameter.");
        }
        
        this.m_multiTaskIDConnectionDAG = (UnmodifiableMap<Long, UnmodifiableList<Long>>) UnmodifiableMap.unmodifiableMap(unmodDAG);

        assert(this.m_multitaskIDSet.size() == multiTasks.size());
        assert(this.m_multiTaskIDToMultiTaskMap.keySet().containsAll(this.m_multitaskIDSet));
        assert(this.m_multitaskIDSet.containsAll(this.m_multiTaskIDToMultiTaskMap.keySet()));
        assert(this.m_multitaskIDSet.containsAll(this.m_multiTaskIDConnectionDAG.keySet()));
        assert(this.m_multitaskIDSet.contains(this.m_leafMultiTaskID));
        assert(this.m_multitaskIDSet.contains(this.m_rootMultiTaskID));
        assert(this.m_multitaskIDSet.containsAll(this.m_multiTaskIDToposorted));
        assert(this.m_multiTaskIDToposorted.containsAll(this.m_multitaskIDSet));
        assert(this.checkMaps());

        MultiTransmitterTask<?,?> transmitter;
        MultiReceiverTask<?,?> receiver;

        for(Pair<Long, Long> connectIDs : this.m_multiTaskIDConnectionsDepthFirst){ // Connect in DFS order
            // Should be the case at this point
            assert(this.m_multiTaskIDToMultiTaskMap.get(connectIDs.getLeft()) instanceof MultiTransmitterTask);
            assert(this.m_multiTaskIDToMultiTaskMap.get(connectIDs.getRight()) instanceof MultiReceiverTask);

            transmitter = (MultiTransmitterTask<?,?>) this.m_multiTaskIDToMultiTaskMap.get(connectIDs.getLeft());
            receiver = (MultiReceiverTask<?,?>) this.m_multiTaskIDToMultiTaskMap.get(connectIDs.getRight());

            this.connect(transmitter, receiver);

        }

        assert(this.checkConnectivity());
    }

    @SuppressWarnings("unchecked")
    private<DATA_OUT_TYPE, DATA_IN_TYPE> void connect(MultiTransmitterTask<DATA_OUT_TYPE, ?> transmitter,
                                                               MultiReceiverTask<DATA_IN_TYPE, ?> receiver) throws UnacceptedConcurrentTaskException {
        try {
            transmitter.addOutConnection((MultiReceiverTask<? super DATA_OUT_TYPE, ?>) receiver);
        }
        catch(ClassCastException e){
            throw new IllegalArgumentException("Incompatible multi task connection.");
        }
    }

    @Override
    public final long getRootMultiTaskID(){
        return this.m_rootMultiTaskID;
    }

    @Override
    public ROOT_TYPE getRootMultiTask() {
    	assert(this.m_multiTaskIDToMultiTaskMap.get(this.m_rootMultiTaskID) == this.m_rootMultiTask);    	
        return this.m_rootMultiTask;
    }

    @Override
    public final long getLeafMultiTaskID(){
        return this.m_leafMultiTaskID;
    }

    @Override
    public LEAF_TYPE getLeafMultiTask() {
    	assert(this.m_multiTaskIDToMultiTaskMap.get(this.m_leafMultiTaskID) == this.m_leafMultiTask);
        return this.m_leafMultiTask;
    }

    @Override
    public final MultiTask<?> getMultiTaskByID(long multiTaskID) {
        if(!this.m_multitaskIDSet.contains(multiTaskID)){
            throw new IndexOutOfBoundsException();
        }
        assert(this.m_multiTaskIDToMultiTaskMap.containsKey(multiTaskID));
        return this.m_multiTaskIDToMultiTaskMap.get(multiTaskID);
    }

    @Override
    public final UnmodifiableSet<Long> getMultiTaskIDs(){
        return this.m_multitaskIDSet;
    }

    @Override
    public final UnmodifiableMap<Long, UnmodifiableList<Long>> getMultiTaskIDConnectionDAG() {
        return this.m_multiTaskIDConnectionDAG;
    }

    @Override
    protected final void preWork() {
        assert(this.isRunning());

        assert(this.m_multiTaskIDToposorted.size() > 0);

        // Start in toposort order
        MultiTask<?> multiTask;
        for(long multiTaskID : this.m_multiTaskIDToposorted){
            multiTask = this.m_multiTaskIDToMultiTaskMap.get(multiTaskID);
            assert(multiTask.isNotStarted());
            this.m_executorService.submit(multiTask);

            while(multiTask.isNotStarted()){
                Thread.yield();
            }
        }
    }

    @Override
    protected void doWorkChunk() {
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
    protected final void postWork() {
        // Terminate in toposort order
        MultiTask<?> multiTask;
        for(long multiTaskID : this.m_multiTaskIDToposorted){
            multiTask = this.m_multiTaskIDToMultiTaskMap.get(multiTaskID);
            assert(multiTask.isRunning());
            try {
                assert(this.terminateCalledWithInterrupt() != null);
                multiTask.terminate(this.terminateCalledWithInterrupt());
            } catch (NotStartedException e) {
                throw new IllegalStateException("This should not happen.", e);
            }

            while(!multiTask.isTerminated()){
                Thread.yield();
            }
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(", containing (in topological order):\n");
        String[] multiTaskLines;
        for(int i = 0; i < this.m_multiTaskIDToposorted.size(); i++){
            multiTaskLines = Strings.SPLIT_LINES_PATTERN.split(this.getMultiTaskByID(this.m_multiTaskIDToposorted.get(i)).toString());
            for (String multiTaskLine : multiTaskLines) {
                sb.append("\t").append(multiTaskLine).append("\n");
            }
        }
        return sb.toString();
    }
}
