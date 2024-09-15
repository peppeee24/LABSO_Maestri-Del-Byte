package Client;

import java.io.*;
import java.net.Socket;

public class ClientConnection {
    private String serverAddress = "127.0.0.1"; // Indirizzo IP del server (localhost di default)
    private int serverPort = 9000; // Porta su cui il server è in ascolto

    private Socket socket; // Socket per la connessione al server
    private BufferedReader in; // Flusso di input dal server
    private PrintWriter out; // Flusso di output verso il server
    private BufferedReader userInput; // Flusso di input dall'utente (console)

    // Costruttore che accetta argomenti per impostare indirizzo e porta del server
    public ClientConnection(String[] args) {
        if (args.length == 2) {
            serverAddress = args[0];
            serverPort = Integer.parseInt(args[1]);
        }
    }

    // Metodo principale per avviare la connessione e gestire l'interazione con il server
    public void start() {
        try {
            // Creazione del socket per connettersi al server
            socket = new Socket(serverAddress, serverPort);
            // Inizializzazione dei flussi di input e output
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            userInput = new BufferedReader(new InputStreamReader(System.in));

            // Autenticazione dell'utente (impostazione dell'username)
            authenticate();

            System.out.println("Connesso al server su " + serverAddress + ":" + serverPort);
            System.out.println("Digita 'help' per visualizzare tutti i comandi disponibili.");
            System.out.println("Nessun ruolo impostato attualmente.");

            // Avvio di un thread per ascoltare i messaggi dal server
            ServerListener serverListener = new ServerListener(in);
            serverListener.start();

            // Gestione dell'input dell'utente dalla console
            handleUserInput();

            // Interrompe il thread di ascolto e chiude la connessione
            serverListener.interrupt();
            socket.close();

        } catch (IOException e) {
            System.err.println("Impossibile connettersi al server: " + e.getMessage());
        }
    }

    // Metodo per autenticare l'utente impostando l'username
    private void authenticate() throws IOException {
        String username;
        while (true) {
            System.out.print("Inserisci il tuo nome utente: ");
            username = userInput.readLine();
            out.println("username " + username); // Invia il comando 'username' al server
            out.flush();

            // Legge la risposta dal server
            String response = in.readLine();
            if (response != null) {
                System.out.println(response);
                if (response.startsWith("Nome utente impostato")) {
                    break; // Username accettato dal server, esce dal loop
                } else if (response.startsWith("Il nome utente")) {
                    // Username già in uso, riprova
                    continue;
                } else if (response.startsWith("Devi inserire un nome utente.")) {
                    // Nessun username inserito, riprova
                    continue;
                } else {
                    // Altra risposta, termina la connessione
                    System.out.println("Connessione terminata dal server.");
                    return;
                }
            } else {
                System.out.println("Connessione chiusa dal server.");
                return;
            }
        }
    }

    // Metodo per gestire l'input dell'utente dalla console
    private void handleUserInput() throws IOException {
        String input;
        while (true) {
            // Legge l'input dell'utente
            System.out.print("> ");
            input = userInput.readLine();
            if (input == null) {
                break; // Fine dell'input (EOF)
            }
            out.println(input); // Invia il comando al server
            out.flush(); // Assicura che i dati siano inviati immediatamente

            if (input.equals("quit")) {
                System.out.println("Hai deciso di disconnetterti dal server. Arrivederci!");
                break; // Esce dal loop se l'utente decide di disconnettersi
            }
        }
    }
}