import java.io.*;
import java.net.*;

public class Client {
    private static String SERVER_ADDRESS = "127.0.0.1";
    private static int SERVER_PORT = 9000;

    public static void main(String[] args) {
        if (args.length == 2) {
            SERVER_ADDRESS = args[0];
            SERVER_PORT = Integer.parseInt(args[1]);
        }

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connesso al server su " + SERVER_ADDRESS + ":" + SERVER_PORT);
            System.out.println("Digita 'help' per visualizzare tutti i comandi disponibili.");
            System.out.println("Nessun ruolo impostato attualmente.");

            String input;
            while (true) {
                // Controlla l'input dell'utente
                if (userInput.ready()) {
                    input = userInput.readLine();
                    out.println(input);
                    out.flush(); // Assicurati che i dati siano inviati immediatamente

                    // Legge e stampa le risposte dal server
                    while (in.ready()) {
                        String response = in.readLine();
                        if (response != null && !response.isEmpty()) {
                            System.out.println(response);
                        }
                    }

                    if (input.equals("quit")) {
                        System.out.println("Hai deciso di disconnetterti dal server. Arrivederci!");
                        break;
                    }
                }

                // Legge e stampa le risposte dal server quando non c'è input dell'utente
                while (in.ready()) {
                    String response = in.readLine();
                    if (response != null && !response.isEmpty()) {
                        System.out.println(response);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Impossibile connettersi al server: " + e.getMessage());
        }
    }
}