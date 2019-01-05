/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;

import java.util.*;

public class Structure {

    private Structure(){}

    private static boolean cyclesReachableFromNode(Object node,
                                                   Set<Object> noCycleNodes,
                                                   Set<Object> visitedNodes,
                                                   Map<?, ? extends Collection<?>> dependencyGraph){

        boolean result;
        if(noCycleNodes.contains(node) || dependencyGraph.get(node) == null ||
                dependencyGraph.get(node).size() == 0){ // leaf node?
            noCycleNodes.add(node);
            visitedNodes.add(node);
            result = false;
        }
        else if(visitedNodes.contains(node)){ // Means we re-visit a higher node (w.r.t. level of "node") in the supposed "tree" -> cylce!
            result = true;
        }
        else{
            visitedNodes.add(node);
            result = false;
            for(Object childNode : dependencyGraph.get(node)){
                if(cyclesReachableFromNode(childNode, noCycleNodes, visitedNodes, dependencyGraph)){
                    result = true;
                    break;
                }
            }
            if(!result){
                noCycleNodes.add(node);
            }
        }

        return result;
    }

    public static boolean hasCycles(Map<?, ? extends Collection<?>> dependencyGraph){
        if(dependencyGraph == null){
            throw new NullPointerException();
        }
        // Nodes in this set are guaranteed that no cycle is reachable from them
        Set<Object> noCycleNodes = new HashSet<>();

        for(Object node : dependencyGraph.keySet()){
            if(cyclesReachableFromNode(node, noCycleNodes,  new HashSet<>(), dependencyGraph)){
                return true;
            }
        }

        return false;
    }

    // Note: partitioning may contain multiple empty collections and still be considered a partitioning
    public static boolean isPartitioning(
    		Collection<? extends Collection<?>> partitioning, 
    				Collection<?> allElements){
        if(partitioning == null || allElements == null){
            throw new NullPointerException();
        }

        Set<Object> allElementsSet = new HashSet<>(allElements);
        Set<Object> encounteredElements = new HashSet<>();
        Set<?> partitionSet;
        for(Collection<?> partition : partitioning){
            if(partition == null){
                throw new NullPointerException();
            }
            partitionSet = new HashSet<>(partition);
            if(!allElements.containsAll(partitionSet) || !Collections.disjoint(encounteredElements, partitionSet)){
                return false;
            }
            else{
                encounteredElements.addAll(partitionSet);
            }
        }

        return allElementsSet.equals(encounteredElements);
    }
    
    public static boolean setEquals(Collection<?> collection1, Collection<?> collection2) {
    	if(collection1 == null || collection2 == null) {
    		throw new NullPointerException();
    	}
    	else {
    		return collection1.size() == collection2.size() && 
    				collection1.containsAll(collection2);
    	}
    }

}
