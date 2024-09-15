package Server;

// Classe per rappresentare un messaggio in attesa di essere inviato (quando un topic è bloccato)
public class PendingMessage {
    private final String username; // Username del client che ha inviato il messaggio
    private final String messageText; // Testo del messaggio

    public PendingMessage(String username, String messageText) {
        this.username = username;
        this.messageText = messageText;
    }

    // Getter per gli attributi
    public String getUsername() {
        return username;
    }

    public String getMessageText() {
        return messageText;
    }
}