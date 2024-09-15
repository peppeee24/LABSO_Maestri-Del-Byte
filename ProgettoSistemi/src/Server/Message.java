package Server;

import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Message {
    private int id; // ID univoco del messaggio
    private final String text; // Testo del messaggio
    private final String timestamp; // Timestamp del messaggio
    private final String publisherUsername; // Username del publisher che ha inviato il messaggio

    public Message(int id, String text, String publisherUsername) {
        this.id = id;
        this.text = text;
        this.timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss").format(new Date());
        this.publisherUsername = publisherUsername;
    }

    // Getter e setter per gli attributi
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

    // Ottiene il socket del publisher utilizzando l'username
    public Socket getPublisherSocket() {
        ClientHandler clientHandler = ServerState.usernamesInUse.get(publisherUsername);
        return clientHandler != null ? clientHandler.getSocket() : null;
    }
}