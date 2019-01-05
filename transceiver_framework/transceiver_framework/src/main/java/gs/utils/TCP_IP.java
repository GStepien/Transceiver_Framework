/**  
 *    Copyright (c) 2018 Grzegorz Stepien
 *
 *    This file and its contents are provided under the BSD 3-clause license.
 *    For more details, see './LICENSE.md'
 *    (where '.' represents this program's root directory).
 */
 
package gs.utils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;

public class TCP_IP {
    // the ports below 1024 are system ports
    private static final int MIN_PORT_NUMBER = 1024;

    // the ports above 49151 are dynamic and/or private
    private static final int MAX_PORT_NUMBER = 49151;

    /**
     * Finds a free port between
     * {@link #MIN_PORT_NUMBER} and {@link #MAX_PORT_NUMBER}.
     * From: http://fahdshariff.blogspot.com/2012/10/java-find-available-port-number.html
     * @return a free port
     * @throws RuntimeException if a port could not be found
     */
    public static int findFreePort() {
        for (int i = MIN_PORT_NUMBER; i <= MAX_PORT_NUMBER; i++) {
            if (isPortAvailable(i)) {
                return i;
            }
        }
        throw new RuntimeException("Could not find an available port between " +
                MIN_PORT_NUMBER + " and " + MAX_PORT_NUMBER);
    }

    /**
     * Returns true if the specified port is available on this host.
     * From: http://fahdshariff.blogspot.com/2012/10/java-find-available-port-number.html
     * @param port the port to check
     * @return true if the port is available, false otherwise
     */
    public static boolean isPortAvailable(final int port) {
        ServerSocket serverSocket = null;
        DatagramSocket dataSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            dataSocket = new DatagramSocket(port);
            dataSocket.setReuseAddress(true);
            return true;
        } catch (final IOException e) {
            return false;
        } finally {
            if (dataSocket != null) {
                dataSocket.close();
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (final IOException e) {
                    throw new RuntimeException("Should never happen.", e);
                }
            }
        }
    }

}
