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

import gs.mdp.utils.r.SimpleConcurrentR;
import gs.utils.json.JSONTypedObject;
import gs.utils.r.SimpleRInterface;
import gs.utils.Concurrency;
import gs.utils.MathUtils;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

// TODO Incremental MP!!!
public class MatrixProfileMotifSearch extends AbstractMotifSearch implements MotifSearch {

    // Filter matrix profile with...
    public enum FilterMode{
        NONE, // No filter
        GAUSS, // Gauss filter with sigma = window length
        NORM_LOG // Scale normalized (i.e., multiplied by sigma squared) laplacian of gaussians with sigma = window length
    }

    private final FilterMode m_filterMode;

    private static final Logger LOGGER = Logger.getLogger(MatrixProfileMotifSearch.class.getName());

    private final double m_mpHysteresisMinThreshold;
    private final double m_mpHysteresisMaxThreshold;
    final int[] m_motifLengths;

    // For each matrix profile motif candidate finder
    private final UnmodifiableList<MathUtils> m_mathUtils;

    private final double m_exclusionZoneFactor;
    private final SimpleRInterface m_rInterface;
    private final boolean m_visualizeMP;
    private final JSONTypedObject m_visualizationJSON;
    private final String m_firstMPPlotOutPath;
    private AtomicLong m_nextVisualizeBeginIndex;

    private final ExecutorService m_executorService;

    private final long m_mpSearchId;
    // Triple stores: (filter, factor, offset)
    private final ConcurrentHashMap<FilterMode, ConcurrentHashMap<Integer, ImmutableTriple<double[], Double, Double>>> m_filterModeToWindowLengthToFilterMap;

    private class MatrixProfileMotifCandidateFinder implements Callable<List<Triple<Integer, Integer, Double>>> {

        private final int m_exclusionZoneRadius;
        private final double[] m_data;
        private final int m_windowLength;
        private final MathUtils m_mathUtils;
        private final double m_mpHysteresisMinThreshold;
        private final double m_mpHysteresisMaxThreshold;
        private final int m_motifNo;
        private final FilterMode m_filterMode;

        public MatrixProfileMotifCandidateFinder(double[] data,
                                                 int windowLength,
                                                 double exclusionZoneFactor,
                                                 MathUtils mathUtils,
                                                 double mpHysteresisMinThreshold,
                                                 double mpHysteresisMaxThreshold,
                                                 int motifNo,
                                                 FilterMode filterMode){
            assert(filterMode != null);
            assert(mpHysteresisMinThreshold >= 0
                    && mpHysteresisMinThreshold <= mpHysteresisMaxThreshold
                    && mpHysteresisMaxThreshold <= 2);
            assert(motifNo >= 0 && motifNo < MatrixProfileMotifSearch.this.m_motifLengths.length);
            int exclusionZoneRadius = (int)Math.floor(exclusionZoneFactor * windowLength);
            assert(data != null && mathUtils != null);
            assert(data.length > 0);
            assert(windowLength >= 2 && // Required for variance computation
                    data.length >= windowLength &&
                    exclusionZoneRadius >= 0);

            this.m_filterMode = filterMode;
            this.m_exclusionZoneRadius = exclusionZoneRadius;
            this.m_data = data;
            this.m_windowLength = windowLength;
            this.m_mathUtils = mathUtils;
            this.m_mpHysteresisMinThreshold = mpHysteresisMinThreshold;
            this.m_mpHysteresisMaxThreshold = mpHysteresisMaxThreshold;
            this.m_motifNo = motifNo;
        }

        // = 1 - r_i, with r_i being corr coeff of subsequence i with subseq starting at startIndex
        private void computeLengthNormDistances(double[] resultVector, int startIndex, int exclusionZone,
                                                int windowLength, double[] xy, double[] means,
                                                double[] vars){
            int mpLength = resultVector.length;
            for(int i = 0; i < mpLength; i++){
                if(i >= startIndex - exclusionZone && i <= startIndex + exclusionZone){
                    resultVector[i] = Double.POSITIVE_INFINITY;
                }
                else{
                    resultVector[i] = 1 - (xy[i] - windowLength * means[startIndex] * means[i]) / (windowLength * Math.sqrt(vars[startIndex] * vars[i]));
                }
            }
        }

