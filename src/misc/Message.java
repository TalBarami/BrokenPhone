package misc;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public class Message {
    private static String getName() {
        return "Networking17";
    }

    /**
     * Creates new request message with random message id.
     * @return array of raw data containing the relevant message.
     */
    public static byte[] createRequestMessage() {
        return ByteBuffer.allocate(20)
                .put(getName().getBytes())
                .put(("REQM").getBytes())
                .putInt((int) (Integer.MAX_VALUE * Math.random()))
                .array();
    }

    /**
     * Creates new offer message with information to identify the sender.
     * @param msgId the id of the message.
     * @param IP the address of the sender.
     * @param port the port of the sender.
     * @return array of raw data containing the relevant message.
     */
    public static byte[] createOfferMessage(int msgId, InetAddress IP, short port) {
        return ByteBuffer.allocate(26)
                .put(getName().getBytes())
                .put(("OFFM").getBytes())
                .putInt(msgId)
                .put(IP.getAddress())
                .putShort(port)
                .array();
    }

    /**
     * gets the type of a given message.
     * @param msg the received message.
     * @return the type of the message as string (request or offer).
     */
    public static String getType(byte[] msg) {
        String message = new String(msg);
        if (message.contains(getName())) {
            if(message.length() >= 26 && getPort(msg) != 0)
                return "OFFM";
            else
                return "REQM";
        }
        return "UNDF";
    }

    /**
     * gets the id of a given message.
     * @param msg the received message.
     * @return the id of the message as int.
     */
    public static int getId(byte[] msg){
        return ByteBuffer.wrap(msg).getInt(16);
    }

    /**
     * gets the inet address of a given message.
     * @param msg the received message.
     * @return the inetAddress of the message.
     */
    public static InetAddress getIP(byte[] msg) throws Exception {
        byte[] ip = new byte[4];
        ByteBuffer.wrap(msg, 20, 4).get(ip);

        return InetAddress.getByAddress(ip);
    }

    /**
     * gets the port of a given message.
     * @param msg the received message.
     * @return the port of the message.
     */
    public static int getPort(byte[] msg) {
        return ByteBuffer.wrap(msg).getShort(24);
    }

    /**
     * replace a random character in a given message.
     * @param msg the given message.
     * @return a new message with random character replaced.
     */
    public static String twistMessage(String msg) {
        int index = (int) (msg.length() * Math.random());
        int diff = (int) ((Math.random() > 0.5 ? 1 : -1) * (20 * Math.random()));
        StringBuilder result = new StringBuilder(msg);
        result.setCharAt(index, (char) (msg.charAt(index) + diff));
        return result.toString();
    }

    /**
     * construct a string representation of a given message.
     * @param msg the given message.
     * @return string represents the message.
     */
    public static String getMessage(byte[] msg){
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(getType(msg)).append(getId(msg));
        if(getType(msg).equals("OFFM")) {
            try {
                sb.append(getIP(msg).toString()).append(":").append(getPort(msg));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
