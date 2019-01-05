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

import org.apache.commons.lang3.tuple.Triple;

import gs.utils.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

public interface MotifSearch {

    Comparator<Triple<Integer, Integer, Double>> START_INDEX_COMPARATOR = new Comparator<Triple<Integer, Integer, Double>>() {
        @Override
        public int compare(Triple<Integer, Integer, Double> o1, Triple<Integer, Integer, Double> o2) {
            if(o1 == o2){
                return 0;
            }
            if(o1.getLeft() < o2.getLeft()){
                return -1;
            }
            else if(o1.getLeft() > o2.getLeft()){
                return 1;
            }
            else{
                assert(o1.getLeft().equals(o2.getLeft()));
                return 0;
            }
        }
    };

    // Each entry corresponds to one motif. Left entry: Start index w.r.t. dataVector, Middle: Motif length, Right: Motif "un-fitness"
    // The latter is implementation dependent -> a small value shall indicate a "good" motif
    ArrayList<Triple<Integer, Integer, Double>> findMotives(double[] dataVector) throws InterruptedException;
    String[] findMotivesAsStringArray(double[] dataVector) throws InterruptedException;

    public default String[] convertSolutionToStringArray(List<Triple<Integer, Integer, Double>> numericSolution, int totalDataLength){
        return MotifSearch.convertSolutionToStringArray(
        		numericSolution, 
        		totalDataLength, 
        		this.getMotifBeginLabel(), 
        		this.getInsideMotifLabel(), 
        		this.getNonMotifLabel());
    }
    
    // Assumes 'numericSolution' to be sorted ascendingly w.r.t. start index (left entry),
    // to have a length of at least 2 (middle entry),
    // to consist of non overlapping elements and not to supercede totalDataLength.
    // Throws IllegalArgumentException, if that is not the case
    static String[] convertSolutionToStringArray(
            List<Triple<Integer, Integer, Double>> numericSolution,
            int totalDataLength,
            String motifBeginLabel,
            String insideMotifLabel,
            String nonMotifLabel){
        if(numericSolution == null || totalDataLength < 0 || motifBeginLabel == null ||
        		insideMotifLabel == null || nonMotifLabel == null) {
        	throw new IllegalArgumentException();
        }

        String[] result = new String[totalDataLength];
        Iterator<Triple<Integer, Integer, Double>> it = numericSolution.iterator();
        Triple<Integer, Integer, Double> currentCand = null, lastCand = null;
        boolean insideMotif = false;

        for(int i = 0; i < totalDataLength; i++){
            if(it.hasNext() && currentCand == null) {
                currentCand = it.next();
                if (currentCand.getLeft() < i ||
                        (lastCand != null && (currentCand.getLeft() < lastCand.getLeft() + lastCand.getMiddle()) ||
                        currentCand.getLeft() >= totalDataLength - currentCand.getMiddle() + 1 ||
                        currentCand.getMiddle() < 2)) {
                	throw new IllegalArgumentException("Inconsistent numericSolution. Latter must be sorted ascendingly "
                			+ " w.r.t. start index, " + 
                			" consist of non overlapping elements and not supercede totalDataLength.");
                }

            }

            if (currentCand != null && i == currentCand.getLeft()) {
                result[i] = motifBeginLabel;
                if(currentCand.getMiddle() > 1) {
                    insideMotif = true;
                }
                else{
                    assert(currentCand.getMiddle() == 1);
                    insideMotif = false;
                    lastCand = currentCand;
                    currentCand = null;
                }
            } else if (currentCand != null && i == currentCand.getLeft() + currentCand.getMiddle() - 1) {
                assert (insideMotif);
                assert(currentCand.getMiddle() > 1);
                result[i] = insideMotifLabel;
                lastCand = currentCand;
                currentCand = null;
                insideMotif = false;
            }
            else if(insideMotif){
                assert(currentCand != null);
                result[i] = insideMotifLabel;
            }
            else{
                result[i] = nonMotifLabel;
            }
        }
        assert(!insideMotif);
        assert(!it.hasNext());
        return result;
    }
    
    // In/Out: Triple = (start index, length, un-fitness)
    // For all overlapping motif sets (i.e. a partition where two candidates go into the same partition if (but NOT only if)
    //   they overlap (or, equivalently: two candidates go into the same partition if and only if there is a sequence of candidates starting with
    //   the first candidate and ending with the second one where each two adjacent candidates overlap)).
    // keep the single candidate from that set with the lowest un-fitness.
    // In case of ambiguities, keep the longest one. If there are still ambiguities, keep the leftmost one. No ambiguities possible
    // after that.
    // Note: Input does not have to be sorted in any manner, Output sorted ascendingly w.r.t. start index (left entry)
    static ArrayList<Triple<Integer, Integer, Double>> mergeMotifCandidates(
            ArrayList<Triple<Integer, Integer, Double>> candidates){
        // For overlapping motifs, keep the one with the lowest un-fitness:
        if(candidates == null) {
        	throw new NullPointerException();
        }
        
        Collections.sort(candidates, START_INDEX_COMPARATOR);
        
        Triple<Integer, Integer, Double> currentCandidate;

        int numCandidates = candidates.size();

        List<SortedSet<Triple<Integer, Integer, Double>>> overlapSets = new LinkedList<>();
        SortedSet<Triple<Integer, Integer, Double>> overlapSet;
        for(int i = 0; i < numCandidates; i++){
            assert(i == numCandidates - 1 || candidates.get(i).getLeft() <= candidates.get(i+1).getLeft());

            overlapSet = AbstractMotifSearch.getOverlapSet(i, candidates);
            assert(overlapSet.size() >= 1);
            assert (Structure.setEquals(candidates.subList(i, i+overlapSet.size()), overlapSet));
            i = i + overlapSet.size() - 1;

            overlapSets.add(overlapSet);
        }
        assert(Structure.isPartitioning(new HashSet<>(overlapSets), new HashSet<>(candidates)));

        ArrayList<Triple<Integer, Integer, Double>> result = new ArrayList<>(overlapSets.size());

        for(Set<Triple<Integer, Integer, Double>> oSet : overlapSets){
            currentCandidate = null;
            assert(oSet.size() > 0 && oSet.size() <= candidates.size());
            for(Triple<Integer, Integer, Double> cand : oSet){
                if(currentCandidate == null){
                    currentCandidate = cand;
                }
                else if(cand.getRight() < currentCandidate.getRight()){
                    currentCandidate = cand;
                }
                else if(Objects.equals(cand.getRight(), currentCandidate.getRight())) {
                    if(cand.getMiddle() > currentCandidate.getMiddle()) {
                        currentCandidate = cand; // Keep longest in case of ambiguities
                    }
                    else if(Objects.equals(cand.getMiddle(), currentCandidate.getMiddle())){
                        if(cand.getLeft() < currentCandidate.getLeft()){
                            currentCandidate = cand; // Keep leftmost (aka.: oldest) in case of ambiguities
                        }
                    }
                }
            }
            assert(currentCandidate != null);
            assert(result.size() == 0 || result.get(result.size() - 1).getLeft() < currentCandidate.getLeft());
            result.add(currentCandidate);
        }
        assert(result.size() == overlapSets.size());
        return result;
    }
    
    String getMotifBeginLabel();
    String getInsideMotifLabel();
    String getNonMotifLabel();


    boolean close();
    boolean isClosed();

}
