/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.examples.tf.muxdemux;

import gs.tf.core.AbstractTransceiverTask;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class FooTransceiverTask
        extends AbstractTransceiverTask<
            Triple<Long, Integer, Integer>,
            Pair<Long, Triple<Long, Integer, Integer>>>{

    private static final Logger LOGGER = Logger.getLogger(FooTransceiverTask.class.getName());

    /**
     * IMPORTANT: 'blockingQueue' must be a thread safe structure!
     *            In general, {@link #processDataElement(Triple)}
     *            and {@link #getNextDataChunk()} are not executed by the same thread.
     *
     * @see AbstractTransceiverTask
     */
    private final BlockingQueue<Pair<Long, Triple<Long, Integer, Integer>>> blockingQueue;

    public FooTransceiverTask(Integer inDataQueueCapacity,
                              Long timeoutInterval,
                              int maxDataChunkSize,
                              ExecutorService executorService) {
        super(inDataQueueCapacity, timeoutInterval, maxDataChunkSize, executorService);

        if(inDataQueueCapacity == null){
            this.blockingQueue = new LinkedBlockingQueue<>();
        }
        else{
            this.blockingQueue = new ArrayBlockingQueue<>(inDataQueueCapacity);
        }
    }

    @Override
    protected void processDataElement(Triple<Long, Integer, Integer> dataElement) throws InterruptedException {
        Pair<Long, Triple<Long, Integer, Integer>> result = Pair.of(this.getID(), dataElement);

        LOGGER.info("Annotating transceiver (ID: " + this.getID() + ")" +
                " processes data element with ID " + dataElement.getRight() +
                " by appending its own ID and storing the result for " +
                "forwarding.");

        this.blockingQueue.put(result);
    }

    @Override
    protected Collection<Pair<Long, Triple<Long, Integer, Integer>>> getNextDataChunk() {
        List<Pair<Long, Triple<Long, Integer, Integer>>> result = new LinkedList<>();

        // IMPORTANT: 'result.size()' may never exceed the maximal data chunk size!
        this.blockingQueue.drainTo(result, this.getMaxDataChunkSize());

        if(result.size() > 0) {
            LOGGER.info("Transceiver (ID: " + this.getID() + ")" +
                    " creates data chunk of size " + result.size() + ".");
        }
        return result; // Returning 'null' in case of 'result.size() == 0' would also be ok.
    }
}
