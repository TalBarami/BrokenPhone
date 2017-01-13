import exceptions.MessageToSelfException;
import misc.State;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static misc.Message.*;
import static misc.Utils.SECOND;
import static misc.Utils.getAvailablePort;

class Application {
    private Logger logger = Logger.getLogger("Application");

    private State state;

    private DatagramSocket udpSocket;
    private int udpPort = 6000;

    private ServerSocket tcpServerSocket;
    private Socket tcpInSocket;
    private Socket tcpOutSocket;

    /**
     * Application is created with state {@link State#RX_OFF_TX_OFF}
     */
    Application() {
        initializeLogger();
        state = State.RX_OFF_TX_OFF;
    }

    /**
     * Start the broken phone simulation.
     */
    void start() {
        try {
            while (true) {
                logger.info("Current state is: " + state + ".");
                switch (state) {
                    case RX_OFF_TX_OFF:
                        logger.info("Initializing...");
                        initialize();
                        break;
                    case RX_OFF_TX_ON:
                        logger.info("We are at the head of the broken phone simulation.");
                        brokenPhoneHead();
                        break;
                    case RX_ON_TX_OFF:
                        logger.info("We are at the tail of the broken phone simulation.");
                        brokenPhoneTail();
                        return;
                    case RX_ON_TX_ON:
                        logger.info("We are at the body of the broken phone simulation.");
                        brokenPhoneLink();
                        return;
                    default:
                        logger.severe("Fatal error detected. Invalid state.");
                        exit(1);
                }
            }
        } catch(SocketException e){
            logger.warning("Connection closed. System exit...");
            exit(0);
        } catch (Exception e) {
            logger.severe("Fatal error detected.");
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Initialize the broken phone app, while state is {@link State#RX_OFF_TX_OFF}.
     * @throws Exception
     */
    private void initialize() throws Exception {
        try {
            tcpServerSocket = new ServerSocket(getAvailablePort());
            tcpServerSocket.setSoTimeout(SECOND);
            logger.info("TCP server socket is up on port " + tcpServerSocket.getLocalPort());

            udpSocket = new DatagramSocket(udpPort);
            udpSocket.setSoTimeout(SECOND);
            udpSocket.setBroadcast(true);

            broadcastRequestMessage();

            while (state.equals(State.RX_OFF_TX_OFF)) {
                if (acceptTcpConnection())
                    return;

                try {
                    DatagramPacket receivePacket = getUdpMessage();
                    byte[] response = receivePacket.getData();

                    switch (getType(response)) {
                        case "REQM":
                            handleRequestMessage(receivePacket, response);
                            break;
                        case "OFFM":
                            handleOfferMessage(response);
                            break;
                        default:
                            logger.warning("Invalid message type. Ignored.");
                            break;
                    }
                } catch (SocketTimeoutException e) {
                    logger.info("Failed to receive datagram packet. Broadcasting new request message.");
                    broadcastRequestMessage();
                } catch (MessageToSelfException e){
                    logger.info(e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new Exception("Exception during initialization.", e);
        }
    }

    /**
     * Represents the head of the broken phone app, while state is {@link State#RX_OFF_TX_ON}.
     */
    private void brokenPhoneHead() throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(1);

        ex.submit(() -> {
            try {
                System.out.println("Please enter your initial inputs below:");
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                while (state.equals(State.RX_OFF_TX_ON) && tcpOutSocket.isConnected()) {
                    String input = br.readLine();

                    DataOutputStream out = new DataOutputStream(tcpOutSocket.getOutputStream());
                    out.writeBytes(input + '\n');
                    logger.info("Sent message: " + input + " to " + tcpOutSocket.getInetAddress());

                }
                ex.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        while (state.equals(State.RX_OFF_TX_ON)) {
            if (acceptTcpConnection())
                return;

            try {
                DatagramPacket receivePacket = getUdpMessage();
                byte[] response = receivePacket.getData();

                if (getType(response).equals("REQM")) {
                    handleRequestMessage(receivePacket, response);
                } else {
                    logger.warning("Received undefined message: " + getMessage(response));
                }
            } catch (SocketTimeoutException ignore) {
                logger.info("No new messages detected via the udp socket.");
            } catch (MessageToSelfException e){
                logger.info(e.getMessage());
            }
        }
    }

    /**
     * Represents the tail of the broken phone app, while state is {@link State#RX_ON_TX_OFF}.
     * In this state we are waiting to receive inputs from another link and display it on the screen.
     */
    private void brokenPhoneTail() throws Exception {
        while (state.equals(State.RX_ON_TX_OFF)) {
            String input = getTcpMessage();
            System.out.println("Received new message: " + input);
        }
    }

    /**
     * Represents a link of the broken phone app, while state is {@link State#RX_ON_TX_ON}.
     * In this state we accept new messages from the previous link and transfer them to the next one.
     */
    private void brokenPhoneLink() throws Exception {
        while (state.equals(State.RX_ON_TX_ON)) {
            String input = getTcpMessage();
            sendTcpMessage(twistMessage(input));
        }
    }

    /**
     * The server will attempt to receive a new tcp-input-connection.
     * @return whether succeed to accept a new tcp connection.
     */
    private boolean acceptTcpConnection() throws IOException {
        if(tcpInSocket != null)
            return false;
        try {
            logger.info("Attempt to accept new connection...");
            tcpInSocket = tcpServerSocket.accept();
            logger.info("Received TCP connection from " + tcpInSocket.getInetAddress());
        } catch (SocketTimeoutException ignore) {
            logger.info("No new connection detected");
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(tcpOutSocket != null && tcpInSocket.getInetAddress().equals(tcpOutSocket.getInetAddress())){
            logger.warning("The new in-connection is the same as the out-connection. Ignored.");
            tcpInSocket.close();
            tcpInSocket = null;
            return false;
        } else {
            state = state.equals(State.RX_OFF_TX_OFF) ? State.RX_ON_TX_OFF : State.RX_ON_TX_ON;
            logger.info("New state: " + state);
            return true;
        }
    }

    /**
     * The udp socket will broadcast a new request message.
     */
    private void broadcastRequestMessage() throws Exception{
        byte[] sendData;
        InetAddress broadcastIP = InetAddress.getByName("255.255.255.255");
        sendData = createRequestMessage();
        udpSocket.send(new DatagramPacket(sendData, sendData.length, broadcastIP, udpPort));
        logger.info("Sending request message via udp broadcast. Message: " + getMessage(sendData) + ", Port: " + udpPort);
    }

    /**
     * Handle the newly accepted request message.
     * Sending offer message as response.
     * @param receivePacket
     * @param response
     */
    private void handleRequestMessage(DatagramPacket receivePacket, byte[] response) throws Exception{
        byte[] sendData;
        logger.info("Received new request message: " + getMessage(response));
        sendData = createOfferMessage(getId(response), InetAddress.getLocalHost(), (short) tcpServerSocket.getLocalPort());
        udpSocket.send(new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), udpPort));
        logger.info("Sent offer message: " + getMessage(sendData));
    }

    /**
     * Handle the newly received offer message.
     * Attempting to connect the tcp out socket into the offered address.
     * @param response
     * @throws Exception
     */
    private void handleOfferMessage(byte[] response) throws IOException {
        InetAddress toConnect = getIP(response);
        logger.info("Offer message received. Attempting to connect to " + toConnect);

        if(tcpOutSocket != null) {
            logger.warning("Failed to connect to " + toConnect + " because out-socket is already connected.");
            return;
        }

        tcpOutSocket = new Socket();
        //tcpOutSocket.bind(new InetSocketAddress(toConnect,getPort(response)));
        try {
            tcpOutSocket.connect(new InetSocketAddress(toConnect, getPort(response)), 10);
        } catch(IOException e){
            logger.warning("Failed to connect to " + toConnect + " because other side refused connection.");
            tcpOutSocket.close();
            tcpOutSocket = null;
            return;
        }
        state = State.RX_OFF_TX_ON;
    }

    /**
     * Attempts to received a udp message (request or offer message).
     * @return the newly received message.
     * @throws Exception
     */
    private DatagramPacket getUdpMessage() throws Exception{
        byte[] receiveData = new byte[32];
        logger.info("Attempt to receive message from the udp socket...");
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        udpSocket.receive(receivePacket);

        if (receivePacket.getAddress().equals(InetAddress.getLocalHost()))
            throw new MessageToSelfException();

        byte[] response = receivePacket.getData();
        logger.info("Received new message: " + getMessage(response));

        return receivePacket;
    }

    /**
     * Attempts to receive a new tcp message (raw input).
     * @return the newly received message.
     * @throws Exception
     */
    private String getTcpMessage() throws Exception{
        logger.info("Waiting for input from " + tcpInSocket.getInetAddress());
        BufferedReader br = new BufferedReader(new InputStreamReader(tcpInSocket.getInputStream()));
        String input = br.readLine();
        logger.info("Received input: " + input + " from " +tcpInSocket.getInetAddress());
        return input;
    }

    /**
     * Sending new tcp message via the tcp out socket.
     * @param msg the message to be sent.
     * @throws Exception
     */
    private void sendTcpMessage(String msg) throws Exception{
        DataOutputStream outputStream = new DataOutputStream(tcpOutSocket.getOutputStream());
        outputStream.writeBytes(msg + "\n");
        logger.info("Sent message: " + msg + " to " + tcpOutSocket.getInetAddress());
    }

    /**
     * Initialize the logging format.
     */
    private void initializeLogger() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
    }

    /**
     * Close all sockets before exit.
     * @param exitCode
     */
    private void exit(int exitCode){
        try {
            if (tcpInSocket != null)
                tcpInSocket.close();
            if (tcpOutSocket != null)
                tcpOutSocket.close();
            if (tcpServerSocket != null)
                tcpServerSocket.close();
            if (udpSocket != null)
                udpSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        tcpInSocket = null;
        tcpOutSocket = null;
        tcpServerSocket = null;
        udpSocket = null;
        System.exit(exitCode);
    }
}