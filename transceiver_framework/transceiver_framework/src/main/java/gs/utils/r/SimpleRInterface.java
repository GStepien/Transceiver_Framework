/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils.r;

public interface SimpleRInterface {
    void evalVoid(String expression) throws RException;
    double evalDouble(String expression) throws RException;
    int evalInt(String expression) throws RException;
    String evalString(String expression) throws RException;
    boolean evalBoolean(String expression) throws RException;
    double[] evalDoubles(String expression) throws RException;
    int[] evalInts(String expression) throws RException;
    double[][] evalDoubleMatrix(String expression) throws RException;
    String[] evalStrings(String expression) throws RException;
    boolean[] evalBooleans(String expression) throws RException;

    void assignBooleans(String varname, boolean[] values) throws RException;
    void assignInts(String varname, int[] values) throws RException;
    void assignDoubles(String varname, double[] values) throws RException;
    void assignStrings(String varname, String[] values) throws RException;

    String getHost();
    int getPort();
    boolean close();
    boolean isClosed();
}
