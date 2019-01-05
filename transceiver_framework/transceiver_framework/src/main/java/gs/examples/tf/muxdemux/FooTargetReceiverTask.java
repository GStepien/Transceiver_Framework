/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.examples.tf.muxdemux;

import gs.tf.core.GenericMuxMultiReceiverTask;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class FooTargetReceiverTask
        extends GenericMuxMultiReceiverTask.AbstractTargetReceiverTask<
            Pair<Long, Triple<Long, Integer, Integer>>> {

    private static final Logger LOGGER = Logger.getLogger(FooTargetReceiverTask.class.getName());
    private final String outputPath;
    private BufferedWriter outputWriter;

    public FooTargetReceiverTask(Integer inDataQueueCapacity,
                             Long timeoutInterval,
                             int numMuxTransmitters,
                             String outputPath) {
        super(inDataQueueCapacity, timeoutInterval, numMuxTransmitters);

        this.outputPath = outputPath;
    }

    @Override
    protected void preWork(){
        // IMPORTANT: Always call super.preWork() when overriding this method!
        super.preWork();

        File directory = new File(Paths.get(this.outputPath).getParent().toString());
        if(!directory.exists()){
            if(!directory.mkdirs()){
                throw new RuntimeException(new IOException("Could not create output directory \"" +
                        directory + "\"."));
            }
        }

        try {
            this.outputWriter = new BufferedWriter(new FileWriter(this.outputPath, false));
            this.outputWriter.write(
                    "Data ID:, " +
                            "Source transmitter ID:, " +
                            "DemuxTransmitterTask index:, " +
                            "Transceiver ID:, " +
                            "MuxReceiverTask index:, " +
                            "Target receiver ID:");
        } catch (IOException e) {
            throw new RuntimeException("Cannot create writer instance for file \"" + this.outputPath + "\"", e);
        }
    }

    @Override
    protected void processDataElementFromMux(int muxIndex,
                                             Pair<Long, Triple<Long, Integer, Integer>> dataElement) {
        Triple<Long, Integer, Integer> payload = dataElement.getRight();

        String output =
                payload.getRight() + ", " +
                        payload.getLeft() + ", " +
                        payload.getMiddle() + ", " +
                        dataElement.getLeft() + ", " +
                        muxIndex + ", " +
                        this.getID();

        LOGGER.info("Target receiver (ID: " + this.getID() + ")" +
                " processes data element by storing the following " +
                "information in a csv file: " + this.outputPath + System.lineSeparator() +
                "\tData element with ID " + payload.getRight() +
                " traveled through transceiver network via (not including the demultiplexer's internal " +
                "DemuxReceiverTask instance and the multiplexer's internal SequentialMuxTransmitterTask " +
                "instance):" + System.lineSeparator() +
                "\t\tSource transmitter " + payload.getLeft() +
                " -> DemuxTransmitterTask of index " + payload.getMiddle() +
                " -> transceiver " + dataElement.getLeft() +
                " -> MuxReceiverTask of index " + muxIndex +
                " -> target receiver " + this.getID());

        try {
            this.outputWriter.newLine();
            this.outputWriter.write(output);
        } catch (IOException e) {
            throw new RuntimeException("Error during writing.", e);
        }
    }

    @Override
    protected void postWork(){
        // IMPORTANT: Always call super.postWork() when overriding this method!
        super.postWork();

        try {
            this.outputWriter.close();
        } catch (IOException e) {
            throw new RuntimeException("Could not close writer.", e);
        }
    }
}