        private boolean mpHasExpectedValues(double[] matrixProfile){
            assert(matrixProfile != null);
            for(double value : matrixProfile){
                if(value < 0 || value > 2){
                    return false;
                }
            }

            return true;
        }

        private double[] computeLengthNormMatrixProfile() throws Exception {

            double[] matrixProfile = new double[this.m_data.length - this.m_windowLength + 1];
            int mpLength = matrixProfile.length;
            assert(mpLength > 0);
            double[] currentDistances = new double[mpLength];
            double[] temp;

            if(mpLength == 1){
                matrixProfile[0] = 0;
                return(matrixProfile);
            }
            else if(this.m_exclusionZoneRadius + 1 >= mpLength){
                // Does at least one subsequence outside of exclusion zone of first one exist
                // to which we can compare z-dist?
                // Note: First index = 0 => fist index outside of exclusion zone = 0 + exclusionZone + 1
                throw new IllegalArgumentException("Too large exclusion zone.");
            }

            int startIndex = 0;
            int endIndexExcl = this.m_windowLength;
            double[] means = new double[mpLength];
            double[] vars = new double[mpLength];

            means[0] = this.m_mathUtils.mean(this.m_data, startIndex, endIndexExcl);
            vars[0] = this.m_mathUtils.variance(this.m_data, startIndex, endIndexExcl);

            for(int i = 1; i < mpLength; i++){
                means[i] = means[i-1] + (this.m_data[i + this.m_windowLength - 1] - this.m_data[i - 1]) / this.m_windowLength;
                vars[i] = vars[i-1] +
                        (Math.pow(this.m_data[i + this.m_windowLength - 1], 2) - Math.pow(this.m_data[i - 1], 2)) / this.m_windowLength +
                        (Math.pow(means[i-1], 2) - Math.pow(means[i], 2));
            }

            for(int i = 0; i < mpLength; i++){
                if(vars[i] <= 0){
                    LOGGER.warning("Variance <= 0 encountered. Setting variance to "+MathUtils.EPS);
                    vars[i] = MathUtils.EPS;
                }
            }

            double[] xy_first = this.m_mathUtils.correlateFFT_1D(
                    this.m_data, 0, this.m_data.length,
                    this.m_data, startIndex, endIndexExcl,
                    1, 0);
            double[] xy = Arrays.copyOf(xy_first, xy_first.length);
            assert(xy_first.length == xy.length);

            this.computeLengthNormDistances(matrixProfile,
                    0, this.m_exclusionZoneRadius,
                    this.m_windowLength, xy, means, vars);

            int startIndex2, endIndexExcl2;
            for(startIndex = 1, endIndexExcl = this.m_windowLength + 1;
                startIndex < mpLength;
                startIndex++, endIndexExcl++){
                assert(endIndexExcl - startIndex == this.m_windowLength);

                for(startIndex2 = mpLength - 1, endIndexExcl2 = this.m_data.length;
                    startIndex2 >= 1; // the zero case is stored in xy_first
                    startIndex2--, endIndexExcl2--){
                    assert(endIndexExcl2 - startIndex2 == this.m_windowLength);

                    xy[startIndex2] = xy[startIndex2 - 1] -
                            this.m_data[startIndex - 1] * this.m_data[startIndex2 - 1] +
                            this.m_data[endIndexExcl - 1] * this.m_data[endIndexExcl2 - 1];

                }
                xy[0] = xy_first[startIndex];

                this.computeLengthNormDistances(currentDistances,
                        startIndex, this.m_exclusionZoneRadius,
                        this.m_windowLength, xy, means, vars);

                temp = this.m_mathUtils.pairwiseMin(matrixProfile, currentDistances, true);
                assert(temp == matrixProfile); // in situ!
            }

            for(int i = 0; i < mpLength; i++){
                if(matrixProfile[i] < 0){
                    LOGGER.warning("Negative matrix profile value: "+matrixProfile[i]+". Setting to zero.");
                    matrixProfile[i] = 0;
                }
                else if(matrixProfile[i] > 2){
                    LOGGER.warning("Matrix profile value > 2 encountered: "+matrixProfile[i]+". Setting to 2.");
                    matrixProfile[i] = 2;
                }
            }
            assert(this.mpHasExpectedValues(matrixProfile));

            return matrixProfile;
        }

