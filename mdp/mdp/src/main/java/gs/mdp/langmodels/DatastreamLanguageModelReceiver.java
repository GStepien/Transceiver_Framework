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

package gs.mdp.langmodels;

import gs.utils.datatypes.LabeledTimestampedData;
import gs.utils.datatypes.StringData;
import gs.tf.core.GenericMuxMultiReceiverTask;
import gs.utils.json.JSONTypedObject;
import gs.utils.r.RException;
import gs.mdp.utils.r.SimpleConcurrentR;
import gs.utils.r.SimpleRInterface;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.collections4.set.UnmodifiableSet;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

// TODO Create own object LanguageModel as interface between m_rInterface and this object to use here
// TODO LM with dynamically expandable vocabulary

// TODO Implement GenericMuxTransceiverTask and move evaluation to a prepended transceiver

public class DatastreamLanguageModelReceiver
        extends GenericMuxMultiReceiverTask.AbstractTargetReceiverTask<LabeledTimestampedData<?>> {

    private static final Logger LOGGER = Logger.getLogger(DatastreamLanguageModelReceiver.class.getName());

    private final boolean m_writeStats;
    private final BufferedWriter m_outWriter;
    private long m_dataNo;
    private long m_charNo;
    private static final String S_CSV_HEAD =
            "Runtime / ms, " +
                    "Runtime HH:mm:ss:SSS, "+
                    "Timestamp / ms, " +
                    "Timestamp HH:mm:ss:SSS, " +
                    "DataNumber, #Correct, #Incorrect, Error, CharacterDataNumber, #CharCorrect, #CharIncorrect, " +
            "CharacterError, NumLMStates, NumLMTransitions";

    private long m_startTime;

    private double m_numCorrectPredictions;
    private double m_numIncorrectPredictions;
    private final ArrayList<Boolean> m_predictionCorrectness;

    private double m_numCorrectCharPredictions;
    private double m_numIncorrectCharPredictions;
    private final ArrayList<Boolean> m_charPredictionCorrectness;

    private final int m_numElementsForError;

    private final SimpleRInterface m_rInterface;
    private final String m_motifBeginLabel;
    private long m_lastTimestamp;
    private final String m_nonMotivLabel;
    private final String m_idleWord;
    private final UnmodifiableSet<Integer> m_numericDimIndices;
    private final UnmodifiableSet<Integer> m_characterDimIndices;
    private final int m_idleID;
    private final UnmodifiableMap<Integer, Integer> m_dimToMotifBeginWordID;
    private final UnmodifiableMap<Integer, Integer> m_dimToNonMotivWordID;
    private final Random m_rnd;
    private final Long m_idleTimeout;

    private Long m_currentNABeginTime;

    private int m_currentNonIdlePathLength;

    private final int m_maxHistoryLength;
    private final String m_modelOutPath;

    //TODO make this a parameter
    private final boolean m_replot = true;
    private final long m_replotMinInterval = 10000;
    private long m_lastReplotTime;
    private final int m_replotRndSeed = 23987492;

    public DatastreamLanguageModelReceiver(
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int numMuxTransmitters,
            int port,
            String rDriverPath,
            String streamJSONPath,
            String motifBeginLabel,
            String nonMotivLabel,
            Collection<Integer> numericDimIndices,
            String statsOutPath, /* == null <=> write no stats*/
            int numElementsForError, /* Wait for this number of elements before starting to "forget" old values via the errorLearningRate */
            Long rnd_seed,
            Long idleTimeout) throws RException {
        super(inDataQueueCapacity, timeoutInterval, numMuxTransmitters);

        if(rDriverPath == null || streamJSONPath == null || motifBeginLabel == null ||
                nonMotivLabel == null || numericDimIndices == null){
            throw new NullPointerException();
        }

        if(nonMotivLabel.equals(motifBeginLabel)){
            throw new IllegalArgumentException("The non motif label, the inside motif and the begin motif labels must be pairwise non-equal.");
        }

        for(int id : numericDimIndices){
            if(id < 0 || id >= numMuxTransmitters){
                throw new IllegalArgumentException();
            }
        }

        Set<Integer> characterDimIndices = new HashSet<>();
        for(int i = 0; i < numMuxTransmitters; i++){
            if(!numericDimIndices.contains(i)){
                characterDimIndices.add(i);
            }
        }

        if(idleTimeout != null && idleTimeout < 0){
            throw new IllegalArgumentException();
        }
        this.m_idleTimeout = idleTimeout;

        if(rnd_seed == null){
            this.m_rnd = new Random();
        }
        else{
            this.m_rnd = new Random(rnd_seed);
        }
        this.m_characterDimIndices = (UnmodifiableSet<Integer>) UnmodifiableSet.unmodifiableSet(characterDimIndices);
        this.m_numericDimIndices = (UnmodifiableSet<Integer>) UnmodifiableSet.unmodifiableSet(new HashSet<>(numericDimIndices));

        this.m_nonMotivLabel = nonMotivLabel;
        this.m_motifBeginLabel = motifBeginLabel;
        this.m_lastTimestamp = 0;
        this.m_rInterface = new SimpleConcurrentR(port);

        this.m_startTime = -1;

        this.m_lastReplotTime = 0;
        this.m_currentNABeginTime = null;

        this.m_currentNonIdlePathLength = 0;

        if(!this.m_rInterface.evalBoolean("length(ls()) == 0")) {
            throw new IllegalStateException("The R environment of the associated RServe instance is not empty.");
        }
        this.m_rInterface.evalVoid("source(\""+
                Paths.get(rDriverPath).toAbsolutePath()+"\")");
        this.m_rInterface.evalVoid("driver <- get_driver_env()");
        this.m_rInterface.evalVoid("lm <- driver$create_empty_multiv_count_lm_from_json(json_file = \""+
                Paths.get(streamJSONPath).toAbsolutePath()+"\")");

        byte[] readAllBytes;
        try {
            readAllBytes = java.nio.file.Files.readAllBytes(Paths.get(streamJSONPath));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        String json_string = new String(readAllBytes);
        JSONTypedObject jsonObject = new JSONTypedObject(json_string);
        jsonObject = jsonObject.getJSONTypedObject("language_model");
        if(jsonObject.containsKey("json_file") && !jsonObject.isNull("json_file")){
            this.m_modelOutPath = jsonObject.getString("json_file");
        }
        else{
            this.m_modelOutPath = null;
        }

        this.m_rInterface.evalVoid("vocabulary <- lm$get_vocabulary()");

        LOGGER.config("Model language model vocabulary:\n"+
                this.m_rInterface.evalString("paste0(" +
                        "lapply(1:vocabulary$get_num_word_ids(), FUN = function(wordID) {" +
                        "   return(paste0(\"\\t\", wordID, \" -> \" , vocabulary$get_word(wordID), \", " +
                        "Dim: \", toString(vocabulary$get_id_dims(wordID)) ,\"\\n\"))" +
                        "})" +
                        ",collapse=\"\")"));


        if(this.m_rInterface.evalBoolean("lm$is_locked()")){
            throw new IllegalStateException();
        }

        this.m_idleWord = this.m_rInterface.evalString("vocabulary$get_idle_word()");
        this.m_idleID = this.m_rInterface.evalInt("vocabulary$get_idle_id()");

        this.m_maxHistoryLength = this.m_rInterface.evalInt("lm$get_max_history_length()");

        if(this.m_rInterface.evalInt("vocabulary$get_num_dims()") != numMuxTransmitters){
            throw new IllegalArgumentException();
        }
        int[] wordIDs;
        Integer idleId, beginMotifID, nonMotifID;
        Map<Integer, Integer> dimToMotifBeginWordID = new HashMap<>();
        Map<Integer, Integer> dimToNonMotifWordID = new HashMap<>();
        for(int index : this.m_numericDimIndices) {
            idleId = beginMotifID = nonMotifID = null;
            assert(!this.m_characterDimIndices.contains(index));
            wordIDs = this.m_rInterface.evalInts("vocabulary$get_dim_ids("+(index + 1)+")"); // R indices start at 1!
            if(wordIDs.length != 3){ // Numeric dimensions should ony have "NA", "MOTIF_BEGIN" and "idle" labels
                throw new IllegalStateException();
            }
            for(int i = 0; i < 3; i++){
                if(wordIDs[i] == this.m_idleID){
                    if(idleId != null){
                        throw new IllegalStateException();
                    }
                    idleId = wordIDs[i];
                    assert(this.m_rInterface.evalString("vocabulary$get_word("+idleId+")").equals(this.m_idleWord));
                }
                else if(this.m_rInterface.evalString("vocabulary$get_word("+wordIDs[i]+")").equals(this.m_motifBeginLabel)){
                    if(beginMotifID != null){
                        throw new IllegalStateException();
                    }
                    beginMotifID = wordIDs[i];
                }
                else if(this.m_rInterface.evalString("vocabulary$get_word("+wordIDs[i]+")").equals(this.m_nonMotivLabel)){
                    if(nonMotifID != null){
                        throw new IllegalStateException();
                    }
                    nonMotifID = wordIDs[i];
                }
                else{
                    throw new IllegalStateException("Unknown word encountered in vocabulary: "+this.m_rInterface.evalString("vocabulary$get_word("+wordIDs[i]+")"));
                }
            }
            if(idleId == null || beginMotifID == null || nonMotifID == null){
                throw new IllegalStateException("Missing words in vocabulary.");
            }
            dimToMotifBeginWordID.put(index, beginMotifID);
            dimToNonMotifWordID.put(index, nonMotifID);
        }
        this.m_numCorrectPredictions = 0;
        this.m_numIncorrectPredictions = 0;
        this.m_numCorrectCharPredictions = 0;
        this.m_numIncorrectCharPredictions = 0;
        this.m_charNo = 0;
        this.m_dataNo = 0;
        if(statsOutPath !=null){
            this.m_writeStats = true;
            if(numElementsForError < 1){
                throw new IllegalArgumentException();
            }

            this.m_numElementsForError = numElementsForError;

            File directory = new File(Paths.get(statsOutPath).getParent().toString());
            if(!directory.exists()){
                if(!directory.mkdirs()){
                    throw new RuntimeException(new IOException("Could not create output directory: "+directory));
                }
            }

            try {
                this.m_outWriter = new BufferedWriter(new FileWriter(statsOutPath));
                this.m_outWriter.write(S_CSV_HEAD);
                this.m_outWriter.newLine();
                this.m_outWriter.flush();
            }
            catch (IOException e){
                throw new RuntimeException(e); // No recovery
            }
        }
        else{
            this.m_outWriter = null;
            this.m_writeStats = false;
            this.m_numElementsForError = 0;
        }

        this.m_predictionCorrectness = new ArrayList<>(this.m_numElementsForError);
        this.m_charPredictionCorrectness = new ArrayList<>(this.m_numElementsForError);

        for(int index : this.m_characterDimIndices){
            assert(!this.m_numericDimIndices.contains(index));
            wordIDs = this.m_rInterface.evalInts("vocabulary$get_dim_ids("+(index + 1)+")"); // R indices start at 1!
            idleId = nonMotifID = null;
            for (int wordID : wordIDs) {
                if (wordID == this.m_idleID) {
                    if (idleId != null) {
                        throw new IllegalStateException();
                    }
                    idleId = wordID;
                    assert (this.m_rInterface.evalString("vocabulary$get_word(" + idleId + ")").equals(this.m_idleWord));
                } else if (this.m_rInterface.evalString("vocabulary$get_word(" + wordID + ")").equals(this.m_nonMotivLabel)) {
                    if (nonMotifID != null) {
                        throw new IllegalStateException();
                    }
                    nonMotifID = wordID;
                }
            }
            if(idleId == null || nonMotifID == null){
                throw new IllegalStateException("Missing words in vocabulary.");
            }
            assert(!dimToNonMotifWordID.keySet().contains(index));
            dimToNonMotifWordID.put(index, nonMotifID);
        }
        assert(dimToMotifBeginWordID.size() == this.m_numericDimIndices.size());
        assert(dimToNonMotifWordID.size() == numMuxTransmitters);
        assert(this.m_characterDimIndices.size() + this.m_numericDimIndices.size() == numMuxTransmitters);

        this.m_dimToMotifBeginWordID = (UnmodifiableMap<Integer, Integer>) UnmodifiableMap.unmodifiableMap(dimToMotifBeginWordID);
        this.m_dimToNonMotivWordID = (UnmodifiableMap<Integer, Integer>) UnmodifiableMap.unmodifiableMap(dimToNonMotifWordID);
    }

    @Override
    protected void processDataElementFromMux(final int muxIndex, LabeledTimestampedData<?> stringDataElement) throws InterruptedException {
        if(stringDataElement == null){
            throw new NullPointerException();
        }

        StringData dataElement;
        if(stringDataElement instanceof StringData){
            dataElement = (StringData) stringDataElement;
        }
        else {
            throw new IllegalArgumentException("The LabeledTimestampedData<?> instance must be an instance of StringData.");
        }

        try {
            if(this.m_startTime == -1){
                this.m_startTime = System.currentTimeMillis();
            }

            final String label = dataElement.getData();
            if(label == null){
                throw new NullPointerException();
            }

            final long timestamp = dataElement.getTimestamp();
            if(timestamp < this.m_lastTimestamp){
                throw new IllegalArgumentException("Timestamps not in order.");
            }

            if(this.m_idleTimeout != null){
                if(!label.equals(this.m_nonMotivLabel)){
                    this.m_currentNABeginTime = null;
                }
                else {
                    if (this.m_currentNABeginTime == null) {
                        this.m_currentNABeginTime = timestamp;
                    }
                    assert(timestamp >= this.m_currentNABeginTime);
                    if (timestamp - this.m_currentNABeginTime >= this.m_idleTimeout &&
                            !this.m_rInterface.evalBoolean("lm$is_in_idle_state()")) {
                        LOGGER.info("Language model event timeout. Assuming idle event. Non idle path length: " + (this.m_currentNonIdlePathLength + 1));
                        this.addTransition(this.m_idleID);
                        this.m_currentNABeginTime = timestamp;
                        this.m_currentNonIdlePathLength = 0;

                        if (this.m_replot && System.currentTimeMillis() - this.m_lastReplotTime >= this.m_replotMinInterval) {
                            this.m_rInterface.evalVoid("lm$plot_raw_adjacency_matrix_igraph(rnd_seed = " +
                                    this.m_replotRndSeed + "," +
                                    "plot_width = 750," + // TODO Make width/height argument
                                    "plot_height = 750)");
                            this.m_lastReplotTime = System.currentTimeMillis();
                        }
                    }

                }
            }


            this.m_lastTimestamp = timestamp;

            int wordID = -1, nonMotifWordID;

            if(label.equals(this.m_idleWord)){
                // TODO This should currently not be possible
                throw new UnsupportedOperationException();

            /*this.addTransition(this.m_idleID);
            return;*/
            }
            else if(this.m_numericDimIndices.contains(muxIndex) && label.equals(this.m_motifBeginLabel)) {
                wordID = this.m_dimToMotifBeginWordID.get(muxIndex);
                assert (wordID == this.getWordID(muxIndex, label));
                assert(wordID >= 1);
            }
            else if(this.m_characterDimIndices.contains(muxIndex) && !label.equals(this.m_nonMotivLabel)) {
                wordID = this.getWordID(muxIndex, label);
                assert(wordID != this.m_dimToNonMotivWordID.get(muxIndex));
                assert(wordID >= 1);
            }

            if(wordID >= 1) { // If motif event detected (i.e., no non-motiv label)
                this.m_currentNonIdlePathLength++;
                if (this.m_writeStats) {
                    Pair<Integer, String> expectedNextWord = this.getArgmaxNextMotifWord();
                    LOGGER.info("Actual word "+label+" in dimension "+muxIndex+" (model vocabulary id: "+
                            this.m_rInterface.evalInt("vocabulary$get_word_id("+(muxIndex+1)+", \""+label+"\")")+").\n" +
                            "Predicted word "+expectedNextWord.getRight()+" in dimension "+expectedNextWord.getLeft()+
                            " (model vocabulary id: "+ this.m_rInterface.evalInt(
                            "vocabulary$get_word_id("+(expectedNextWord.getLeft()+1)+", \""+expectedNextWord.getRight()+"\")")+").");
                    // Some character prediction or occurrence involved?
                    if(!this.m_numericDimIndices.contains(muxIndex) || !this.m_numericDimIndices.contains(expectedNextWord.getLeft())){
                        assert(this.m_characterDimIndices.contains(muxIndex) || this.m_characterDimIndices.contains(expectedNextWord.getLeft()));
                        if (expectedNextWord.getLeft() == muxIndex && expectedNextWord.getRight().equals(label)) {
                            if (this.m_charNo + 1 > this.m_numElementsForError) {
                                assert(this.m_charPredictionCorrectness.size() == this.m_numElementsForError);
                                boolean oldestCorrectness = this.m_charPredictionCorrectness.remove(0);
                                this.m_charPredictionCorrectness.add(true);
                                if(!oldestCorrectness) {
                                    this.m_numCorrectCharPredictions += 1;
                                    this.m_numIncorrectCharPredictions -= 1;
                                }
                            } else {
                                this.m_charPredictionCorrectness.add(true);
                                assert(this.m_charPredictionCorrectness.size() == this.m_charNo + 1);
                                this.m_numCorrectCharPredictions++;
                            }
                        } else {
                            if (this.m_charNo + 1 > this.m_numElementsForError) {
                                assert(this.m_charPredictionCorrectness.size() == this.m_numElementsForError);
                                boolean oldestCorrectness = this.m_charPredictionCorrectness.remove(0);
                                this.m_charPredictionCorrectness.add(false);
                                if(oldestCorrectness) {
                                    this.m_numCorrectCharPredictions -= 1;
                                    this.m_numIncorrectCharPredictions += 1;
                                }
                            } else {
                                this.m_charPredictionCorrectness.add(false);
                                assert(this.m_charPredictionCorrectness.size() == this.m_charNo + 1);
                                this.m_numIncorrectCharPredictions++;
                            }
                        }
                        this.m_charNo++;
                    }

                    if (expectedNextWord.getLeft() == muxIndex && expectedNextWord.getRight().equals(label)) {
                        if (this.m_dataNo + 1 > this.m_numElementsForError) {
                            assert(this.m_predictionCorrectness.size() == this.m_numElementsForError);
                            boolean oldestCorrectness = this.m_predictionCorrectness.remove(0);
                            this.m_predictionCorrectness.add(true);
                            if(!oldestCorrectness) {
                                this.m_numCorrectPredictions += 1;
                                this.m_numIncorrectPredictions -= 1;
                            }
                        } else {
                            this.m_predictionCorrectness.add(true);
                            assert(this.m_predictionCorrectness.size() == this.m_dataNo + 1);
                            this.m_numCorrectPredictions++;
                        }
                    } else {
                        if (this.m_dataNo + 1 > this.m_numElementsForError) {
                            assert(this.m_predictionCorrectness.size() == this.m_numElementsForError);
                            boolean oldestCorrectness = this.m_predictionCorrectness.remove(0);
                            this.m_predictionCorrectness.add(false);

                            if(oldestCorrectness) {
                                this.m_numCorrectPredictions -= 1;
                                this.m_numIncorrectPredictions += 1;
                            }
                        } else {
                            this.m_predictionCorrectness.add(false);
                            assert(this.m_predictionCorrectness.size() == this.m_dataNo + 1);
                            this.m_numIncorrectPredictions++;
                        }
                    }
                    assert(this.m_numIncorrectCharPredictions >= 0 && this.m_numCorrectCharPredictions >= 0 &&
                            this.m_numIncorrectPredictions >= 0 && this.m_numCorrectPredictions >= 0);

                    int numTransitions = this.m_rInterface.evalInt("lm$get_total_num_transitions()");
                    int numStates = this.m_rInterface.evalInt("lm$get_num_states()");
                    assert(this.m_startTime >= 0);
                    long currRuntime = System.currentTimeMillis() - this.m_startTime;
                    String msg = currRuntime + ", " + DurationFormatUtils.formatDuration(currRuntime, "HH:mm:ss:SSS") + ", " +
                            dataElement.getTimestamp() + ", " +
                            DurationFormatUtils.formatDuration(dataElement.getTimestamp(), "HH:mm:ss:SSS") + ", " +
                            (this.m_dataNo+1) + ", " +
                            this.m_numCorrectPredictions + ", " + this.m_numIncorrectPredictions + ", " +
                            (this.m_numIncorrectPredictions == 0.0 ?
                                    0.0 :
                                    this.m_numIncorrectPredictions / (this.m_numIncorrectPredictions + this.m_numCorrectPredictions)) + ", " +
                            (this.m_charNo + 1) + ", "+
                            this.m_numCorrectCharPredictions + ", " + this.m_numIncorrectCharPredictions + ", " +
                            (this.m_numIncorrectCharPredictions == 0.0 ?
                                    0.0 :
                                    this.m_numIncorrectCharPredictions / (this.m_numIncorrectCharPredictions + this.m_numCorrectCharPredictions)) + ", "+
                            numStates + ", "+
                            numTransitions;
                    LOGGER.finer("LM stats: "+msg);
                    try {
                        this.m_outWriter.write(msg);
                        this.m_outWriter.newLine();
                        this.m_outWriter.flush();
                    }
                    catch(IOException e) {
                        throw new RuntimeException(e); // No recovery
                    }
                    this.m_dataNo++;

                }
                int dimNum = this.getNumMuxTransmitters();
                for (int i = 0; i < dimNum; i++) {
                    if (i == muxIndex) {
                        this.addTransition(wordID);
                    } else {
                        nonMotifWordID = this.m_dimToNonMotivWordID.get(i);
                        assert (nonMotifWordID == this.getWordID(i, this.m_nonMotivLabel));
                        this.addTransition(nonMotifWordID);
                    }
                }
                assert(dimNum <= 1 ||
                        this.m_rInterface.evalBoolean(
                                "all(sapply(1:"+(dimNum-1)+", " +
                                        "FUN = function(dim){" +
                                        "  return(length(lm$.get_current_state()$get_state_word_id_listlist()[[dim]]) ==" +
                                        "    length(lm$.get_current_state()$get_state_word_id_listlist()[[dim+1]]))" +
                                        "}))"));

                if(this.m_replot && System.currentTimeMillis() - this.m_lastReplotTime >= this.m_replotMinInterval){
                    this.m_rInterface.evalVoid("lm$plot_raw_adjacency_matrix_igraph(rnd_seed = "+
                            this.m_replotRndSeed+"," +
                            "plot_width = 750," + // TODO Make width/height/plot_state_names argument
                            "plot_height = 750," +
                            "plot_state_names = FALSE)");
                    this.m_lastReplotTime = System.currentTimeMillis();
                }
            }
            else {
                assert(wordID == -1);
                LOGGER.fine("Actual word " + label + " in dimension " + muxIndex + ".\n" +
                        "No word prediction.");
            }
        }
        catch(RException e){
            throw new RuntimeException(e);
        }
    }

    private void addTransition(int wordID) throws RException {
        if(wordID != this.m_idleID){
            int wordDim = this.m_rInterface.evalInt("vocabulary$get_id_dims("+wordID+")"); // Should have length 1 for non-idle words
            int historyLengthInDim = this.m_rInterface.evalInt("length(lm$.get_current_state()$get_state_word_id_listlist()[["+wordDim+"]])");
            assert(historyLengthInDim <= this.m_maxHistoryLength);

            if(historyLengthInDim == this.m_maxHistoryLength){
                LOGGER.warning("History overflow.");
            }
        }

        this.m_rInterface.evalVoid("lm$add_transition(word_id = "+wordID+", state_character_id = NULL, proceed = TRUE)");
    }

    private int getWordID(int muxID, String word) throws RException {
        return this.m_rInterface.evalInt("vocabulary$get_word_id("+(muxID+1)+", \""+word+"\")");
    }

    // Prob of encountering wordID at given state where wordID is neither the idle word nor a NA word
    private double getRegularWordProbability(int dim, int wordID, String stateCharacterID) throws Exception {
        assert(this.m_rInterface.evalBoolean(wordID + " %in% vocabulary$get_all_ids()"));
        assert(this.m_rInterface.evalBoolean((dim+1)+" %in% vocabulary$get_id_dims("+wordID+")"));
        assert(wordID != this.m_idleID && wordID != this.m_dimToNonMotivWordID.get(dim));
        assert(this.m_rInterface.evalBoolean("\""+ stateCharacterID +"\"" +" %in% lm$get_state_names()"));

        int dimNum = this.getNumMuxTransmitters();
        assert(dimNum <= 1 ||
                this.m_rInterface.evalBoolean(
                        "all(sapply(1:"+(dimNum-1)+", " +
                                "FUN = function(dim){" +
                                "  return(length(lm$.get_state_names_to_states_list()[[\""+stateCharacterID+"\"]]$get_state_word_id_listlist()[[dim]]) ==" +
                                "    length(lm$.get_state_names_to_states_list()[[\""+stateCharacterID+"\"]]$get_state_word_id_listlist()[[dim+1]]))" +
                                "}))"));

        String originalState = this.m_rInterface.evalString("lm$get_current_state_character_id()");
        this.m_rInterface.evalVoid("lm$reset_current_state(state_character_id = \""+stateCharacterID+"\")");
        double logResult = 0; // Log is numerically more stable
        for(int d = 0; d < dimNum; d++){
            if(d == dim && !(this.m_rInterface.evalBoolean("lm$.get_current_state()$get_transition_target_character("+wordID+") "+
                    " %in% lm$get_state_names()")) ||
                d != dim && !(this.m_rInterface.evalBoolean("lm$.get_current_state()$get_transition_target_character("+
                        this.m_dimToNonMotivWordID.get(d)+") %in% lm$get_state_names()"))){
                // Target state not encountered yet -> set prob to smoothed probability
                LOGGER.fine("LM target state not encountered yet. Using smoothed fallback solution");
                this.m_rInterface.evalVoid("lm$reset_current_state(state_character_id = \""+stateCharacterID+"\")");
                logResult = Math.log(this.m_rInterface.evalDouble("lm$get_probability(" +
                        "word_id = "+wordID+"," +
                        "proceed = FALSE)"));
                break;
            }
            else if(d == dim){
                assert(this.m_rInterface.evalBoolean("lm$.get_current_state()$get_transition_target_character("+wordID+") "+
                        " %in% lm$get_state_names()"));
                logResult += Math.log(this.m_rInterface.evalDouble("lm$get_probability(" +
                        "word_id = " + wordID + "," +
                        "proceed = TRUE)"));

            }
            else{
                assert(this.m_rInterface.evalBoolean("lm$.get_current_state()$get_transition_target_character("+
                        this.m_dimToNonMotivWordID.get(d)+") %in% lm$get_state_names()"));
                logResult += Math.log(this.m_rInterface.evalDouble("lm$get_probability(" +
                        "word_id = " + this.m_dimToNonMotivWordID.get(d) + "," +
                        "proceed = TRUE)"));

            }
        }
        this.m_rInterface.evalVoid("lm$reset_current_state(state_character_id = \""+originalState+"\")");

        return(Math.exp(logResult));
    }

    public Pair<Integer, String> getArgmaxNextMotifWord(){
        // Note: LM learned by: If idX encountered in dim n, with idX NOT being non motif word id:
        //       Add NA transition to dim 1,2,..., n-1, add idX transition to dim n, add NA transition to
        //       dim n+1, ..., D - IN THAT ORDER
        //       => Finding argmax next non-NA word means finding most probable state that is D transitions away
        //          and extracting non-ID word leading to that state
        double highestProbability = Double.NEGATIVE_INFINITY, currentProbability;
        Pair<Integer, String> result;
        try {
            String label;
            ArrayList<Integer> candidates = new ArrayList<>(this.m_rInterface.evalInt("vocabulary$get_num_word_ids()"));
            for (int dim = 0; dim < this.getNumMuxTransmitters(); dim++) {
                for (int wordId : this.m_rInterface.evalInts("vocabulary$get_dim_ids(" + (dim + 1) + ")")) {
                    label = this.m_rInterface.evalString("vocabulary$get_word("+wordId+")");
                    if (wordId != this.m_idleID && (this.m_numericDimIndices.contains(dim) && label.equals(this.m_motifBeginLabel)
                        || this.m_characterDimIndices.contains(dim) && !label.equals(this.m_nonMotivLabel))) {
                        currentProbability = this.getRegularWordProbability(
                                dim,
                                wordId,
                                this.m_rInterface.evalString("lm$get_current_state_character_id()"));
                        if(currentProbability > highestProbability){
                            candidates.clear();
                            highestProbability = currentProbability;
                        }
                        if(currentProbability >= highestProbability){
                            candidates.add(wordId);
                        }
                    }
                }
            }

            assert(!candidates.isEmpty());
            Integer bestWordID = candidates.get(this.m_rnd.nextInt(candidates.size()));
            int num_encountered_next_states = this.m_rInterface.evalInt("length(lm$get_word_ids_to_existing_state())");
            assert(num_encountered_next_states >= 1); // Idle state should always exist and be reachable from each state
            StringBuilder sb = new StringBuilder();
            if(num_encountered_next_states == 1){
                sb.append("Each non-idle word currently leads to a non-existing state.\n");
                assert(candidates.size() ==
                        this.m_rInterface.evalInt("vocabulary$get_num_word_ids()") - 1 - this.getNumMuxTransmitters()); // Minus idle word and NA words
            }
            sb.append("Most probable word id candidates (model vocabulary): ").
                    append(Arrays.toString(candidates.toArray(new Integer[0]))).append("\n");
            LOGGER.finer(sb.toString()+"Chosen word ID (model vocabulary): "+bestWordID);
            result = new ImmutablePair<>(
                    this.m_rInterface.evalInt("vocabulary$get_id_dims("+bestWordID+")") - 1,// R indices start at 1!
                    this.m_rInterface.evalString("vocabulary$get_word("+bestWordID+")"));

        }
        catch(Exception e){
            throw new RuntimeException("this should not have happened.", e);
        }

        assert(result != null);
        return result;
    }

    @Override
    protected void postWork() {
        super.postWork();
        double[] stats;
        try {
            if(!this.m_rInterface.evalBoolean("lm$is_in_idle_state()") &&
                !this.m_rInterface.evalBoolean("lm$is_strongly_connected()")) {
                LOGGER.warning("LM is not strongly connected. Last state is non idle. " +
                        "Adding idle transition from last state to ensure strong connectivity.");
                // Lm not being strongly connected should only be possible if we are not in the idle state and
                // the CURRENT state has no child states yet
                assert(this.m_rInterface.evalBoolean("length(lm$.get_current_state()$get_child_state_list()) == 0"));
                this.addTransition(this.m_idleID);
            }
            assert(this.m_rInterface.evalBoolean("lm$is_strongly_connected()"));

            // Otherwise get_raw_stats produces error
            if(this.m_rInterface.evalInt("lm$get_num_states()") >= 3) {
                stats = this.m_rInterface.evalDoubles("lm$get_raw_stats()");
                if(stats == null){
                    throw new NullPointerException();
                }
                if(stats.length != 3){
                    throw new IllegalStateException("Unexpected number of language model statistics.");
                }
                LOGGER.config("Trained language model min error: " + stats[0] + "\n" +
                        "Trained language model entropy rate: " + stats[1] + "\n" +
                        "Trained language model perplexity rate: "+ stats[2]);
            }
            else{
                LOGGER.warning("Language model has less than 3 states. Model statistics (min error, " +
                        "entropy & perplexity rate) can only be computed with 3 or more states (including idle state).");
            }

            if(this.m_modelOutPath != null){
                this.m_rInterface.evalVoid("lm$write_lm(json_file = \""+ this.m_modelOutPath +"\", " +
                        "overwrite = TRUE)");
            }
        } catch (RException e) {
            throw new RuntimeException(e);
        }
        if(this.m_writeStats){
            try {
                this.m_outWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e); // No recovery
            }
        }

        if(!this.m_rInterface.close()){
            throw new IllegalStateException("Could not close r interface connection.");
        }
    }
}
