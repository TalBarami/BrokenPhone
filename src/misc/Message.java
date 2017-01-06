package misc;

import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Created by Tal on 05/01/2017.
 */
public class Message {
    private static String getName() {
        return "Networking17";
    }

    public static byte[] createRequestMessage() {
        return ByteBuffer.allocate(20)
                .put(getName().getBytes())
                .put(("REQM").getBytes())
                .putInt((int) (Integer.MAX_VALUE * Math.random()))
                .array();
    }

    public static byte[] createOfferMessage(int msgId, InetAddress IP, short port) {
        return ByteBuffer.allocate(26)
                .put(getName().getBytes())
                .put(("OFFM").getBytes())
                .putInt(msgId)
                .put(IP.getAddress())
                .putShort(port)
                .array();
    }

    public static String getType(byte[] msg) {
        String message = new String(msg);
        if (message.contains(getName())) {

            // ARAB - FIXME
            if(getPort(msg) != 0)
                return "OFFM";
            else
                return "REQM";
            /*if (message.length() == 20)
                return "REQM";
            else if (message.length() == 26)
                return "OFFM";*/
        }
        return "UNDF";
    }

    public static int getId(byte[] msg){
        return ByteBuffer.wrap(msg).getInt(16);
    }

    public static InetAddress getIP(byte[] msg) throws Exception {
        byte[] ip = new byte[4];
        ByteBuffer.wrap(msg, 20, 4).get(ip);

        return InetAddress.getByAddress(ip);
    }

    public static int getPort(byte[] msg) {
        return ByteBuffer.wrap(msg).getShort(24);
    }

    public static String twistMessage(String msg) {
        int index = (int) (msg.length() * Math.random());
        StringBuilder result = new StringBuilder(msg);
        result.setCharAt(index, (char) (msg.charAt(index) + 'a'));
        return result.toString();
    }
}
