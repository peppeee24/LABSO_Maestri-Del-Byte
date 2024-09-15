package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 9000; // Porta su cui il server è in ascolto
    private List<ClientHandler> clientHandlers = new ArrayList<>(); // Lista dei client connessi
    private boolean isRunning = true; // Flag per indicare se il server è in esecuzione

    public void start() {
        // Avvia il thread per gestire i comandi amministrativi
        new CommandHandler(this).start();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Il server è in ascolto sulla porta " + PORT);
            System.out.println("Digita 'admin' per visualizzare tutti i comandi disponibili.");
            while (isRunning) {
                try {
                    // Accetta nuove connessioni dai client
                    Socket socket = serverSocket.accept();
                    // Crea un nuovo ClientHandler per ogni client connesso
                    ClientHandler clientHandler = new ClientHandler(socket, this);
                    clientHandlers.add(clientHandler);
                    clientHandler.start(); // Avvia il thread per gestire il client
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

    // Metodo per fermare il server
    public void stop() {
        isRunning = false;
    }

    // Disconnette tutti i client connessi
    public void disconnectAllClients() {
        for (ClientHandler clientHandler : clientHandlers) {
            clientHandler.interrupt();
            clientHandler.disconnect();
        }
    }

    // Getter per la lista dei client connessi
    public List<ClientHandler> getClientHandlers() {
        return clientHandlers;
    }

    // Verifica se il server è in esecuzione
    public boolean isRunning() {
        return isRunning;
    }
}