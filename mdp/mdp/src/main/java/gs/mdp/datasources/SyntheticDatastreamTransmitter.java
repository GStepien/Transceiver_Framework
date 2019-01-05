/** 
 * MDP: A motif detector and predictor.
 *
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * 
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */

package gs.mdp.datasources;

import gs.utils.datatypes.LabeledTimestampedData;
import gs.utils.datatypes.GenericDoubleData;
import gs.utils.datatypes.GenericStringData;
import gs.tf.core.GenericDemuxMultiTransmitterTask;
import gs.utils.r.RException;
import gs.mdp.utils.r.SimpleConcurrentR;
import gs.utils.r.SimpleRInterface;
import org.apache.commons.collections4.set.UnmodifiableSet;

import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

// TODO Create own object SyntheticDatastream as interface between m_rInterface and this object to use here
public class SyntheticDatastreamTransmitter extends
        GenericDemuxMultiTransmitterTask.AbstractSourceTransmitterTask<LabeledTimestampedData<?>> {

    private static final Logger LOGGER = Logger.getLogger(SyntheticDatastreamTransmitter.class.getName());

    private final SimpleRInterface m_rInterface;
    private final UnmodifiableSet<Integer> m_numericStreamIndices;

    public SyntheticDatastreamTransmitter(int maxDataChunkSize,
                                          int numDemuxTransmitters,
                                          int port,
                                          String rDriverPath,
                                          String streamJSONPath) throws RException {
        super(maxDataChunkSize, numDemuxTransmitters);

        if(rDriverPath == null || streamJSONPath == null){
            throw new NullPointerException();
        }

        this.m_rInterface = new SimpleConcurrentR(port);

        if(!this.m_rInterface.evalBoolean("length(ls()) == 0")) {
            throw new IllegalStateException("The R environment of the associated RServe instance is not empty.");
        }
        this.m_rInterface.evalVoid("source(\""+
                Paths.get(rDriverPath).toAbsolutePath()+"\")");
        this.m_rInterface.evalVoid("driver <- get_driver_env()");
        this.m_rInterface.evalVoid("synth_ds <- driver$create_synth_ds_its_from_json(json_file = \""+
                Paths.get(streamJSONPath).toAbsolutePath()+"\")");

        int lmNumStates = this.m_rInterface.evalInt("driver$get_last_language_model()$get_num_states()");
        int lmNumTransitions = this.m_rInterface.evalInt("driver$get_last_language_model()$get_total_num_transitions()");
        assert(lmNumStates > 0);
        double[] stats = this.m_rInterface.evalDoubles("driver$get_last_language_model()$get_raw_stats()");
        if(stats == null){
            throw new NullPointerException();
        }
        if(stats.length != 3){
            throw new IllegalStateException("Unexpected number of language model statistics.");
        }
        LOGGER.config("Created R synthetic data stream.\n" +
                "  Language model state number: " + lmNumStates + "\n" +
                "  Language model transition number: " + lmNumTransitions + "\n" +
                "  Language model min error: " + stats[0] + "\n" +
                "  Language model entropy rate: " + stats[1] + "\n" +
                "  Language model perplexity rate: "+ stats[2]);
        this.m_rInterface.evalVoid("vocabulary <- driver$get_last_vocabulary()");
        LOGGER.config("Data stream language model vocabulary:\n"+
                this.m_rInterface.evalString("paste0(" +
                        "lapply(1:vocabulary$get_num_word_ids(), FUN = function(wordID) {" +
                        "   return(paste0(\"\\t\", wordID, \" -> \" , vocabulary$get_word(wordID), \", " +
                        "Dim: \", toString(vocabulary$get_id_dims(wordID)) ,\"\\n\"))" +
                        "})" +
                        ",collapse=\"\")"));

        int[] numInd = this.m_rInterface.evalInts("synth_ds$get_numeric_indices()");
        Set<Integer> numericStreamIndicesSet = new HashSet<>();
        for(int ind : numInd){
            if(ind < 1 || ind >= numDemuxTransmitters + 1){ // R indices start at 1!
                throw new IllegalStateException();
            }
            numericStreamIndicesSet.add(ind - 1);
        }
        this.m_numericStreamIndices = (UnmodifiableSet<Integer>) UnmodifiableSet.unmodifiableSet(numericStreamIndicesSet);

    }

    @Override
    protected Collection<LabeledTimestampedData<?>> getNextDataChunkForDemux(int demuxIndex) {
        try {
            int numRetrieve = Math.min(this.getMaxDataChunkSize(),
                    this.m_rInterface.evalInt("synth_ds$get_next_count(" + (demuxIndex + 1) + ")")); // R indices start at 1!
            this.m_rInterface.evalVoid("data <- synth_ds$get_next(" + (demuxIndex + 1) + ", " + numRetrieve + ")");

            int timestamps[] = this.m_rInterface.evalInts("data[,2]");
            String labels[] = this.m_rInterface.evalStrings("data[,1]");
            double[] dataNum = null;
            String[] dataString = null;

            boolean isNum = this.m_numericStreamIndices.contains(demuxIndex);

            if (isNum) {
                dataNum = this.m_rInterface.evalDoubles("data[,3]");
            } else {
                dataString = this.m_rInterface.evalStrings("data[,3]");
            }

            if (timestamps.length != numRetrieve ||
                    labels.length != numRetrieve ||
                    isNum && dataNum.length != numRetrieve ||
                    !isNum && dataString.length != numRetrieve) {
                throw new IllegalStateException("Inconsistent number of data elements returned");
            }

            List<LabeledTimestampedData<?>> result = new ArrayList<>(numRetrieve);
            if (isNum) {
                for (int i = 0; i < numRetrieve; i++) {
                    result.add(new GenericDoubleData(timestamps[i], labels[i], dataNum[i]));
                }
            } else {
                for (int i = 0; i < numRetrieve; i++) {
                    result.add(new GenericStringData(timestamps[i], labels[i], dataString[i]));
                }
            }
            return result;
        }
        catch(RException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void postWork() {
        super.postWork();

        if(!this.m_rInterface.close()){
            throw new IllegalStateException("Could not close r interface connection.");
        }
    }
}
