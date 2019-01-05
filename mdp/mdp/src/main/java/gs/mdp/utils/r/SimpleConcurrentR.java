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

import gs.utils.r.RException;
import gs.utils.r.SimpleRInterface;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import java.io.*;
import java.nio.file.Paths;

// TODO : Make Rserve start automatically
public class SimpleConcurrentR implements SimpleRInterface {

    private final String m_host;
    private final int m_port;
    private boolean m_closed;
    private RConnection m_rConnection;

    // No need to synchronize, called in constructor
    private void startRClient() throws RException {
        try {
            this.m_rConnection = new RConnection(this.m_host, this.m_port);
            this.m_rConnection.voidEval("rm(list = ls())");
            this.m_rConnection.voidEval("setwd(\""+ Paths.get(".").toAbsolutePath() +"\")");
        } catch (RserveException e) {
            throw new RException(e);
        }
        this.m_closed = false;
    }

    // No need to synchronize, called in synchronized close()
    private boolean shutdownRClient() throws RException {
        if(this.m_closed){
            return true;
        }
        else {
            return (this.m_closed = this.m_rConnection.close());
        }
    }

    public SimpleConcurrentR(Integer port) throws RException {
        if(port == null){
            throw new NullPointerException();
        }
        else if(port < 0) {
            throw new IllegalArgumentException();
        }

        this.m_port = port;
        this.m_host = "127.0.0.1";
        this.m_closed = true;
        this.startRClient();
    }

    @Override
    public synchronized void evalVoid(String expression) throws RException {
        try {
            this.m_rConnection.voidEval(expression);
        } catch (RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized double evalDouble(String expression) throws RException {
        try {
            return this.m_rConnection.eval(expression).asDouble();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized int evalInt(String expression) throws RException{
        try {
            return this.m_rConnection.eval(expression).asInteger();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized String evalString(String expression) throws RException {
        try {
            return this.m_rConnection.eval(expression).asString();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized boolean evalBoolean(String expression) throws RException {
        try {
            REXP rResult = this.m_rConnection.eval(expression);
            boolean result;
            if (rResult.length() == 1 && rResult.isLogical() && !rResult.isNA()[0]) {
                result = ((REXPLogical) rResult).isTRUE()[0];
            } else {
                throw new IllegalArgumentException("Result is not a non-NA boolean of length one.");
            }
            return result;
        }
        catch(REXPMismatchException|RserveException e){
            throw new RException(e);
        }
    }

    @Override
    public synchronized double[] evalDoubles(String expression) throws RException {
        try {
            return this.m_rConnection.eval(expression).asDoubles();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized int[] evalInts(String expression) throws RException{
        try {
            return this.m_rConnection.eval(expression).asIntegers();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized double[][] evalDoubleMatrix(String expression) throws RException{
        try {
            return this.m_rConnection.eval(expression).asDoubleMatrix();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized String[] evalStrings(String expression) throws RException {
        try {
            return this.m_rConnection.eval(expression).asStrings();
        } catch (REXPMismatchException|RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized boolean[] evalBooleans(String expression) throws RException {
        try {
            REXP rResult = this.m_rConnection.eval(expression);
            if (!rResult.isLogical()) {
                throw new IllegalArgumentException("Result is not a non-NA boolean.");
            }

            boolean[] result = new boolean[rResult.length()];

            for (int i = 0; i < rResult.length(); i++) {
                if (!rResult.isNA()[i]) {
                    result[i] = ((REXPLogical) rResult).isTRUE()[i];
                } else {
                    throw new IllegalArgumentException("Result is not a non-NA boolean.");
                }
            }
            return result;
        }
        catch (REXPMismatchException|RserveException e){
            throw new RException(e);
        }
    }

    @Override
    public synchronized void assignBooleans(String varname, boolean[] values) throws RException {
        if(varname == null || values == null){
            throw new NullPointerException();
        }

        try {
            this.m_rConnection.assign(varname, new REXPLogical(values));
        } catch (RserveException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized void assignInts(String varname, int[] values) throws RException {
        if(varname == null || values == null){
            throw new NullPointerException();
        }
        try {
            this.m_rConnection.assign(varname, values);
        } catch (REngineException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized void assignDoubles(String varname, double[] values) throws RException {
        if(varname == null || values == null){
            throw new NullPointerException();
        }
        try {
            this.m_rConnection.assign(varname, values);
        } catch (REngineException e) {
            throw new RException(e);
        }
    }

    @Override
    public synchronized void assignStrings(String varname, String[] values) throws RException {
        if(varname == null || values == null){
            throw new NullPointerException();
        }
        try {
            this.m_rConnection.assign(varname, values);
        } catch (REngineException e) {
            throw new RException(e);
        }
    }

    @Override
    public String getHost() {
        return this.m_host;
    }

    @Override
    public int getPort() {
        return this.m_port;
    }

    @Override
    public synchronized boolean close() {
        try {
            return this.isClosed() || this.shutdownRClient();
        }
        catch (IOException e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean isClosed() {
        assert(!this.m_closed || !this.m_rConnection.isConnected());
        assert(this.m_rConnection.isConnected() || this.m_closed);
        return this.m_closed;
    }
}
