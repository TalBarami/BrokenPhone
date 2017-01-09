package exceptions;

public class MessageToSelfException extends Exception {
    public MessageToSelfException(){
        super("Received request message from self. Ignored.");
    }
}
