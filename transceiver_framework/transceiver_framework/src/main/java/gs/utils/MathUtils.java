/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;


import gs.utils.datatypes.DoubleData;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.math3.stat.StatUtils;

import java.util.Collection;

public interface MathUtils {

    double EPS = 0.0001;

    public default double[] correlateFFT_1D(double[] data, double[] filter,
                                            double factor, double offset) throws Exception {
        if(data == null || filter == null){
            throw new NullPointerException();
        }
        return this.correlateFFT_1D(
                data, 0, data.length,
                filter, 0, filter.length,
                factor, offset);
    }

    // Result length = (endIndexExcl - startIndex) - (endIndex2 - endIndexExcl2) + 1, same for the others
    double[] correlateFFT_1D(double[] data, int startIndex1, int endIndexExcl1,
                             double[] filter, int startIndex2, int endIndexExcl2,
                             double factor, double offset) throws Exception;

    public default double[] correlate_1D(double[] data, double[] filter,
                                         double factor, double offset) throws Exception {
        if(data == null || filter == null){
            throw new NullPointerException();
        }
        return this.correlate_1D(
                data, 0, data.length,
                filter, 0, filter.length,
                factor, offset);
    }

    // Result length = (endIndexExcl - startIndex) - (endIndex2 - endIndexExcl2) + 1, same for the others
    public default double[] correlate_1D(
            double[] data, int startIndex1, int endIndexExcl1,
            double[] filter, int startIndex2, int endIndexExcl2,
            double factor, double offset) {
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

        int resultLength = dataLength - filterLength + 1;
        double[] result = new double[resultLength];

        for(int i = startIndex1; i < resultLength; i++){
            for(int j = 0; j < filterLength; j++){
                result[i] += data[i + j] * filter[j];
            }
            result[i] = factor * result[i] + offset;
        }

        return result;
    }

    // Laplacian of Gaussian
    // Triple = (filter, sum of negative values, sum of positive values)
    ImmutableTriple<double[], Double, Double> getScaleNormLoGFilter(int length, double standardDeviation) throws Exception;

    // Triple = (filter, sum of negative values, sum of positive values)
    ImmutableTriple<double[], Double, Double> getGaussFilter(int length, double standardDeviation) throws Exception;

    public default double mean(double[] data){
        if(data == null){
            throw new NullPointerException();
        }
        return this.mean(data, 0, data.length);
    }

    public default double mean(double[] data, int startIndex, int endIndexExcl){
        int length = endIndexExcl - startIndex;
        if(length == 0){
            throw new IllegalArgumentException();
        }
        double result = StatUtils.mean(data, startIndex, length);
        assert(!Double.isNaN(result));
        return result;
    }

    public default double variance(double[] data){
        if(data == null){
            throw new NullPointerException();
        }
        return this.variance(data, 0, data.length);
    }

    public default double variance(double[] data, int startIndex, int endIndexExcl){

        int length = endIndexExcl - startIndex;
        if(length < 2){
            throw new IllegalArgumentException();
        }
        double result = StatUtils.variance(data, startIndex, length);
        assert(!Double.isNaN(result));
        return result;
    }

    // If inSitu is true, result will be stored in x
    public default double[] pairwiseMin(double[] x, double[]y, boolean inSitu){
        if(x == null || y == null){
            throw new NullPointerException();
        }
        if(x.length != y.length){
            throw new IllegalArgumentException();
        }

        double[] result = inSitu ? x : new double[x.length];
        for(int i = 0; i < x.length; i++){
            result[i] = Math.min(x[i], y[i]);
        }
        return result;
    }

    // If inSitu is true, result will be stored in x
    public default double[] pairwiseMax(double[] x, double[]y, boolean inSitu){
        if(x == null || y == null){
            throw new NullPointerException();
        }
        if(x.length != y.length){
            throw new IllegalArgumentException();
        }

        double[] result = inSitu ? x : new double[x.length];
        for(int i = 0; i < x.length; i++){
            result[i] = Math.max(x[i], y[i]);
        }
        return result;
    }

    default double[] toDoubleArray(Collection<DoubleData> collection) {
        if(collection == null){
            throw new NullPointerException();
        }
        double[] result = new double[collection.size()];
        int i = 0;
        for(DoubleData element : collection){
            result[i] = element.getData();
            i++;
        }

        return result;
    }

    public default boolean equalsWithinLimits(double value1, double value2){
        return this.equalsWithinLimits(value1, value2, EPS);
    }

    public default boolean equalsWithinLimits(double value1, double value2, double eps){
        if(eps < 0){
            throw new IllegalArgumentException("'eps' must be >= 0.");
        }
        return Math.abs(value1 - value2) <= eps;
    }

    boolean close();
    boolean isClosed();


}