        // Triple = (start index, length, "un-fitness" value)
        // TODO: NOTE: unfitness currently restricted to values from [0,2], this might be relaxed in future versions
        // Fitness is used in the merging phase.
        // Returned values are ordered w.r.t. start index (left entry)
        private List<Triple<Integer, Integer, Double>> findCandidates(double[] filteredMatrixProfile){
            assert(filteredMatrixProfile != null && filteredMatrixProfile.length >= 1 && this.m_windowLength >= 2);

            List<Triple<Integer, Integer, Double>> initialCandidates = new LinkedList<>();
            int mpLength = filteredMatrixProfile.length;

            // Exclude minima at beginning -> start at 1, end at mpLength - 2
            double bestMP;
            int bestIndex;
            for(int i = 1; i < mpLength - 1; i++) {
                if(filteredMatrixProfile[i] <= this.m_mpHysteresisMinThreshold){
                    bestMP = filteredMatrixProfile[i];
                    bestIndex = i;
                    // Find leftmost (oldest) global MINIMUM in hysteresis segment
                    for(i = i + 1; i < mpLength - 1 && filteredMatrixProfile[i] <= this.m_mpHysteresisMaxThreshold; i++){
                        if(bestMP > filteredMatrixProfile[i]){
                            bestMP = filteredMatrixProfile[i];
                            bestIndex = i;
                        }
                    }
                    // TODO: Maybe use length of hysteresis segment to refine motif length (i.e., window_length - length(hysteresis_segment))
                    assert(filteredMatrixProfile[i] >= 0 && filteredMatrixProfile[i] <= 2);
                    initialCandidates.add(new MutableTriple<>(bestIndex, this.m_windowLength, filteredMatrixProfile[i]));
                    // Now we are either done (i == mpLength - 1) or we know that matrixProfile[i] > max threshold
                    // -> in the latter case, next outer loop will increment i
                }
            }
            return new ArrayList<>(initialCandidates);
        }

