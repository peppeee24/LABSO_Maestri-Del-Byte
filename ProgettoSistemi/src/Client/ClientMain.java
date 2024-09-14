package Client;

public class ClientMain {
    public static void main(String[] args) {
        ClientConnection clientConnection = new ClientConnection(args);
        clientConnection.start();
    }
}