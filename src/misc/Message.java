package misc;

import java.net.InetAddress;
import java.nio.ByteBuffer;

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
            if(message.length() >= 26 && getPort(msg) != 0)
                return "OFFM";
            else
                return "REQM";
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