        // Overwrite this method in order to change or add additional filter
        protected double[] filterMatrixProfile(double[] matrixProfile){
            double[] result;
            if(this.m_filterMode == FilterMode.NONE){
                result = matrixProfile;
            }
            else{
                int filterLength = Math.min(matrixProfile.length, (int)(3.0 * this.m_windowLength));
                double filterScale = this.m_windowLength / 2.0;
                try {
                    synchronized (MatrixProfileMotifSearch
                            .this
                            .m_filterModeToWindowLengthToFilterMap) {
                        if (!MatrixProfileMotifSearch
                                .this
                                .m_filterModeToWindowLengthToFilterMap
                                .containsKey(this.m_filterMode)) {
                            MatrixProfileMotifSearch
                                    .this
                                    .m_filterModeToWindowLengthToFilterMap
                                    .put(this.m_filterMode, new ConcurrentHashMap<>());
                        }
                    }

                    ImmutableTriple<double[], Double, Double> currentFilter;

                    // No need to synchronize further - there is only one candidate finder that has that specific
                    // filter mode and window length combination (also, internal and external map is Concurrent)
                    if(!MatrixProfileMotifSearch
                            .this
                            .m_filterModeToWindowLengthToFilterMap
                            .get(this.m_filterMode)
                            .containsKey(this.m_windowLength)){
                        switch (this.m_filterMode) {
                            case GAUSS: {
                                currentFilter = this.m_mathUtils.getGaussFilter(filterLength, filterScale);
                                assert(this.m_mathUtils.equalsWithinLimits(currentFilter.getMiddle(), 0));
                                assert(this.m_mathUtils.equalsWithinLimits(currentFilter.getRight(), 1));

                                MatrixProfileMotifSearch
                                        .this
                                        .m_filterModeToWindowLengthToFilterMap
                                        .get(this.m_filterMode)
                                        .put(this.m_windowLength,
                                                ImmutableTriple.of(currentFilter.getLeft(), 1.0, 0.0));
                                break;
                            }
                            case NORM_LOG: {
                                // TODO: Find better normalization!
                                currentFilter = this.m_mathUtils.getScaleNormLoGFilter(filterLength, filterScale);
                                double currentLowerFilteredBound = 2 * currentFilter.getMiddle();
                                double currentUpperFilteredBound = 2 * currentFilter.getRight();
                                double currentDiff = currentUpperFilteredBound - currentLowerFilteredBound;

                                if(currentDiff <= 0){
                                    throw new RuntimeException();
                                }

                                MatrixProfileMotifSearch
                                        .this
                                        .m_filterModeToWindowLengthToFilterMap
                                        .get(this.m_filterMode)
                                        .put(this.m_windowLength,
                                                ImmutableTriple.of(
                                                        currentFilter.getLeft(),
                                                        -2.0 / currentDiff,
                                                        2.0 * currentUpperFilteredBound / currentDiff));
                                break;
                            }
                            default: {
                                throw new IllegalStateException("Unknown filter mode: " + this.m_filterMode.toString());
                            }
                        }
                    }
                    currentFilter = MatrixProfileMotifSearch
                            .this
                            .m_filterModeToWindowLengthToFilterMap
                            .get(this.m_filterMode)
                            .get(this.m_windowLength);
                    assert(currentFilter != null);

                    result = this.m_mathUtils.correlateFFT_1D(
                            matrixProfile,
                            currentFilter.getLeft(),
                            currentFilter.getMiddle(),
                            currentFilter.getRight());
                }
                catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
            return result;
        }

        @Override
        public List<Triple<Integer, Integer, Double>> call() throws Exception {
            double[] matrixProfile = this.computeLengthNormMatrixProfile();
            assert(matrixProfile.length == this.m_data.length - this.m_windowLength + 1);

            MatrixProfileMotifSearch.this.visualizeMP(matrixProfile, this.m_motifNo, this.m_windowLength, FilterMode.NONE);

            double[] filteredMatrixProfile = this.filterMatrixProfile(matrixProfile);
            if(this.m_filterMode != FilterMode.NONE) {
                MatrixProfileMotifSearch.this.visualizeMP(filteredMatrixProfile, this.m_motifNo, this.m_windowLength, this.m_filterMode);
            }

            return this.findCandidates(filteredMatrixProfile);
        }
    }

