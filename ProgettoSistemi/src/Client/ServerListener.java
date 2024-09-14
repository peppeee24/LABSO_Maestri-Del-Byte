package Client;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerListener extends Thread {
    private BufferedReader in;

    public ServerListener(BufferedReader in) {
        this.in = in;
    }

    public void run() {
        try {
            String response;
            while ((response = in.readLine()) != null) {
                if (response != null && !response.isEmpty()) {
                    System.out.println(response);
                }
            }
        } catch (IOException e) {
            System.out.println("Connessione al server interrotta.");
        }
    }
}
