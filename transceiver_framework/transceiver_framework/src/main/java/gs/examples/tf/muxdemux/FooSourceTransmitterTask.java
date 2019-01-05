/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.examples.tf.muxdemux;

import gs.tf.core.GenericDemuxMultiTransmitterTask;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

public class FooSourceTransmitterTask
        extends GenericDemuxMultiTransmitterTask.AbstractSourceTransmitterTask<
            Triple<Long, Integer, Integer>> {

    private static final Logger LOGGER = Logger.getLogger(FooSourceTransmitterTask.class.getName());

    private int dataID = 0;

    public FooSourceTransmitterTask(int maxDataChunkSize,
                                    int numDemuxTransmitters) {
        super(maxDataChunkSize, numDemuxTransmitters);
    }

    @Override
    protected Collection<Triple<Long, Integer, Integer>> getNextDataChunkForDemux(int demuxIndex) {
        LOGGER.info("Source transmitter (ID: " + this.getID() + ")" +
                " creates a data element with ID " + this.dataID +
                " to be forwarded by the demultiplexer's DemuxTransmitterTask instance " +
                "of index "+demuxIndex+".");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Do nothing
        }

        return Collections.singleton(Triple.of(this.getID(), demuxIndex, this.dataID++));
    }
}