    public MatrixProfileMotifSearch(int numMatrixProfiles,
                                    double mpHysteresisMinThreshold,
                                    double mpHysteresisMaxThreshold,
                                    int minMotifLength,
                                    int maxMotifLength,
                                    String motifBeginLabel,
                                    String insideMotifLabel,
                                    String nonMotifLabel,
                                    List<MathUtils> mathUtils,
                                    double exclusionZoneFactor,
                                    boolean visualizeMatrixProfile,
                                    Integer port,
                                    String visualizationJSONPath,
                                    FilterMode filterMode,
                                    ExecutorService executorService) throws Exception {
        super(motifBeginLabel, insideMotifLabel, nonMotifLabel);


        if((visualizeMatrixProfile && (port == null || visualizationJSONPath == null)) ||
                mathUtils == null || executorService == null || filterMode == null){
            throw new NullPointerException();
        }

        if(numMatrixProfiles < 1 || mpHysteresisMinThreshold < 0 || mpHysteresisMinThreshold > mpHysteresisMaxThreshold ||
                mpHysteresisMaxThreshold > 2 || exclusionZoneFactor < 0 || minMotifLength > maxMotifLength || minMotifLength < 2){
            throw new IllegalArgumentException();
        }

        if(mathUtils.size() != numMatrixProfiles){
            throw new IllegalArgumentException();
        }

        this.m_mathUtils = (UnmodifiableList<MathUtils>) 
        		UnmodifiableList.unmodifiableList(new ArrayList<>(mathUtils));
        for(int i = 0; i < numMatrixProfiles; i++){
            if(this.m_mathUtils.get(i) == null){
                throw new NullPointerException();
            }
        }

        this.m_filterMode = filterMode;
        this.m_filterModeToWindowLengthToFilterMap = new ConcurrentHashMap<>();
        this.m_mpHysteresisMinThreshold = mpHysteresisMinThreshold;
        this.m_mpHysteresisMaxThreshold = mpHysteresisMaxThreshold;
        this.m_exclusionZoneFactor = exclusionZoneFactor;
        this.m_visualizeMP = visualizeMatrixProfile;
        this.m_executorService = executorService;
        this.m_mpSearchId = Concurrency.getNextID(this.getClass().getName());

        if(this.m_visualizeMP){
            byte[] readAllBytes;
            try {
                readAllBytes = java.nio.file.Files.readAllBytes(Paths.get(visualizationJSONPath));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            String json_string = new String(readAllBytes);
            this.m_visualizationJSON = (new JSONTypedObject(json_string)).getJSONTypedObject("ds_visualization");
            this.m_rInterface = new SimpleConcurrentR(port);
            if(!this.m_rInterface.evalBoolean("length(ls()) == 0")) {
                throw new IllegalStateException("The R environment of the associated RServe instance is not empty.");
            }
            // Randomly initialize the .Random.seed field (may not exist by default when Rserve server started via console
            this.m_rInterface.evalVoid("set.seed(round((as.numeric(Sys.time()) * 1000) %% .Machine$integer.max))");

            String visualizationPath = this.m_visualizationJSON.getString("visualization_r_path");
            Path visPathAbsolute = Paths.get(visualizationPath).toAbsolutePath();
            this.m_rInterface.evalVoid("setwd(\""+visPathAbsolute.getParent().getParent().toString()+"\")");
            this.m_rInterface.evalVoid("source(\""+visPathAbsolute+"\")");
            this.m_rInterface.evalVoid("ait <- get_alphanum_iterators_env()");

            String firstOutPath = this.m_visualizationJSON.getString("first_plot_out_path");
            if(firstOutPath == null){
                this.m_firstMPPlotOutPath = null;
            }
            else{
                String extension = FilenameUtils.getExtension(firstOutPath);
                this.m_firstMPPlotOutPath = FilenameUtils.removeExtension(firstOutPath) + "_" + this.m_mpSearchId +
                        (extension.isEmpty() ? "" : "." + extension);
            }

        }
        else{
            this.m_visualizationJSON = null;
            this.m_rInterface = null;
            this.m_firstMPPlotOutPath = null;
        }

        int range = maxMotifLength - minMotifLength;

        List<Integer> motifLengthList = new ArrayList<>(numMatrixProfiles);
        int newLength;
        if(numMatrixProfiles == 1){
            motifLengthList.add((int)Math.round((minMotifLength + maxMotifLength) / 2.0));
        }
        else {
            for (int i = 0; i < numMatrixProfiles; i++) {
                newLength = minMotifLength + (int) Math.round((1.0 * i / (numMatrixProfiles - 1)) * range);

                assert(i == 0 || motifLengthList.get(i - 1) <= newLength);
                assert(i != 0 || newLength == minMotifLength);
                assert(i != numMatrixProfiles - 1 || newLength == maxMotifLength);

                if(i == 0 || newLength > motifLengthList.get(i - 1)){
                    motifLengthList.add(newLength);
                }
            }
        }

        int[] motifLengths = new int[motifLengthList.size()];
        for(int i = 0; i < motifLengths.length; i++){
            motifLengths[i] = motifLengthList.get(i);
        }
        assert(this.checkMotifLengths(motifLengths));
        assert(motifLengths.length <= numMatrixProfiles);
        if(motifLengths.length < numMatrixProfiles){
            assert(maxMotifLength - minMotifLength + 1 < numMatrixProfiles);
            LOGGER.warning("Number of matrix profile window lengths too large. Reduced from "+numMatrixProfiles+
                    " to "+motifLengths.length+ " in order to have distinct window lengths.");
        }
        this.m_motifLengths = motifLengths;

        this.m_nextVisualizeBeginIndex = new AtomicLong(0);
    }

    private boolean checkMotifLengths(int[] motifLengths){
        for (int motifLength : motifLengths) {
            if (motifLength < 2) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected SortedSet<Triple<Integer, Integer, Double>> getMotifCandidates(double[] dataVector) throws InterruptedException{
        SortedSet<Triple<Integer, Integer, Double>> candidates = new TreeSet<>(START_INDEX_COMPARATOR);
        assert(this.m_motifLengths.length > 0);
        List<Future<List<Triple<Integer, Integer, Double>>>> motifSearchTasks = new ArrayList<>(this.m_motifLengths.length);
        for(int motifNo = 0; motifNo < this.m_motifLengths.length; motifNo++){
            assert(motifNo == 0 || this.m_motifLengths[motifNo] > this.m_motifLengths[motifNo - 1]);

            if(this.m_motifLengths[motifNo] > dataVector.length - 1){ // => m > n - 1
                if(motifNo > 0 && this.m_motifLengths[motifNo - 1] < dataVector.length - 1){
                    LOGGER.warning("Data vector of length "+dataVector.length+" too short for " +
                            "motif length"+this.m_motifLengths[motifNo]+". Starting last motif search with " +
                            "motif length " + (dataVector.length - 1) + ".");

                    motifSearchTasks.add(
                            this.m_executorService.submit(
                                    new MatrixProfileMotifCandidateFinder(
                                            dataVector,
                                            dataVector.length - 1,
                                            this.m_exclusionZoneFactor,
                                            this.m_mathUtils.get(motifNo),
                                            this.m_mpHysteresisMinThreshold,
                                            this.m_mpHysteresisMaxThreshold,
                                            motifNo,
                                            this.m_filterMode)
                            )
                    );
                }
                else{
                    LOGGER.warning("Data vector of length "+dataVector.length+" too short for " +
                            "motif length"+this.m_motifLengths[motifNo]+". Last motif search already performed with " +
                            "motif length " + (dataVector.length-1) + ".");
                }
                break;
            }
            else { // => m <= n - 1
                motifSearchTasks.add(
                        this.m_executorService.submit(
                                new MatrixProfileMotifCandidateFinder(
                                        dataVector,
                                        this.m_motifLengths[motifNo],
                                        this.m_exclusionZoneFactor,
                                        this.m_mathUtils.get(motifNo),
                                        this.m_mpHysteresisMinThreshold,
                                        this.m_mpHysteresisMaxThreshold,
                                        motifNo,
                                        this.m_filterMode)
                        )
                );
            }
        }
        int oldCandidateSize;
        for(int motifNo = 0; motifNo < motifSearchTasks.size(); motifNo++){
            oldCandidateSize = candidates.size();
            try {
                candidates.addAll(motifSearchTasks.get(motifNo).get()); // Blocking for as along as the search task is not finished
            } catch(InterruptedException e){
                for(Future<List<Triple<Integer, Integer, Double>>> future : motifSearchTasks){
                    while(!future.isDone()){
                        Thread.yield();
                    }
                }
                throw new InterruptedException();
            } catch (ExecutionException e) {
                throw new RuntimeException(e); // No recovery
            }
            LOGGER.info("Search for motifs of length "+
                    this.m_motifLengths[motifNo]+ " results in "+(candidates.size() - oldCandidateSize) +" candidates.");
        }
        this.m_nextVisualizeBeginIndex.addAndGet(dataVector.length - this.m_motifLengths[0] + 1);

        return(candidates);
    }

    private synchronized void visualizeMP(double[] matrixProfile, int motifNo, int windowLength, FilterMode filterMode) throws Exception {
        assert(matrixProfile.length > 1);
        if(this.m_visualizeMP){
            String visualizerName = "v_"+filterMode.name();
            if(!this.m_rInterface.evalBoolean("exists(\""+visualizerName+"\")")){
                assert(this.m_nextVisualizeBeginIndex.get() == 0);

                StringBuilder sb = new StringBuilder();
                assert(this.m_motifLengths.length > 0);
                for(int i = 0; i < this.m_motifLengths.length; i++){
                    if(i == 0){
                        sb.append("c(");
                    }

                    sb.append("\"Matrix profile ").append(i + 1)
                            .append(", window length: ").append(windowLength);
                    if(filterMode != FilterMode.NONE) {
                        sb.append(", filter mode: ").append(filterMode.toString());
                    }
                    sb.append("\"");

                    if(i < this.m_motifLengths.length - 1){
                        sb.append(",");
                    }
                    if(i == this.m_motifLengths.length - 1){
                        sb.append(")");
                    }
                }
                double maxXProgressFactor = this.m_visualizationJSON.getDouble("max_x_progress_factor");
                if(maxXProgressFactor <= 0 || maxXProgressFactor > 1){
                    throw new IllegalArgumentException();
                }
                String tmp;
                this.m_rInterface.evalVoid(
                        visualizerName+" <- ait$Datastream_Visualization$new(" +
                                "assertions_status = "+(this.m_visualizationJSON.getBoolean("assertions_status") ? "TRUE" : "FALSE" )+","+
                                "numeric_iterators_num = "+this.m_motifLengths.length+","+
                                "character_iterators_num = 0,"+
                                "NA_label = \""+this.m_nonMotifLabel+"\"," +
                                "replot_min_interval = "+this.m_visualizationJSON.getDouble("replot_min_interval")+","+
                                "x_axis_range = "+matrixProfile.length+","+
                                "max_x_progress = "+(maxXProgressFactor * matrixProfile.length)+","+
                                "graph_labels = "+sb.toString()+","+
                                "plot_width = "+this.m_visualizationJSON.getDouble("plot_width")+","+
                                // Note: Datastream_Visualization takes EITHER "plot_height" OR "plot_height_per_graph"
                                "plot_height = "+
                                (this.m_visualizationJSON.isNull("plot_height")
                                        ? "NULL"
                                        : this.m_visualizationJSON.getDouble("plot_height"))+","+
                                "plot_height_per_graph = "+
                                (this.m_visualizationJSON.isNull("plot_height_per_graph")
                                    ? "NULL"
                                    : this.m_visualizationJSON.getDouble("plot_height_per_graph"))+","+
                                "window_label = \""+this.m_visualizationJSON.getString("window_label")+"\","+
                                "ground_truth_name = \"label\","+
                                "timestamp_name = \"DataIndex\","+
                                "color_rnd_seed = "+this.m_visualizationJSON.getInt("color_rnd_seed")+"," +
                                "first_plot_out_path = "+
                                  (this.m_firstMPPlotOutPath == null ? "NULL" : ("\"" +
                                          FilenameUtils.removeExtension(this.m_firstMPPlotOutPath) +
                                          "_" +
                                          filterMode.name() +
                                          ((tmp = FilenameUtils.getExtension(this.m_firstMPPlotOutPath)).isEmpty()
                                                  ? ""
                                                  : "." + tmp) +
                                          "\""))+")");
            }

            this.m_rInterface.assignDoubles("data", matrixProfile);
            this.m_rInterface.evalVoid(
                    visualizerName+"$add_data(" +
                            "elements = list(data.frame(\"label\" = rep(\""+this.m_nonMotifLabel+"\", times = "+matrixProfile.length+")," +
                            "\"DataIndex\" = "+(this.m_nextVisualizeBeginIndex.get()+1)+":"+(this.m_nextVisualizeBeginIndex.get() + matrixProfile.length)+","+
                            "\"data\" = data))," +
                            "dimensions = "+(motifNo+1)+"," + // R indices start at 1!
                            "replot = TRUE)");
        }
    }

    @Override
    protected boolean close2(){
        boolean result1 = this.m_rInterface.close();
        boolean result2 = true;
        for (MathUtils mathUtil : this.m_mathUtils) {
            result2 = result2 && mathUtil.close();
        }
        return result1 && result2;
    }
}
