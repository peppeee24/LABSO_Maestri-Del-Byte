package Server;

public class PendingMessage {
    private final String username;
    private final String messageText;

    public PendingMessage(String username, String messageText) {
        this.username = username;
        this.messageText = messageText;
    }

    public String getUsername() {
        return username;
    }

    public String getMessageText() {
        return messageText;
    }
}
