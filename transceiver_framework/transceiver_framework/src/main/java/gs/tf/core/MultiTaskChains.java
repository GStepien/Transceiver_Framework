/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.tf.core;

/*
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ExecutorService;
*/
public class MultiTaskChains {
	// TODO: Adapt code to refactored MultiTask & MultiTaskChain typing
    // TODO: Test these methods
    // TODO: Move these methods to Generic implementation -> allows chaining of connect expressions

    // Only static utility functions
 /*   private MultiTaskChains(){}

    private static Map<MultiTask, List<MultiTask>> createSimpleDAG(MultiTask from, MultiTask to){
        Map<MultiTask, List<MultiTask>> result = new HashMap<>();
        result.put(from, new LinkedList<>());
        result.get(from).add(to);
        return result;
    }

    private static Pair<List<MultiTask>, Map<MultiTask, List<MultiTask>>> createParams(MultiTask first, MultiTask second){
        if(first == null || second == null){
            throw new NullPointerException();
        }

        long firstID = first.getID();
        long secondID = second.getID();

        if(firstID == secondID){
            throw new IllegalStateException();
        }

        Map<MultiTask, List<MultiTask>> connectionDAG = createSimpleDAG(first, second);
        List<MultiTask> multiTaskList = new ArrayList<>(2);
        multiTaskList.add(first);
        multiTaskList.add(second);

        return new ImmutablePair<>(multiTaskList, connectionDAG);
    }

    public static<DATA_IN_TYPE, DATA_INTERMEDIATE_TYPE> MultiReceiverTask<DATA_IN_TYPE> connect(
            MultiTransceiverTask<DATA_IN_TYPE, DATA_INTERMEDIATE_TYPE> first,
            MultiReceiverTask<? super DATA_INTERMEDIATE_TYPE> second,
            ExecutorService executorService) throws UnacceptedConcurrentTaskException {

        if(executorService == null){
            throw new NullPointerException();
        }

        Pair<List<MultiTask>, Map<MultiTask, List<MultiTask>>> params = createParams(first, second);

        return new GenericMultiReceiverTaskChain<>(params.getLeft(), params.getRight(), executorService);
    }

    public static<DATA_INTERMEDIATE_TYPE, DATA_OUT_TYPE> MultiTransmitterTask<DATA_OUT_TYPE> connect(
            MultiTransmitterTask<DATA_INTERMEDIATE_TYPE> first,
            MultiTransceiverTask<? super DATA_INTERMEDIATE_TYPE, DATA_OUT_TYPE> second,
            ExecutorService executorService) throws UnacceptedConcurrentTaskException {

        if(executorService == null){
            throw new NullPointerException();
        }


        Pair<List<MultiTask>, Map<MultiTask, List<MultiTask>>> params = createParams(first, second);

        return new GenericMultiTransmitterTaskChain<>(params.getLeft(), params.getRight(), executorService);
    }

    public static<DATA_IN_TYPE, DATA_INTERMEDIATE_TYPE, DATA_OUT_TYPE> MultiTransceiverTask<DATA_IN_TYPE, DATA_OUT_TYPE> connect(
            MultiTransceiverTask<DATA_IN_TYPE, DATA_INTERMEDIATE_TYPE> first,
            MultiTransceiverTask<? super DATA_INTERMEDIATE_TYPE, DATA_OUT_TYPE> second,
            ExecutorService executorService) throws UnacceptedConcurrentTaskException {

        if(executorService == null){
            throw new NullPointerException();
        }

        Pair<List<MultiTask>, Map<MultiTask, List<MultiTask>>> params = createParams(first, second);

        return new GenericMultiTransceiverTaskChain<>(params.getLeft(), params.getRight(), executorService);
    }

    public static<DATA_INTERMEDIATE_TYPE> ClosedMultiTaskChain connect(
            MultiTransmitterTask<DATA_INTERMEDIATE_TYPE> first,
            MultiReceiverTask<? super DATA_INTERMEDIATE_TYPE> second,
            ExecutorService executorService) throws UnacceptedConcurrentTaskException {

        if(executorService == null){
            throw new NullPointerException();
        }

        Pair<List<MultiTask>, Map<MultiTask, List<MultiTask>>> params = createParams(first, second);

        return new GenericClosedMultiTaskChain(params.getLeft(), params.getRight(), executorService);
    }

    public static MultiTaskChain connect(
            MultiTask first,
            MultiTask second,
            ExecutorService executorService) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }*/
}
