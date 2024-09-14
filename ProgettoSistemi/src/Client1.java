import java.io.*;
import java.net.*;

public class Client1 {
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

            // Legge e invia l'username al server
            String username;
            while (true) {
                System.out.print("Inserisci il tuo nome utente: ");
                username = userInput.readLine();
                out.println("username " + username);  // Invia il nome utente al server
                out.flush();

                // Legge la risposta dal server
                String response = in.readLine();
                if (response != null) {
                    System.out.println(response);
                    if (response.startsWith("Nome utente impostato")) {
                        break;  // Username accettato dal server
                    } else if (response.startsWith("Il nome utente")) {
                        // Username già in uso, riprova
                        continue;
                    } else if (response.startsWith("Devi inserire un nome utente.")) {
                        // Nessun username inserito, riprova
                        continue;
                    } else {
                        // Altra risposta, termina
                        System.out.println("Connessione terminata dal server.");
                        return;
                    }
                } else {
                    System.out.println("Connessione chiusa dal server.");
                    return;
                }
            }

            System.out.println("Connesso al server su " + SERVER_ADDRESS + ":" + SERVER_PORT);
            System.out.println("Digita 'help' per visualizzare tutti i comandi disponibili.");
            System.out.println("Nessun ruolo impostato attualmente.");

            // Thread per la lettura delle risposte dal server
            Thread serverResponseThread = new Thread(() -> {
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
            });
            serverResponseThread.start();

            String input;
            while (true) {
                // Legge l'input dell'utente
                System.out.print("> ");
                input = userInput.readLine();
                if (input == null) {
                    break; // Fine dell'input
                }
                out.println(input);
                out.flush(); // Assicurati che i dati siano inviati immediatamente

                if (input.equals("quit")) {
                    System.out.println("Hai deciso di disconnetterti dal server. Arrivederci!");
                    break;
                }
            }

            // Chiude le risorse e interrompe il thread
            serverResponseThread.interrupt();
            socket.close();
        } catch (IOException e) {
            System.err.println("Impossibile connettersi al server: " + e.getMessage());
        }
    }
}