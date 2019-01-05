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

package gs.mdp.utils.r;


import gs.utils.MathUtils;
import gs.utils.r.SimpleRInterface;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.math3.stat.StatUtils;

import java.util.Arrays;

// TODO Cuda based implementation
public class RMathUtils implements MathUtils {

    private final SimpleRInterface m_rInterface;

    public RMathUtils(SimpleRInterface rInterface) throws Exception {
        if(rInterface == null){
            throw new NullPointerException();
        }
        else if(rInterface.isClosed()){
            throw new IllegalArgumentException();
        }
        this.m_rInterface = rInterface;

        if(!this.m_rInterface.evalBoolean("length(ls()) == 0")) {
            throw new IllegalStateException("The R environment of the associated RServe instance is not empty.");
        }
        // Install (if necessary) and load "stats" package in R
        this.m_rInterface.evalVoid(
                "if(!require(\"stats\")){\n" +
                "    install.packages(\"stats\")\n" +
                "} else {\n" +
                "    library(\"stats\")\n" +
                "}");

    }

    @Override
    public synchronized double[] correlateFFT_1D(
            double[] data, int startIndex1, int endIndexExcl1,
            double[] filter, int startIndex2, int endIndexExcl2,
            double factor, double offset) throws Exception {
        if(data == null || filter == null){
            throw new NullPointerException();
        }
        if(startIndex1 < 0 || endIndexExcl1 > data.length){
            throw new IndexOutOfBoundsException();
        }
        if(startIndex2 < 0 || endIndexExcl2 > filter.length){
            throw new IndexOutOfBoundsException();
        }
        int dataLength = endIndexExcl1 - startIndex1;
        int filterLength = endIndexExcl2 - startIndex2;

        if(filterLength < 1 || dataLength < filterLength){
            throw new IllegalArgumentException();
        }
        if(dataLength == data.length) {
            this.m_rInterface.assignDoubles("x", data);
        }
        else{
            this.m_rInterface.assignDoubles("x", Arrays.copyOfRange(data, startIndex1, endIndexExcl1));
        }

        if(filterLength == filter.length) {
            this.m_rInterface.assignDoubles("y", filter);
        }
        else{
            this.m_rInterface.assignDoubles("y", Arrays.copyOfRange(filter, startIndex2, endIndexExcl2));
        }

        this.m_rInterface.evalVoid("factor <- "+factor);
        this.m_rInterface.evalVoid("offset <- "+offset);

        double[] result = this.m_rInterface.evalDoubles("factor * convolve(x, y, type = \"filter\") + offset");
        assert(result.length == dataLength - filterLength + 1);

        return result;
    }

    @Override
    public synchronized ImmutableTriple<double[], Double, Double> getScaleNormLoGFilter(int length, double standardDeviation) throws Exception {
        if(length <= 0 || standardDeviation <= 0){
            throw new IllegalArgumentException();
        }

        double meanIndex = (length + 1.0) / 2.0; // R indices start at 1!
        this.m_rInterface.evalVoid("x <- dnorm(0:"+(length+1)+", mean = "+meanIndex+", sd = "+standardDeviation+")");
        this.m_rInterface.evalVoid("x <- x / sum(x)");
        this.m_rInterface.evalVoid("y <- convolve(x, c(1, -2, 1), type = \"filter\") * "+standardDeviation+"^2");
        double[] filter = this.m_rInterface.evalDoubles("y");
        double sumPositives = this.m_rInterface.evalDouble("sum(y[y > 0])");
        double sumNegatives = this.m_rInterface.evalDouble("sum(y[y < 0])");

        assert(filter.length == length);
        assert(this.equalsWithinLimits(StatUtils.min(filter), filter[(int) Math.floor(meanIndex) - 1]));
        assert(this.equalsWithinLimits(StatUtils.min(filter), filter[(int) Math.ceil(meanIndex) - 1]));
        assert(this.equalsWithinLimits(filter[(int) Math.floor(meanIndex) - 1], filter[(int) Math.ceil(meanIndex) - 1]));

        return new ImmutableTriple<>(filter, sumNegatives, sumPositives);
    }

    @Override
    public synchronized ImmutableTriple<double[], Double, Double> getGaussFilter(int length, double standardDeviation) throws Exception {
        if(length <= 0 || standardDeviation <= 0){
            throw new IllegalArgumentException();
        }

        double meanIndex = (length + 1.0) / 2.0; // R indices start at 1!
        this.m_rInterface.evalVoid("x <- dnorm(1:"+length+", mean = "+meanIndex+", sd = "+standardDeviation+")");
        this.m_rInterface.evalVoid("x <- x / sum(x)");
        double[] filter = this.m_rInterface.evalDoubles("x");
        double sumPositives = this.m_rInterface.evalDouble("sum(x[x > 0])");
        assert(this.m_rInterface.evalDouble("sum(x[x < 0])") == 0);
        double sumNegatives = 0;

        assert(filter.length == length);
        assert(this.equalsWithinLimits(StatUtils.min(filter), filter[(int) Math.floor(meanIndex) - 1]));
        assert(this.equalsWithinLimits(StatUtils.min(filter), filter[(int) Math.ceil(meanIndex) - 1]));
        assert(this.equalsWithinLimits(filter[(int) Math.floor(meanIndex) - 1], filter[(int) Math.ceil(meanIndex) - 1]));

        return new ImmutableTriple<>(filter, sumNegatives, sumPositives);
    }

    @Override
    public synchronized boolean close(){
        return this.m_rInterface.close();
    }

    @Override
    public synchronized boolean isClosed(){
        return this.m_rInterface.isClosed();
    }
}
