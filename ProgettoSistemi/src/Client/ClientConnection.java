package Client;

import java.io.*;
import java.net.Socket;

public class ClientConnection {
    private String serverAddress = "127.0.0.1";
    private int serverPort = 9000;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private BufferedReader userInput;

    public ClientConnection(String[] args) {
        if (args.length == 2) {
            serverAddress = args[0];
            serverPort = Integer.parseInt(args[1]);
        }
    }

    public void start() {
        try {
            socket = new Socket(serverAddress, serverPort);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            userInput = new BufferedReader(new InputStreamReader(System.in));

            authenticate();

            System.out.println("Connesso al server su " + serverAddress + ":" + serverPort);
            System.out.println("Digita 'help' per visualizzare tutti i comandi disponibili.");
            System.out.println("Nessun ruolo impostato attualmente.");

            ServerListener serverListener = new ServerListener(in);
            serverListener.start();

            handleUserInput();

            serverListener.interrupt();
            socket.close();

        } catch (IOException e) {
            System.err.println("Impossibile connettersi al server: " + e.getMessage());
        }
    }

    private void authenticate() throws IOException {
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
    }

    private void handleUserInput() throws IOException {
        String input;
        while (true) {
            // Legge l'input dell'utente
            System.out.print("> ");
            input = userInput.readLine();
            if (input == null) {
                break; // Fine dell'input
            }
            out.println(input);
            out.flush(); // Assicura che i dati siano inviati immediatamente

            if (input.equals("quit")) {
                System.out.println("Hai deciso di disconnetterti dal server. Arrivederci!");
                break;
            }
        }
    }
}