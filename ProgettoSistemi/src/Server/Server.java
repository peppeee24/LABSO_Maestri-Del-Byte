package Server;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 9000;
    private List<ClientHandler> clientHandlers = new ArrayList<>();
    private boolean isRunning = true;

    public void start() {
        new CommandHandler(this).start();  // Avvia il thread per i comandi amministrativi
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Il server è in ascolto sulla porta " + PORT);
            while (isRunning) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(socket, this);
                    clientHandlers.add(clientHandler);
                    clientHandler.start();
                } catch (SocketException e) {
                    if (isRunning) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("C'è stato un errore: " + e.getMessage());
        } finally {
            // Chiude tutte le connessioni dei client quando il server si arresta
            disconnectAllClients();
            System.exit(0);
        }
    }

    public void stop() {
        isRunning = false;
    }

    public void disconnectAllClients() {
        for (ClientHandler clientHandler : clientHandlers) {
            clientHandler.interrupt();
            clientHandler.disconnect();
        }
    }

    public List<ClientHandler> getClientHandlers() {
        return clientHandlers;
    }

    public boolean isRunning() {
        return isRunning;
    }
}