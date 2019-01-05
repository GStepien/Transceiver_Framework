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

package gs.mdp.visualizations;

import gs.utils.datatypes.LabeledTimestampedData;
import gs.utils.datatypes.StringData;
import gs.utils.datatypes.DoubleData;
import gs.tf.core.GenericMuxMultiReceiverTask;
import gs.utils.r.RException;
import gs.mdp.utils.r.SimpleConcurrentR;
import gs.utils.r.SimpleRInterface;
import org.apache.commons.collections4.set.UnmodifiableSet;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

// TODO Create own object SyntheticDataStreamVisualization as interface between m_rInterface and this object to use here
public class DatastreamVisualizationReceiver extends
        GenericMuxMultiReceiverTask.AbstractTargetReceiverTask<LabeledTimestampedData<?>> {

    private final SimpleRInterface m_rInterface;
    private final UnmodifiableSet<Integer> m_numericStreamIndices;

    public DatastreamVisualizationReceiver(
            Integer inDataQueueCapacity,
            Long timeoutInterval,
            int numMuxTransmitters,
            int port,
            String rDriverPath,
            String streamJSONPath) throws RException {
        super(inDataQueueCapacity, timeoutInterval, numMuxTransmitters);

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
        this.m_rInterface.evalVoid("ds_visualization <- driver$create_ds_visualization_from_json(json_file = \""+
                Paths.get(streamJSONPath).toAbsolutePath()+"\")");

        int[] numInd = this.m_rInterface.evalInts("ds_visualization$get_numeric_indices()");
        Set<Integer> numericStreamIndicesSet = new HashSet<>();
        for(int ind : numInd){
            if(ind < 1 || ind >= numMuxTransmitters + 1){ // R indices start at 1!
                throw new IllegalStateException();
            }
            numericStreamIndicesSet.add(ind - 1);
        }
        this.m_numericStreamIndices = (UnmodifiableSet<Integer>) UnmodifiableSet.unmodifiableSet(numericStreamIndicesSet);
    }

    // Todo: Maybe buffer data for some time before sending to R visualization
    @Override
    protected void processDataElementFromMux(int muxIndex, LabeledTimestampedData<?> dataElement) throws InterruptedException {
        try {
            DoubleData numData = null;
            StringData stringData = null;
            boolean isNum = this.m_numericStreamIndices.contains(muxIndex);
            if (isNum) {
                if (dataElement instanceof DoubleData) {
                    numData = (DoubleData) dataElement;
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                if (dataElement instanceof StringData) {
                    stringData = (StringData) dataElement;
                } else {
                    throw new IllegalArgumentException();
                }
            }

            if (isNum) {
                this.m_rInterface.evalVoid("ds_visualization$add_data (" +
                        "elements = list(list(list(\"" + numData.getLabel() + "\", " + numData.getTimestamp() + ", " + numData.getData() + ")))," +
                        "dimensions = " + (muxIndex + 1) + ", " + // R indices start at 1!
                        "replot = TRUE)");
            } else {
                this.m_rInterface.evalVoid("ds_visualization$add_data (" +
                        "elements = list(list(list(\"" + stringData.getLabel() + "\", " + stringData.getTimestamp() + ", \"" + stringData.getData() + "\")))," +
                        "dimensions = " + (muxIndex + 1) + ", " +
                        "replot = TRUE)");
            }
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
