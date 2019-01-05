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

package gs.mdp.evaluation;

import gs.utils.datatypes.StringData;
import gs.tf.core.GenericMuxMultiReceiverTask;

import org.apache.commons.collections4.set.UnmodifiableSet;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class SimpleStringDataF1Receiver extends GenericMuxMultiReceiverTask.AbstractTargetReceiverTask<StringData> {

    private static final Logger LOGGER = Logger.getLogger(SimpleStringDataF1Receiver.class.getName());

    private final BufferedWriter m_outWriter;
    private long m_dataNo;

    private static final String S_CSV_HEAD =
            "Runtime / ms, " +
                    "Runtime HH:mm:ss:SSS, "+
                    "Timestamp / ms, " +
                    "Timestamp HH:mm:ss:SSS, " +
                    "DataNumber, PrecisionPositives = TP / (TP + FP), " +
                    "RecallPositives = TP / (TP + FN), " +
                    "F1Positives = Harm_Mean(PrecisionPositives, RecallPositives)," +
                    "PrecisionNegatives = TN / (TN + FN), " +
                    "RecallNegatives = TN / (TN + FP), " +
                    "F1Negatives = Harm_Mean(PrecisionNegatives, RecallNegatives)," +
                    "F1 = Harm_Mean(F1Positives, F1Negatives)";

    private long m_startTime;

    private long m_truePositives;
    private long m_falsePositives;
    private long m_falseNegatives;
    private long m_trueNegatives;

    private final UnmodifiableSet<String> m_negativeLabels;
    private final UnmodifiableSet<String> m_positiveLabels;

    public double getPrecisionPositives(){
        if(this.m_truePositives == 0){
            return 0.0;
        }
        double result = 1.0 * this.m_truePositives / (this.m_truePositives + this.m_falsePositives);
        assert(result >= 0 && result <= 1);

        return (result);
    }

    public double getRecallPositives(){
        if(this.m_truePositives == 0){
            return 0.0;
        }
        double result =  1.0 * this.m_truePositives / (this.m_truePositives + this.m_falseNegatives);
        assert(result >= 0 && result <= 1);

        return (result);
    }

    public double getPrecisionNegatives(){
        if(this.m_trueNegatives == 0){
            return 0.0;
        }
        double result = 1.0 * this.m_trueNegatives / (this.m_trueNegatives + this.m_falseNegatives);
        assert(result >= 0 && result <= 1);

        return (result);
    }

    public double getRecallNegatives(){
        if(this.m_trueNegatives == 0){
            return 0.0;
        }
        double result =  1.0 * this.m_trueNegatives / (this.m_trueNegatives + this.m_falsePositives);
        assert(result >= 0 && result <= 1);

        return (result);
    }

    public double getF1Positives(){
        double precision, recall;
        if((precision = this.getPrecisionPositives()) == 0){
            return 0.0;
        }
        if((recall = this.getRecallPositives()) == 0){
            return 0.0;
        }

        double result = (2 * (precision * recall) / (precision + recall));
        assert(result >= 0 && result <= 1);
        return (result);
    }

    public double getF1Negatives(){
        double precision, recall;
        if((precision = this.getPrecisionNegatives()) == 0){
            return 0.0;
        }
        if((recall = this.getRecallNegatives()) == 0){
            return 0.0;
        }

        double result = (2 * (precision * recall) / (precision + recall));
        assert(result >= 0 && result <= 1);
        return (result);
    }

    public double getF1(){
        double f1Positives, f1negatives;
        if((f1Positives = this.getF1Positives()) == 0){
            return 0.0;
        }
        if((f1negatives = this.getF1Negatives()) == 0){
            return 0.0;
        }

        double result = (2 * (f1Positives * f1negatives) / (f1Positives + f1negatives));
        assert(result >= 0 && result <= 1);
        return (result);
    }

    public SimpleStringDataF1Receiver(
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int numMuxTransmitters,
            String outPath,
            String motifBeginLabel,
            String insideMotifLabel,
            String nonMotifLabel) throws IOException {
        super(inDataQueueCapacity, timeoutInterval, numMuxTransmitters);

        Set<String> positiveLabels = new HashSet<>();
        positiveLabels.add(motifBeginLabel);
        positiveLabels.add(insideMotifLabel);
        Set<String> negativeLabels = new HashSet<>();
        negativeLabels.add(nonMotifLabel);

        if(outPath == null){
            throw new NullPointerException();
        }
        if(positiveLabels.size() == 0 || negativeLabels.size() == 0){
            throw new IllegalArgumentException();
        }

        this.m_positiveLabels = (UnmodifiableSet<String>) 
        		UnmodifiableSet.unmodifiableSet(new HashSet<>(positiveLabels));
        this.m_negativeLabels = (UnmodifiableSet<String>) 
        		UnmodifiableSet.unmodifiableSet(new HashSet<>(negativeLabels));
        this.m_dataNo = 1;
        for(String label : this.m_positiveLabels){
            if(this.m_negativeLabels.contains(label)){
                throw new IllegalArgumentException();
            }
        }
        for(String label : this.m_negativeLabels){
            if(this.m_positiveLabels.contains(label)){
                throw new IllegalArgumentException();
            }
        }

        File directory = new File(Paths.get(outPath).getParent().toString());
        if(!directory.exists()){
            if(!directory.mkdirs()){
                throw new RuntimeException(new IOException("Could not create output directory: "+directory));
            }
        }
        this.m_outWriter = new BufferedWriter(new FileWriter(outPath));
        this.m_outWriter.write(S_CSV_HEAD);
        this.m_outWriter.newLine();
        this.m_outWriter.flush();
        this.m_falseNegatives = 0;
        this.m_falsePositives = 0;
        this.m_truePositives = 0;
        this.m_trueNegatives = 0;

        this.m_startTime = -1;
    }


    @Override
    protected void processDataElementFromMux(int muxIndex, StringData dataElement) throws InterruptedException {
        String data = dataElement.getData();
        String ground = dataElement.getLabel();
        if(this.m_startTime == -1){
            this.m_startTime = System.currentTimeMillis();
        }

        if(this.m_negativeLabels.contains(data)){
            if(data.equals(ground)){
                this.m_trueNegatives++;
            }
            else{
                this.m_falseNegatives++;
            }
        }
        else{
            assert(this.m_positiveLabels.contains(data));

            if(this.m_negativeLabels.contains(ground)){
                this.m_falsePositives++;
            }
            else{
                this.m_truePositives++;
            }
        }
        assert(this.m_startTime >= 0);
        long currRuntime = System.currentTimeMillis() - this.m_startTime;
        String msg = currRuntime + ", " + DurationFormatUtils.formatDuration(currRuntime, "HH:mm:ss:SSS") + ", " +
                dataElement.getTimestamp() + ", " +
                DurationFormatUtils.formatDuration(dataElement.getTimestamp(), "HH:mm:ss:SSS") + ", " +
                this.m_dataNo + ", " +
                this.getPrecisionPositives() + ", " + this.getRecallPositives() +", " + this.getF1Positives() +", "+
                this.getPrecisionNegatives() +", " + this.getRecallNegatives() +", "+ this.getF1Negatives() +", " +
                this.getF1();

        LOGGER.finer("F1 stats: "+msg);

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


    @Override
    protected void postWork() {
        super.postWork();
        try {
            this.m_outWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e); // No recovery
        }
    }
}
