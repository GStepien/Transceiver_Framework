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

import java.util.*;
import java.util.logging.Logger;

public abstract class AbstractMotifSearch implements MotifSearch {

    private static final Logger LOGGER = Logger.getLogger(AbstractMotifSearch.class.getName());

    protected final String m_motifBeginLabel;
    protected final String m_insideMotifLabel;
    protected final String m_nonMotifLabel;

    private boolean m_closed;

    public AbstractMotifSearch(String motifBeginLabel,
                               String insideMotifLabel,
                               String nonMotifLabel){
        if(motifBeginLabel == null || insideMotifLabel == null || nonMotifLabel == null){
            throw new NullPointerException();
        }

        if(motifBeginLabel.equals(insideMotifLabel) || motifBeginLabel.equals(nonMotifLabel) || insideMotifLabel.equals(nonMotifLabel)){
            throw new IllegalArgumentException();
        }

        this.m_motifBeginLabel = motifBeginLabel;
        this.m_insideMotifLabel = insideMotifLabel;
        this.m_nonMotifLabel = nonMotifLabel;
        this.m_closed = false;
    }
    
    // Assumes candidates to be sorted ascendingly w.r.t. start indices (left entry)
    static SortedSet<Triple<Integer, Integer, Double>> getOverlapSet(
            int startIndex,
            ArrayList<Triple<Integer, Integer, Double>> candidatesArray){
        assert(candidatesArray != null);
        int numCandidates = candidatesArray.size();

        assert(startIndex >= 0 && startIndex < numCandidates);

        SortedSet<Triple<Integer, Integer, Double>> result = new TreeSet<>(START_INDEX_COMPARATOR);
        Triple<Integer, Integer, Double> currentCandidate, nextCandidate;
        currentCandidate = candidatesArray.get(startIndex);
        assert(currentCandidate != null);
        result.add(currentCandidate);

        int currentLastIndex = currentCandidate.getLeft() + currentCandidate.getMiddle() - 1;

        for(int i = startIndex + 1; i < numCandidates; i++){
            nextCandidate = candidatesArray.get(i);
            assert(nextCandidate != null);
            assert(nextCandidate.getLeft() >= currentCandidate.getLeft());
            if(nextCandidate.getLeft() <= currentLastIndex){
                result.add(nextCandidate);
                currentLastIndex = Math.max(currentLastIndex, nextCandidate.getLeft() + nextCandidate.getMiddle() - 1);
            }
            else {
                break;
            }
            currentCandidate = nextCandidate;
        }
        return result;
    }

    @Override
    public ArrayList<Triple<Integer, Integer, Double>> findMotives(double[] dataVector) throws InterruptedException{
        if(dataVector == null){
            throw new NullPointerException();
        }
        if(dataVector.length == 0){
            return new ArrayList<>();
        }

        SortedSet<Triple<Integer, Integer, Double>> candidates = this.getMotifCandidates(dataVector);
        if(candidates == null || candidates.comparator() != START_INDEX_COMPARATOR){
            throw new IllegalStateException();
        }

        ArrayList<Triple<Integer, Integer, Double>> result = MotifSearch.mergeMotifCandidates(new ArrayList<>(candidates));
        if(result == null){
            throw new IllegalStateException();
        }
        if(!candidates.containsAll(result)){
            throw new IllegalStateException("Merged candidates must be a subset of pre-merge candidates.");
        }
        Triple<Integer, Integer, Double> candidate;
        for(int i = 0; i < (result.size() - 1); i++){
            candidate = result.get(i);
            if(candidate == null){
                throw new NullPointerException();
            }
            if(candidate.getLeft() >= result.get(i+1).getLeft()){
                throw new IllegalStateException();
            }
        }

        LOGGER.info("Final motif solution after merging has "+result.size()+" elements.");
        assert(this.convertSolutionToStringArray(result, dataVector.length).length == dataVector.length);

        return(result);
    }

    protected abstract SortedSet<Triple<Integer, Integer, Double>> getMotifCandidates(double[] dataVector) throws InterruptedException;

    @Override
    public String[] findMotivesAsStringArray(double[] dataVector) throws InterruptedException {
        String[] result = this.convertSolutionToStringArray(this.findMotives(dataVector), dataVector.length);
        assert(result.length == dataVector.length);
        return(result);
    }

    @Override
    public String getMotifBeginLabel() {
        return this.m_motifBeginLabel;
    }

    @Override
    public String getInsideMotifLabel() {
        return this.m_insideMotifLabel;
    }

    @Override
    public String getNonMotifLabel() {
        return this.m_nonMotifLabel;
    }

    @Override
    public boolean close() {
        if(this.m_closed){
            return true;
        }
        else{
            if(!this.close2()){
               return false;
            }
            else{
                this.m_closed = true;
                return true;
            }
        }
    }

    protected abstract boolean close2();

    @Override
    public boolean isClosed() {
        return this.m_closed;
    }
}
