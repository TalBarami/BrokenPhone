package misc;

import java.net.PortUnreachableException;
import java.net.ServerSocket;
import java.net.SocketException;

/**
 * Created by Tal on 02/01/2017.
 */
public class Utils {
    public static final int MIN_PORT = 6000;
    public static final int MAX_PORT = 7000;

    public static final int SECOND = 1000;

    /**
     * Will attempt to receive an available port for the server socket between {@link Utils#MIN_PORT} and {@link Utils#MAX_PORT}
     * @return available port.
     * @throws Exception if no port is available
     */
    public static int getAvailablePort() throws Exception {

        for (int i = MIN_PORT; i < MAX_PORT; i++) {
            try {
                (new ServerSocket(i)).close();
                return i;
            } catch (SocketException ignore) {
            }
        }
        throw new PortUnreachableException("Couldn't find port between " + MIN_PORT + " to " + MAX_PORT);
    }
}
