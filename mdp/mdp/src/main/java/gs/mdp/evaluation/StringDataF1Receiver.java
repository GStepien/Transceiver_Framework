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
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class StringDataF1Receiver extends GenericMuxMultiReceiverTask.AbstractTargetReceiverTask<StringData> {

    private class Event{

        private int m_listIndex;

        private int m_startIndex;
        private int m_length;
        private final boolean m_isNA;
        private final boolean m_isGroundTruth;
        private boolean m_finalLength;

        private boolean m_maxF1Computed;

        private boolean m_hasAllDependencies;
        private final ArrayList<Event> m_dependencies;

        private final ArrayList<Event> m_thisEventList;
        private final ArrayList<Event> m_otherEventList;

        public Event(boolean isNA, boolean isGroundTruth, int muxIndex){
            if(isGroundTruth){
                this.m_thisEventList = StringDataF1Receiver.this.m_groundTruthEvents.get(muxIndex);
                this.m_otherEventList = StringDataF1Receiver.this.m_detectedEvents.get(muxIndex);
            }
            else{
                this.m_thisEventList = StringDataF1Receiver.this.m_detectedEvents.get(muxIndex);
                this.m_otherEventList = StringDataF1Receiver.this.m_groundTruthEvents.get(muxIndex);
            }

            this.m_listIndex = 0;
            this.m_startIndex = 0;
            this.m_isNA = isNA;
            this.m_isGroundTruth = isGroundTruth;
            this.m_finalLength = false;
            this.m_maxF1Computed = false;
            this.m_hasAllDependencies = false;
            this.m_length = 1;

            this.m_dependencies = new ArrayList<>(INITIAL_CAPACITY);
        }

        public int getStartIndex(){
            return this.m_startIndex;
        }

        public int getEndIndex(){
            return this.m_startIndex + this.m_length - 1;
        }

        public int getListIndex(){
            return this.m_listIndex;
        }

        public boolean isMaxF1Computed(){
            return this.m_maxF1Computed;
        }

        public int getLength(){
            return this.m_length;
        }

        public boolean hasFinalLength(){
            return this.m_finalLength;
        }

        public boolean isNA(){
            return this.m_isNA;
        }

        public boolean isGroundTruth(){
            return this.m_isGroundTruth;
        }

        public boolean hasAllDependencies(){
            return this.m_hasAllDependencies;
        }

        public void incrementLength(){
            assert(!this.m_finalLength);
            this.m_length++;
        }

        public void finalizeLength(){
            assert(!this.m_finalLength);
            this.m_finalLength = true;
        }

        public void addToStartIndex(int delta){
            this.m_startIndex += delta;
            assert(this.m_startIndex >= 0);
        }

        public void addToListIndex(int delta){
            this.m_listIndex += delta;
            assert(this.m_thisEventList.get(this.m_listIndex) == this);
            assert(this.m_listIndex == 0 || this.m_startIndex == this.m_thisEventList.get(this.m_listIndex - 1).getStartIndex() +
                    this.m_thisEventList.get(this.m_listIndex - 1).getLength());
        }

        // Note: Dependency = all overlapping subsequences. During F1 computation, only those other sequences are
        //       considered that match w.r.t. being NA/non-NA, see getPrecision(...)
        public void addDependency(Event dependency){
            assert(!this.m_hasAllDependencies);
            assert(this.isGroundTruth() && !dependency.isGroundTruth() ||
                    !this.isGroundTruth() && dependency.isGroundTruth());
            assert(this.hasFinalLength() && dependency.hasFinalLength());
            assert(dependency.getEndIndex() >= this.getStartIndex() && dependency.getStartIndex() <= this.getEndIndex());
            assert(this.m_dependencies.size() == 0 ||
                    dependency.getStartIndex() == this.m_dependencies.get(this.m_dependencies.size() - 1).getEndIndex() + 1);
            assert(this.m_dependencies.size() > 0 ||
                    dependency.getStartIndex() <= this.getStartIndex());
            assert(this.m_thisEventList.get(this.getListIndex()) == this);
            assert(this.m_otherEventList.get(dependency.getListIndex()) == dependency);

            this.m_dependencies.add(dependency);
            if(dependency.getEndIndex() >= this.getEndIndex()){
                this.m_hasAllDependencies = true;
            }

        }

        public boolean mayBeDeleted(){
            if(!this.isMaxF1Computed()){
                return false;
            }
            assert (this.hasAllDependencies());

            for(Event dep : this.m_dependencies){
                if(!dep.isMaxF1Computed()){
                    return false;
                }
            }
            return true;
        }

        // Highest F1 among overlapping events
        public double getMaxF1(){
            assert(!this.isMaxF1Computed());
            assert(this.hasAllDependencies());

            double currentResult;
            double bestResult = 0;
            Event dep;
            for(int i = 0; i < this.m_dependencies.size(); i++){
                dep = this.m_dependencies.get(i);
                assert(isOverlapping(this, dep));
                assert(i > 0 || dep.getStartIndex() <= this.getStartIndex());
                assert(i < this.m_dependencies.size() - 1 || dep.getEndIndex() >= this.getEndIndex());
                assert(i == 0 || i == this.m_dependencies.size() - 1 ||
                        dep.getStartIndex() >= this.getStartIndex() && dep.getEndIndex() <= this.getEndIndex());
                assert(i == 0 || dep.getStartIndex() == this.m_dependencies.get(i-1).getEndIndex() + 1);

                currentResult = getF1(this, dep);
                if(currentResult > bestResult){
                    bestResult = currentResult;
                }
            }

            this.m_maxF1Computed = true;
            return bestResult;
        }
    }

    private static int getOverlapSize(Event e1, Event e2){
        if(!isOverlapping(e1, e2)){
            return 0;
        }
        else{
            boolean firstStartLeq = e1.getStartIndex() <= e2.getStartIndex();
            boolean firstEndLeq = e1.getEndIndex() <= e2.getEndIndex();

            if(firstStartLeq && firstEndLeq){
                return e1.getEndIndex() - e2.getStartIndex() + 1;
            }
            else if(!firstStartLeq && firstEndLeq){
                return e1.getEndIndex() - e1.getStartIndex() + 1;
            }
            else if(firstStartLeq) { // At this point: !firstEndLeq){
                return e2.getEndIndex() - e2.getStartIndex() + 1;
            }
            else{
                assert(!firstStartLeq && !firstEndLeq);
                return e2.getEndIndex() - e1.getStartIndex() + 1;
            }
        }

    }

    private static double getPrecision(Event detected, Event groundTruth){
        assert(detected != null && groundTruth != null);
        assert(detected.getLength() > 0 && groundTruth.getLength() > 0);
        assert(detected.hasFinalLength() && groundTruth.hasFinalLength());
        if(detected.isNA() && !groundTruth.isNA() || !detected.isNA() && groundTruth.isNA()){
            return 0.0;
        }
        else {
            return 1.0 * getOverlapSize(detected, groundTruth) / detected.getLength();
        }
    }

    private static double getRecall(Event detected, Event groundTruth){
        return getPrecision(groundTruth, detected);
    }

    private static double getF1(Event detected, Event groundTruth){
        double precision = getPrecision(detected, groundTruth);
        double recall = getRecall(detected, groundTruth);
        assert(precision >= 0 && precision <= 1);
        assert(recall >= 0 && recall <= 1);

        if(precision + recall == 0){
            return 0;
        }
        else{
            return 2*(precision * recall)/(precision + recall);
        }
    }

    private static boolean isOverlapping(Event e1, Event e2){
        return e1.getEndIndex() >= e2.getStartIndex() && e1.getStartIndex() <= e2.getEndIndex();
    }

    // TODO make initial capacity parameter ?
    private static final int INITIAL_CAPACITY = 16;
    private static final Logger LOGGER = Logger.getLogger(StringDataF1Receiver.class.getName());

    private final BufferedWriter m_outWriter;
    private long m_dataNo;

    private static final String S_CSV_HEAD =
            "Runtime / ms, " +
            "Runtime HH:mm:ss:SSS, "+
            "Timestamp / ms, " +
            "Timestamp HH:mm:ss:SSS, " +
            "DataNumber, " +
            "PositivesPrecision, " +
            "PositivesRecall, " +
            "PositivesF1, " +
            "NegativesPrecision, " +
            "NegativesRecall, " +
            "NegativesF1, " +
            "OverallF1";

    private long m_startTime;

    private final ArrayList<ArrayList<Event>> m_detectedEvents;
    private final ArrayList<Integer> m_nextStartIndexDetectedEvents;
    private final ArrayList<ArrayList<Event>> m_groundTruthEvents;
    private final ArrayList<Integer> m_nextStartIndexGroundTruthEvents;

    private final ArrayList<Event> m_nextDetectedEvent;
    private final ArrayList<Event> m_nextGroundTruthEvent;

    private double m_f1PositivesPrecision;
    private double m_f1PositivesRecall;
    private double m_f1Positives;
    private long m_numPositivesPrecision;
    private long m_numPositivesRecall;

    private double m_f1NegativesPrecision;
    private double m_f1NegativesRecall;
    private double m_f1Negatives;
    private long m_numNegativesPrecision;
    private long m_numNegativesRecall;

    private double m_overallF1;

    private final String m_motifBeginLabel;
    private final String m_insideMotifLabel;
    private final String m_nonMotifLabel;

    private final ArrayList<String> m_lastGroundTruthLabel;
    private final ArrayList<String> m_lastDataLabel;

    public StringDataF1Receiver(
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int numMuxTransmitters,
            String outPath,
            String motifBeginLabel,
            String insideMotifLabel,
            String nonMotifLabel) throws IOException {
        super(inDataQueueCapacity, timeoutInterval, numMuxTransmitters);

        if(outPath == null || motifBeginLabel == null || insideMotifLabel == null || nonMotifLabel == null){
            throw new NullPointerException();
        }
        if(motifBeginLabel.equals(insideMotifLabel) || motifBeginLabel.equals(nonMotifLabel) || insideMotifLabel.equals(nonMotifLabel)){
            throw new IllegalArgumentException();
        }

        this.m_startTime = -1;

        this.m_motifBeginLabel = motifBeginLabel;
        this.m_insideMotifLabel = insideMotifLabel;
        this.m_nonMotifLabel = nonMotifLabel;

        this.m_dataNo = 1;

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

        this.m_f1PositivesPrecision = 0.0;
        this.m_f1PositivesRecall = 0.0;
        this.m_f1Positives = 0.0;
        this.m_numPositivesPrecision = 0;
        this.m_numPositivesRecall = 0;

        this.m_f1NegativesPrecision = 0.0;
        this.m_f1NegativesRecall = 0.0;
        this.m_f1Negatives = 0.0;
        this.m_numNegativesPrecision = 0;
        this.m_numNegativesRecall = 0;

        this.m_overallF1 = 0.0;

        this.m_nextDetectedEvent = new ArrayList<>(numMuxTransmitters);
        this.m_nextGroundTruthEvent = new ArrayList<>(numMuxTransmitters);
        this.m_lastGroundTruthLabel = new ArrayList<>(numMuxTransmitters);
        this.m_lastDataLabel = new ArrayList<>(numMuxTransmitters);
        this.m_nextStartIndexDetectedEvents = new ArrayList<>(numMuxTransmitters);
        this.m_nextStartIndexGroundTruthEvents = new ArrayList<>(numMuxTransmitters);
        this.m_detectedEvents = new ArrayList<>(numMuxTransmitters);
        this.m_groundTruthEvents = new ArrayList<>(numMuxTransmitters);

        for(int i = 0; i < numMuxTransmitters; i++) {
            this.m_nextDetectedEvent.add(null);
            this.m_nextGroundTruthEvent.add(null);
            this.m_lastGroundTruthLabel.add(null);
            this.m_lastDataLabel.add(null);
            this.m_nextStartIndexDetectedEvents.add(0);
            this.m_nextStartIndexGroundTruthEvents.add(0);
            this.m_detectedEvents.add(new ArrayList<>(INITIAL_CAPACITY));
            this.m_groundTruthEvents.add(new ArrayList<>(INITIAL_CAPACITY));
        }
    }

    private void updateF1s() {
        if(this.m_f1PositivesPrecision + this.m_f1PositivesRecall == 0){
            this.m_f1Positives = 0.0;
        }
        else {
            this.m_f1Positives = 2 * (this.m_f1PositivesPrecision * this.m_f1PositivesRecall) /
                    (this.m_f1PositivesPrecision + this.m_f1PositivesRecall);
        }

        if(this.m_f1NegativesPrecision + this.m_f1NegativesRecall == 0){
            this.m_f1Negatives = 0.0;
        }
        else {
            this.m_f1Negatives = 2 * (this.m_f1NegativesPrecision * this.m_f1NegativesRecall) /
                    (this.m_f1NegativesPrecision + this.m_f1NegativesRecall);
        }

        if(this.m_f1Positives + this.m_f1Negatives == 0){
            this.m_overallF1 = 0.0;
        }
        else {
            this.m_overallF1 = 2 * (this.m_f1Positives * this.m_f1Negatives) /
                    (this.m_f1Positives + this.m_f1Negatives);
        }
    }

    private void finalizeAndAddEventToList(Event currentEvent,
                                           int nextStartIndex,
                                           ArrayList<Event> targetList,
                                           ArrayList<Event> potentialDependencies){
        Event event;
        currentEvent.finalizeLength();
        currentEvent.addToStartIndex(nextStartIndex);
        targetList.add(currentEvent);
        currentEvent.addToListIndex(targetList.size() - 1);
        ArrayList<Event> dependencies = new ArrayList<>(potentialDependencies.size());
        for(int i = potentialDependencies.size() - 1; i >= 0; i--){
            event = potentialDependencies.get(i);
            if(isOverlapping(currentEvent, event)){
                dependencies.add(event);
            }
            else{
                break;
            }
        }

        for(int i = dependencies.size() - 1; i >= 0; i--){
            event = dependencies.get(i);
            currentEvent.addDependency(event);
            event.addDependency(currentEvent);
        }
    }

    private boolean updateFields(int muxIndex){
        boolean updatedF1s = false;
        ArrayList<Event> eventList = this.m_detectedEvents.get(muxIndex);
        for (Event event : eventList) {
            if (event.hasAllDependencies()) {
                if (!event.isMaxF1Computed()) {
                    if (event.isNA()) {
                        this.m_f1NegativesPrecision =
                                1.0 * this.m_numNegativesPrecision / (this.m_numNegativesPrecision + 1) * this.m_f1NegativesPrecision +
                                        event.getMaxF1() / (this.m_numNegativesPrecision + 1);
                        this.m_numNegativesPrecision++;
                    } else {
                        this.m_f1PositivesPrecision =
                                1.0 * this.m_numPositivesPrecision / (this.m_numPositivesPrecision + 1) * this.m_f1PositivesPrecision +
                                        event.getMaxF1() / (this.m_numPositivesPrecision + 1);
                        this.m_numPositivesPrecision++;
                    }
                    updatedF1s = true;
                }
            } else {
                break;
            }
        }

        eventList = this.m_groundTruthEvents.get(muxIndex);
        for (Event event : eventList) {
            if (event.hasAllDependencies()) {
                if (!event.isMaxF1Computed()) {
                    if (event.isNA()) {
                        this.m_f1NegativesRecall =
                                1.0 * this.m_numNegativesRecall / (this.m_numNegativesRecall + 1) * this.m_f1NegativesRecall +
                                        event.getMaxF1() / (this.m_numNegativesRecall + 1);
                        this.m_numNegativesRecall++;
                    } else {
                        this.m_f1PositivesRecall =
                                1.0 * this.m_numPositivesRecall / (this.m_numPositivesRecall + 1) * this.m_f1PositivesRecall +
                                        event.getMaxF1() / (this.m_numPositivesRecall + 1);
                        this.m_numPositivesRecall++;
                    }
                    updatedF1s = true;
                }
            } else {
                break;
            }
        }
        return updatedF1s;
    }

    @SuppressWarnings("unchecked")
    private void deleteOldEvents(int muxIndex){

        Event event;
        int listIndex;
        for(ArrayList<Event> list : new ArrayList[]{this.m_detectedEvents.get(muxIndex), this.m_groundTruthEvents.get(muxIndex)}){
            listIndex = -1;
            for (int i = 0; i < list.size(); i++) {
                event = list.get(i);
                if (event.mayBeDeleted()) {
                    listIndex = i;
                } else {
                    break;
                }
            }

            if (listIndex >= 0) {
                list.subList(0, listIndex + 1).clear();
            }
            if(listIndex != -1) {
                for (Event ev : list) {
                    ev.addToListIndex(-(listIndex + 1));
                }
            }
        }

        ArrayList<Event> detectedEventList = this.m_detectedEvents.get(muxIndex);
        ArrayList<Event> groundTruthEventList = this.m_groundTruthEvents.get(muxIndex);
        Integer deltaStartIndex = null;
        if(detectedEventList.size() > 0){
            deltaStartIndex = detectedEventList.get(0).getStartIndex();
            if(groundTruthEventList.size() > 0){
                deltaStartIndex = Math.min(deltaStartIndex, groundTruthEventList.get(0).getStartIndex());
            }
        }
        else if(groundTruthEventList.size() > 0){
            deltaStartIndex = groundTruthEventList.get(0).getStartIndex();
        }
        assert(deltaStartIndex == null || deltaStartIndex >= 0);
        if(deltaStartIndex != null && deltaStartIndex > 0){
            this.m_nextStartIndexGroundTruthEvents.set(muxIndex, this.m_nextStartIndexGroundTruthEvents.get(muxIndex) - deltaStartIndex);
            this.m_nextStartIndexDetectedEvents.set(muxIndex,  this.m_nextStartIndexDetectedEvents.get(muxIndex) - deltaStartIndex);
            for(Event ev : detectedEventList){
                ev.addToStartIndex(-deltaStartIndex);
            }
            for(Event ev : groundTruthEventList){
                ev.addToStartIndex(-deltaStartIndex);
            }
        }

    }

    @Override
    protected void processDataElementFromMux(int muxIndex, StringData dataElement) throws InterruptedException {
        String data = dataElement.getData();
        String ground = dataElement.getLabel();
        if(this.m_startTime == -1){
            this.m_startTime = System.currentTimeMillis();
        }

        if(this.m_nextDetectedEvent.get(muxIndex) == null){ // First call with that muxIndex?
            assert(this.m_nextGroundTruthEvent.get(muxIndex) == null);
            assert(this.m_groundTruthEvents.get(muxIndex).size() == 0);
            assert(this.m_detectedEvents.get(muxIndex).size() == 0);
            assert(this.m_lastGroundTruthLabel.get(muxIndex) == null && this.m_lastDataLabel.get(muxIndex) == null);

            assert(!data.equals(this.m_insideMotifLabel));

            if(data.equals(this.m_nonMotifLabel)){
                this.m_nextDetectedEvent.set(muxIndex, new Event(true, false, muxIndex));
            }
            else{
                assert(data.equals(this.m_motifBeginLabel));
                this.m_nextDetectedEvent.set(muxIndex, new Event( false, false, muxIndex));
            }

            if(ground.equals(this.m_nonMotifLabel)){
                this.m_nextGroundTruthEvent.set(muxIndex, new Event(true, true, muxIndex));
            }
            else{
                this.m_nextGroundTruthEvent.set(muxIndex, new Event(false, true, muxIndex));
            }
        }
        else{
            assert(this.m_nextGroundTruthEvent.get(muxIndex) != null && this.m_nextDetectedEvent.get(muxIndex) != null);
            assert(this.m_lastDataLabel.get(muxIndex) != null && this.m_lastGroundTruthLabel.get(muxIndex) != null);
            Event nextDetectedEv = this.m_nextDetectedEvent.get(muxIndex);
            Event nextGroundTruthEv = this.m_nextGroundTruthEvent.get(muxIndex);
            if(data.equals(this.m_nonMotifLabel)){
                if(nextDetectedEv.isNA()){
                    assert(this.m_lastDataLabel.get(muxIndex).equals(this.m_nonMotifLabel));
                    nextDetectedEv.incrementLength();
                }
                else{
                    assert(this.m_lastDataLabel.get(muxIndex).equals(this.m_insideMotifLabel) ||
                            this.m_lastDataLabel.get(muxIndex).equals(this.m_motifBeginLabel));
                    // Move next detected event to list, add dependencies
                    this.finalizeAndAddEventToList(nextDetectedEv,
                            this.m_nextStartIndexDetectedEvents.get(muxIndex),
                            this.m_detectedEvents.get(muxIndex),
                            this.m_groundTruthEvents.get(muxIndex));

                    this.m_nextStartIndexDetectedEvents.set(muxIndex,
                            this.m_nextStartIndexDetectedEvents.get(muxIndex) + nextDetectedEv.getLength());
                    this.m_nextDetectedEvent.set(muxIndex, new Event(true, false, muxIndex));
                }
            }
            else if(data.equals(this.m_motifBeginLabel)){
                // Move next detected event to list, add dependencies
                this.finalizeAndAddEventToList(nextDetectedEv,
                        this.m_nextStartIndexDetectedEvents.get(muxIndex),
                        this.m_detectedEvents.get(muxIndex),
                        this.m_groundTruthEvents.get(muxIndex));


                this.m_nextStartIndexDetectedEvents.set(muxIndex,
                        this.m_nextStartIndexDetectedEvents.get(muxIndex) + nextDetectedEv.getLength());
                this.m_nextDetectedEvent.set(muxIndex, new Event(false, false, muxIndex));
            }
            else {
                assert(data.equals(this.m_insideMotifLabel));
                assert(this.m_lastDataLabel.get(muxIndex).equals(this.m_insideMotifLabel) ||
                        this.m_lastDataLabel.get(muxIndex).equals(this.m_motifBeginLabel));

                assert(!nextDetectedEv.isNA());
                nextDetectedEv.incrementLength();
            }

            if(ground.equals(this.m_nonMotifLabel)){
                if(nextGroundTruthEv.isNA()){
                    assert(this.m_lastGroundTruthLabel.get(muxIndex).equals(this.m_nonMotifLabel));
                    nextGroundTruthEv.incrementLength();
                }
                else{
                    assert(!ground.equals(this.m_lastGroundTruthLabel.get(muxIndex)));

                    // Move next ground truth event to list, add dependencies
                    this.finalizeAndAddEventToList(nextGroundTruthEv,
                            this.m_nextStartIndexGroundTruthEvents.get(muxIndex),
                            this.m_groundTruthEvents.get(muxIndex),
                            this.m_detectedEvents.get(muxIndex));

                    this.m_nextStartIndexGroundTruthEvents.set(muxIndex,
                            this.m_nextStartIndexGroundTruthEvents.get(muxIndex) + nextGroundTruthEv.getLength());
                    this.m_nextGroundTruthEvent.set(muxIndex, new Event(true, true, muxIndex));
                }
            }
            else if(!ground.equals(this.m_lastGroundTruthLabel.get(muxIndex))){
                // Move next ground truth event to list, add dependencies
                this.finalizeAndAddEventToList(nextGroundTruthEv,
                        this.m_nextStartIndexGroundTruthEvents.get(muxIndex),
                        this.m_groundTruthEvents.get(muxIndex),
                        this.m_detectedEvents.get(muxIndex));

                this.m_nextStartIndexGroundTruthEvents.set(muxIndex,
                        this.m_nextStartIndexGroundTruthEvents.get(muxIndex) + nextGroundTruthEv.getLength());
                this.m_nextGroundTruthEvent.set(muxIndex, new Event(false, true, muxIndex));
            }
            else {
                assert(!nextGroundTruthEv.isNA());
                nextGroundTruthEv.incrementLength();
            }
        }
        this.m_lastGroundTruthLabel.set(muxIndex, ground);
        this.m_lastDataLabel.set(muxIndex, data);

        boolean updatedF1 = this.updateFields(muxIndex);
        this.deleteOldEvents(muxIndex);

        if(updatedF1) {
            this.updateF1s();
            assert(this.m_startTime >= 0);
            long currRuntime = System.currentTimeMillis() - this.m_startTime;
            String msg = currRuntime + ", " + DurationFormatUtils.formatDuration(currRuntime, "HH:mm:ss:SSS") + ", " +
                    dataElement.getTimestamp() + ", " +
                    DurationFormatUtils.formatDuration(dataElement.getTimestamp(), "HH:mm:ss:SSS") + ", " +
                    this.m_dataNo + ", " +
                    this.m_f1PositivesPrecision + ", " + this.m_f1PositivesRecall + ", " + this.m_f1Positives + ", " +
                    this.m_f1NegativesPrecision + ", " + this.m_f1NegativesRecall + ", " + this.m_f1Negatives + ", " +
                    this.m_overallF1;

            LOGGER.finer("F1 stats: " + msg);

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
