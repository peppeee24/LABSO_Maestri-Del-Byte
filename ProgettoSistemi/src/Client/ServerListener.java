package Client;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerListener extends Thread {
    private BufferedReader in; // Flusso di input dal server

    public ServerListener(BufferedReader in) {
        this.in = in;
    }
// Fa parlare il server, prende le risposte le stampa per il client
    public void run() {
        try {
            String response;
            // Continua a leggere le risposte dal server
            while ((response = in.readLine()) != null) {
                if (response != null && !response.isEmpty()) {
                    // Stampa la risposta ricevuta dal server
                    System.out.println(response);
                }
            }
        } catch (IOException e) {
            System.out.println("Connessione al server interrotta.");
        }
    }
}