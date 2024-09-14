package Server;


import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Message {
    private int id;
    private final String text;
    private final String timestamp;
    private final String publisherUsername;

    public Message(int id, String text, String publisherUsername) {
        this.id = id;
        this.text = text;
        this.timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss").format(new Date());
        this.publisherUsername = publisherUsername;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getPublisherUsername() {
        return publisherUsername;
    }

    public Socket getPublisherSocket() {
        ClientHandler clientHandler = ServerState.usernamesInUse.get(publisherUsername);
        return clientHandler != null ? clientHandler.getSocket() : null;
    }
}