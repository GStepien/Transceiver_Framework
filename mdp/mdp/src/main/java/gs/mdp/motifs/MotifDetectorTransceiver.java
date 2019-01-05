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

package gs.mdp.motifs;

import gs.utils.datatypes.DoubleData;
import gs.utils.datatypes.GenericStringData;
import gs.utils.datatypes.LabeledTimestampedData;
import gs.utils.datatypes.StringData;
import gs.tf.core.AbstractTransceiverTask;
import gs.utils.MathUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class MotifDetectorTransceiver extends AbstractTransceiverTask<LabeledTimestampedData<?>, StringData> {

    private static final Logger LOGGER = Logger.getLogger(MotifDetectorTransceiver.class.getName());

    private final ArrayList<DoubleData> m_buffer;
    private final int m_bufferSize;
    private final int m_bufferPurgeSize;

    private final BlockingQueue<StringData> m_outData;

    private final MotifSearch m_motifSearch;
    private final MathUtils m_mathUtils;

    private final ArrayList<Triple<Integer, Integer, Double>> m_candidates; // Left: Motif buffer start index, Middle: Motif length, Right: Motif-"un-fitness"

    private final Long m_timeoutInterval;
    private boolean m_lastWasTimeout;    

    private long m_offset; // Data No. = position in buffer + offset

    public MotifDetectorTransceiver(
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int maxDataChunkSize,
            ExecutorService executorService,
            int bufferSize,
            double bufferPurgeFactor,
            Integer outQueueCapacity,
            MotifSearch motifSearch,
            MathUtils mathUtils) {
        super(inDataQueueCapacity, timeoutInterval, maxDataChunkSize, executorService);

        this.m_timeoutInterval = timeoutInterval;
        this.m_lastWasTimeout = false;
        this.m_offset = 1;
        if(bufferSize < 1 || bufferPurgeFactor <= 0 || bufferPurgeFactor > 1){
            throw new IllegalArgumentException();
        }
        if(motifSearch == null || mathUtils == null){
            throw new IllegalArgumentException();
        }

        if(outQueueCapacity == null){
            this.m_outData = new LinkedBlockingQueue<>();
        }
        else{
            if(inDataQueueCapacity < 1){
                throw new IllegalArgumentException();
            }

            this.m_outData = new ArrayBlockingQueue<>(outQueueCapacity, true);
        }

        // Motif candidates sorted w.r.t. timestamp
        this.m_candidates = new ArrayList<>(bufferSize);

        this.m_buffer = new ArrayList<>(bufferSize);
        this.m_bufferSize = bufferSize;

        this.m_bufferPurgeSize = Math.max(1, (int)Math.ceil(bufferPurgeFactor * this.m_bufferSize));
        assert(this.m_bufferPurgeSize >= 1 && this.m_bufferPurgeSize <= this.m_bufferSize);

        this.m_motifSearch = motifSearch;
        this.m_mathUtils = mathUtils;
    }
    private void purgeOldestBufferEntries(int endIndexExcl){
        assert(endIndexExcl >= 0 &&
                this.m_buffer.size() >= endIndexExcl);
        if (endIndexExcl > 0) {
            this.m_buffer.subList(0, endIndexExcl).clear();
        }
        this.m_offset +=endIndexExcl;
    }

    private StringData createOutData(String motifLabel, DoubleData dataElement, int index) {
        StringData newData;

        assert(motifLabel.equals(this.m_motifSearch.getNonMotifLabel()) ||
                motifLabel.equals(this.m_motifSearch.getInsideMotifLabel()) ||
                motifLabel.equals(this.m_motifSearch.getMotifBeginLabel()));

        assert(this.m_buffer.get(index) == dataElement);
        /* Out commented version below sets the detected label to the ground truth label and ignores the motifs
           from the actual detection - USE ONLY FOR DEBUG PURPOSES!!! (i.e., debugging the language model
           by checking prediction error if learned with ground truth)
        if(index == 0){
            newData = new StringData(dataElement.getTimestamp(),
                    ((LabeledTimestampedData)dataElement).getLabel(), // Add ground truth as label for F1 measure
                    motifLabel);
        }
        else {
            String currentGroundTruth = ((DoubleData)dataElement).getLabel();
            String previousGroundTruth = ((DoubleData)this.m_buffer.get(index-1)).getLabel();

            if(!currentGroundTruth.equals(this.m_motifSearch.getNonMotifLabel())){
                if(!currentGroundTruth.equals(previousGroundTruth)){
                    newData = new StringData(
                            dataElement.getTimestamp(),
                            ((LabeledTimestampedData)dataElement).getLabel(), // Add ground truth as label for F1 measure
                            this.m_motifSearch.getMotifBeginLabel());
                }
                else{
                    newData = new StringData(dataElement.getTimestamp(),
                            ((LabeledTimestampedData)dataElement).getLabel(), // Add ground truth as label for F1 measure
                            this.m_motifSearch.getInsideMotifLabel());
                }
            }
            else{
                newData = new StringData(dataElement.getTimestamp(),
                        ((LabeledTimestampedData)dataElement).getLabel(), // Add ground truth as label for F1 measure
                        this.m_motifSearch.getNonMotifLabel());
            }
        }
        return(newData);*/

        newData = new GenericStringData(dataElement.getTimestamp(),
        		dataElement.getLabel(), // Add ground truth as label for F1 measure
        		motifLabel);

        return newData;
    }

    private boolean checkForOverlaps(ArrayList<Triple<Integer, Integer, Double>> motifCandidates){
        assert(motifCandidates != null);
        Triple<Integer, Integer, Double> currentCand;
        for(int i = 0; i < motifCandidates.size(); i++){
            currentCand = motifCandidates.get(i);
            assert(currentCand.getMiddle() >= 1);
            assert(currentCand.getLeft() >= 0 && currentCand.getLeft() + currentCand.getMiddle() - 1 < this.m_buffer.size());

            if(i < motifCandidates.size() - 1){
                assert(currentCand.getLeft() < motifCandidates.get(i+1).getLeft());
                if(currentCand.getLeft() + currentCand.getMiddle() - 1 >= motifCandidates.get(i+1).getLeft()) {
                    return true;
                }
            }
        }

        return false;
    }

    // TODO: Clean up this method -> divide into submethods
    @Override
    protected void processDataElement(LabeledTimestampedData<?> doubleDataElement) throws InterruptedException {
        if(doubleDataElement == null ){
            throw new NullPointerException();
        }
        DoubleData dataElement;
        if(doubleDataElement instanceof DoubleData){
            dataElement = (DoubleData) doubleDataElement;
        }
        else{
            throw new IllegalArgumentException("The LabeledTimestampedData<?> instance must be an instance of DoubleData.");
        }


        assert(this.m_buffer.size() == 0 || this.m_buffer.get(this.m_buffer.size() - 1).getTimestamp() <= dataElement.getTimestamp());

        if(this.m_buffer.size() < this.m_bufferSize){
            this.m_buffer.add(dataElement);
            if(this.m_buffer.size() % 100 == 0){
                LOGGER.info("Motif detector transceiver (ID: "+this.getID()+") buffer: "+this.m_buffer.size() +" / "+this.m_bufferSize + " elements.");
            }
        }
        else{
            assert(this.m_buffer.size() == this.m_bufferSize);

            // Find motives - no biggie
            // Left Motif begin index, middle: motif length, right: matrix profile value
            int oldCandidateNum = this.m_candidates.size();
            ArrayList<Triple<Integer, Integer, Double>> newMotifs, result;
            newMotifs = this.m_motifSearch.findMotives(this.m_mathUtils.toDoubleArray(this.m_buffer));
            if(newMotifs == null){
                throw new NullPointerException();
            }
            assert(!this.checkForOverlaps(newMotifs));
            assert(!this.checkForOverlaps(this.m_candidates));
            int newMotifsNum = newMotifs.size();

            // 1. Merge result with current candidates
            SortedSet<Triple<Integer, Integer, Double>> mergedCands = new TreeSet<>(MotifSearch.START_INDEX_COMPARATOR);
            mergedCands.addAll(newMotifs);
            mergedCands.addAll(this.m_candidates);
            // Merger expects candidates sorted w.r.t. start index (i.e., left triple entry)
            result = MotifSearch.mergeMotifCandidates(new ArrayList<>(mergedCands));
            assert(mergedCands.containsAll(result));

            assert (this.m_candidates.size() == oldCandidateNum);
            this.m_candidates.removeAll(result);
            int numOldDiscard = this.m_candidates.size();
            assert(newMotifs.size() == newMotifsNum);
            newMotifs.removeAll(result);
            int numNewDiscard = newMotifs.size();

            this.m_candidates.clear();
            this.m_candidates.addAll(result);

            assert(!this.checkForOverlaps(this.m_candidates));

            // 2. Emit all motifs starting at index < purge size
            int latestMotifEnd = -1;
            ArrayList<Triple<Integer, Integer, Double>> emitList = new ArrayList<>(this.m_candidates.size());
            for (Triple<Integer, Integer, Double> candidate : this.m_candidates) {
                if (candidate.getLeft() < this.m_bufferPurgeSize) {
                    emitList.add(candidate);
                    assert (latestMotifEnd < candidate.getLeft());
                    latestMotifEnd = candidate.getLeft() + candidate.getMiddle() - 1;
                } else {
                    break;
                }
            }
            LOGGER.info("Motif search yielded "+newMotifsNum+" new motif candidates.\nDiscarded "+ numOldDiscard + " old motif candidates and " +
                    numNewDiscard + " new candidates.\nOld candidate set size is "+ oldCandidateNum + " and new candidate set size is "+
                    this.m_candidates.size()+".\nCurrent offset: "+this.m_offset+"\nEmitting "+emitList.size()+" motifs: "+Arrays.toString(emitList.toArray()));

            assert(latestMotifEnd < this.m_buffer.size());
            String[] motifString = this.m_motifSearch.convertSolutionToStringArray(emitList, latestMotifEnd + 1);
            assert(motifString.length == latestMotifEnd + 1);

            if(latestMotifEnd >= 0) { // Motifs found -> emit them
                if(this.m_timeoutInterval == null) {
                    for (int i = 0; i <= latestMotifEnd; i++) {
                        this.m_outData.put(this.createOutData(
                                motifString[i],
                                this.m_buffer.get(i),
                                i));
                    }
                }
                else{
                    boolean timeout = this.m_lastWasTimeout;
                    int offered = 0;
                    StringData data;
                    for(int i = 0; i <= latestMotifEnd; i++) {
                    	data = this.createOutData(
                                motifString[i],
                                this.m_buffer.get(i),
                                i);
                    	if(this.m_outData.offer(data)) {
                    		timeout = false;
                    	}
                    	else if(!this.m_lastWasTimeout) {
                    		timeout = !this.m_outData.offer(
                    				data,
                    				this.m_timeoutInterval, TimeUnit.MILLISECONDS);
                    	}
                    	
                    	if(!timeout) {
                    		offered++;
                    	}
                    	else {
                            LOGGER.warning("Discarding "+(latestMotifEnd + 1 - offered)+ " output elements due to timeout in "+
                                    this.getClass().getName()+", running in thread with ID "+this.getRunThreadID()+" and name "+
                                    this.getRunThreadName()+ ".");
                            break;
                        }
                    }
                    this.m_lastWasTimeout = timeout;
                }
            }

            // 3. Remove max(purge_size, index of element corresponding to last element of latest emitted motif) elements from buffer
            //    and update start index of remaining candidates
            Triple<Integer, Integer, Double> candidate;
            for (Triple<Integer, Integer, Double> candFromEmitList : emitList) {
                candidate = this.m_candidates.remove(0);
                assert (candidate == candFromEmitList);
            }
            int numToPurge = Math.max(this.m_bufferPurgeSize, latestMotifEnd + 1);
            assert(this.m_candidates.size() == 0 ||
                    (this.m_candidates.get(0).getLeft() >= numToPurge));
            if(this.m_timeoutInterval == null) {
                for (int i = latestMotifEnd + 1; i < numToPurge; i++) {
                    this.m_outData.put(this.createOutData(
                            this.m_motifSearch.getNonMotifLabel(),
                            this.m_buffer.get(i),
                            i));
                }
            }
            else{
                boolean timeout = this.m_lastWasTimeout;
                int offered = 0;
                StringData data;
                for (int i = latestMotifEnd + 1; i < numToPurge; i++) {
                	data = this.createOutData(
                            this.m_motifSearch.getNonMotifLabel(),
                            this.m_buffer.get(i),
                            i);
                	if(this.m_outData.offer(data)) {
                		timeout = false;
                	}
                	else if(!this.m_lastWasTimeout) {
                		timeout = !this.m_outData.offer(data,
                            this.m_timeoutInterval, TimeUnit.MILLISECONDS);
                	}
                	
                	if(!timeout){
                		offered++;
                	}
                	else {
                        LOGGER.warning("Discarding "+(this.m_bufferPurgeSize - latestMotifEnd - 1 - offered)+ " output elements due to timeout in "+
                                this.getClass().getName()+", running in thread with ID "+this.getRunThreadID()+" and name "+
                                this.getRunThreadName()+ ".");
                        break;
                    }
                }
                this.m_lastWasTimeout = timeout;
            }
            assert(this.m_buffer.size() == this.m_bufferSize);

            if(numToPurge > 0) {
                this.purgeOldestBufferEntries(numToPurge);
                assert (this.m_buffer.size() == this.m_bufferSize - numToPurge);
                // Update start indices
                for (int i = 0; i < this.m_candidates.size(); i++) {
                    candidate = this.m_candidates.get(i);
                    assert (candidate.getLeft() - numToPurge >= 0);
                    this.m_candidates.set(i, new ImmutableTriple<>(
                            candidate.getLeft() - numToPurge,
                            candidate.getMiddle(),
                            candidate.getRight()));
                }
            }

            // 5. Add new data point
            assert(this.m_buffer.size() < this.m_bufferSize);
            this.m_buffer.add(dataElement);
        }
    }

    @Override
    protected Collection<StringData> getNextDataChunk() {
        List<StringData> dataChunk = new LinkedList<>();
        this.m_outData.drainTo(dataChunk, this.getMaxDataChunkSize());

        return dataChunk;
    }

    @Override
    protected void midPostWork(){

        super.midPostWork();
        assert(this.terminateCalledWithInterrupt() != null);
        if(this.terminateCalledWithInterrupt()) {
            ArrayList<StringData> remainingData = new ArrayList<>(this.m_outData.size());
            this.m_outData.drainTo(remainingData);
            if (this.m_candidates.size() + remainingData.size() > 0) {
                LOGGER.warning("Discarding " + (this.m_candidates.size() + remainingData.size()) +
                        " motif candidates due to interrupt-termination.");
            }
        }
        else{
            String[] motifString = this.m_motifSearch.convertSolutionToStringArray(this.m_candidates, this.m_buffer.size());
            for(int i = 0; i < motifString.length; i++) {
                try {
                    this.m_outData.put(this.createOutData(
                            motifString[i],
                            this.m_buffer.get(i),
                            i));

                } catch (InterruptedException e) {
                    throw new IllegalStateException("No interrupt should happen at this point.", e);
                }
            }

            while(this.m_outData.size() > 0){
                Thread.yield(); // Internal Transmitters (which are still running) should forward the data via calls to get_next_chunk
//                try {
//
//                    this.forwardToReceiver(col);
//                } catch (InterruptedException e) {
//                    throw new IllegalStateException("No interrupt should happen at this point.", e);
//                } catch (IllegalStatusException e) {
//                    throw new RuntimeException("Should not happen here.", e);
//                }
            }
        }
        assert(this.m_outData.isEmpty());
    }

    @Override
    protected void postWork() {
        super.postWork(); // Terminates internal receiver and transmitter

        if(!this.m_mathUtils.close()){
            throw new IllegalStateException("Could not close math interface connection.");
        }

        if(!this.m_motifSearch.close()){
            throw new IllegalStateException("Could not close motif search interface connection.");
        }
    }
}
