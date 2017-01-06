import misc.State;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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
    private byte[] sendData;
    private byte[] receiveData;

    private ServerSocket tcpServerSocket;
    private Socket tcpInSocket;
    private Socket tcpOutSocket;


    Application() {
        initializeLogger();
        state = State.RX_OFF_TX_OFF;
    }

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
                        break;
                    case RX_ON_TX_ON:
                        logger.info("We are at the body of the broken phone simulation.");
                        brokenPhoneLink();
                        break;
                    default:
                        logger.severe("Fatal error detected. Invalid state.");
                        System.exit(1);
                }
            }
        } catch (Exception e) {
            logger.severe("Fatal error detected.");
            e.printStackTrace();
        }
    }

    private void initialize() throws Exception {
        try {
            tcpServerSocket = new ServerSocket(getAvailablePort());
            tcpServerSocket.setSoTimeout(SECOND);
            logger.info("TCP server socket is up on port " + tcpServerSocket.getLocalPort());

            InetAddress broadcastIP = InetAddress.getByName("255.255.255.255");

            udpSocket = new DatagramSocket(udpPort);
            udpSocket.setSoTimeout(SECOND);
            udpSocket.setBroadcast(true);

            sendData = createRequestMessage();
            receiveData = new byte[32];
            udpSocket.send(new DatagramPacket(sendData, sendData.length, broadcastIP, udpPort));
            logger.info("Sending request message via udp broadcast. Message: " + getId(sendData) + ", Port: " + udpPort);

            while (state.equals(State.RX_OFF_TX_OFF)) {
                if (acceptTcpConnection())
                    break;

                try {
                    logger.info("Attempt to receive message from the udp socket...");
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    udpSocket.receive(receivePacket);
                    if (receivePacket.getAddress().equals(InetAddress.getLocalHost()))
                        continue;

                    byte[] response = receivePacket.getData();
                    //String response = new String(receivePacket.getData());
                    logger.info("Received new message: " + new String(response) + ". RID: " + getId(response));
                    switch (getType(response)) {
                        case "REQM":
                            sendData = createOfferMessage(getId(response), InetAddress.getLocalHost(), (short) tcpServerSocket.getLocalPort());
                            logger.info("Request message received. Sending offer message: " + new String(sendData));
                            udpSocket.send(new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), udpPort));
                            break;
                        case "OFFM":
                            logger.info("Offer message received. Connecting to " + getIP(response));
                            tcpOutSocket = new Socket(getIP(response), getPort(response));
                            state = State.RX_OFF_TX_ON;
                            break;
                        default:
                            logger.warning("Invalid message type. Ignored.");
                            break;
                    }
                } catch (SocketTimeoutException e) {
                    logger.info("Failed to receive datagram packet. Broadcasting again.");
                    udpSocket.send(new DatagramPacket(sendData, sendData.length, broadcastIP, udpPort));
                }
            }
        } catch (Exception e) {
            throw new Exception("Exception during initialization.", e);
        }
    }

    private void brokenPhoneHead() throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(1);

        ex.submit(() -> {
            try {
                System.out.println("Please enter your initial input below:");
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String input = br.readLine();
                logger.info("Sent input: " + input);

                DataOutputStream out = new DataOutputStream(tcpOutSocket.getOutputStream());
                out.writeBytes(input + '\n');
                logger.info("Message sent via the tcp out socket.");
                ex.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        while (state.equals(State.RX_OFF_TX_ON)) {
            if (acceptTcpConnection())
                break;

            logger.info("Attempt to receive message from the udp socket...");
            try {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                udpSocket.receive(receivePacket);
                byte[] response = receivePacket.getData();
                if (getType(response).equals("REQM")) {
                    logger.info("Received new request message: " + new String(response) + ". RID: " + getId(response));
                    sendData = createOfferMessage(getId(response), InetAddress.getLocalHost(), (short) tcpServerSocket.getLocalPort());
                    udpSocket.send(new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), udpPort));
                    logger.info("Sent offer message: " + new String(sendData));
                } else {
                    logger.warning("Received undefined message: " + new String(response));
                }
            } catch (SocketTimeoutException ignore) {
                logger.info("No new messages detected via the udp socket.");
            }
        }
    }

    private void brokenPhoneTail() throws Exception {
        while (state.equals(State.RX_ON_TX_OFF)) {
            BufferedReader br = new BufferedReader(new InputStreamReader(tcpInSocket.getInputStream()));
            String input = br.readLine();
            System.out.println("Received input: " + input);
        }
    }

    private void brokenPhoneLink() throws Exception {
        while (state.equals(State.RX_ON_TX_ON)) {
            BufferedReader br = new BufferedReader(new InputStreamReader(tcpInSocket.getInputStream()));
            String input = br.readLine();
            logger.info("Received input: " + input);
            String output = twistMessage(input);

            DataOutputStream outputStream = new DataOutputStream(tcpOutSocket.getOutputStream());
            outputStream.writeBytes(output);
            logger.info("Sent message: " + output);
        }
    }

    private boolean acceptTcpConnection() throws Exception {
        if(tcpInSocket != null)
            return false;
        try {
            logger.info("Attempt to accept new connection...");
            tcpInSocket = tcpServerSocket.accept();
            state = state.equals(State.RX_OFF_TX_OFF) ? State.RX_ON_TX_OFF : State.RX_ON_TX_ON;
            logger.info("Received TCP connection from " + tcpInSocket.getInetAddress() + ". New state: " + state);
            return true;
        } catch (SocketTimeoutException ignore) {
            logger.info("No new connection detected");
            return false;
        }
    }

    private void initializeLogger() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
    }
}