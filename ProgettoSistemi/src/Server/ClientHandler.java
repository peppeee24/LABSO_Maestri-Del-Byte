package Server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler extends Thread {
    private Socket socket; // Socket per la comunicazione con il client
    private Server server; // Riferimento al server principale
    private String topic; // Topic a cui il client è associato
    private String clientAddress; // Indirizzo del client
    private String role; // Ruolo del client (publisher o subscriber)
    private PrintWriter out; // Flusso di output verso il client
    private String username; // Username del client

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        System.out.println("Nuovo client connesso: " + clientAddress);
    }

// Gestisce i comandi del client
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            String line;

            // Richiede l'username se non è stato già impostato
            while (username == null && (line = in.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                String command = parts[0];
                String argument = parts.length > 1 ? parts[1] : "";

                if (command.equals("username")) {
                    if (argument.isEmpty()) {
                        out.println("Devi inserire un nome utente.");
                    } else if (ServerState.usernamesInUse.containsKey(argument)) {
                        out.println("Il nome utente '" + argument + "' è già in uso. Scegli un altro nome utente.");
                    } else {
                        this.username = argument;
                        ServerState.usernamesInUse.put(username, this);
                        out.println("Nome utente impostato: " + username);
                        System.out.println("Client " + clientAddress + " ha impostato l'username: " + username);
                        break; // Esce dal loop dopo aver impostato l'username
                    }
                } else {
                    out.println("Devi impostare un nome utente utilizzando il comando 'username <nome>' prima di continuare.");
                }
            }

            if (username == null) {
                // Se l'username non è stato impostato, termina la connessione
                disconnect();
                return;
            }

            // Da qui in poi, l'username è impostato
            while ((line = in.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    return; // Esci dal thread se interrotto
                }

                System.out.println("Comando ricevuto da " + username + ": " + line);
                String[] parts = line.split(" ", 2);
                String command = parts[0];
                String argument = parts.length > 1 ? parts[1] : "";

                // Controlla se il topic è bloccato (in fase di ispezione)
                if (topic != null && isTopicLocked(command, topic)) {
                    out.println("Fase Ispettiva del topic '" + topic + "' in corso. Attendere il termine o riprova più tardi...");
                    if (command.equals("send") && "publisher".equals(role)) {
                        out.println("Il topic '" + topic + "' è attualmente in fase di ispezione. Il messaggio sarà inviato alla fine della fase di ispezione.");
                        enqueueMessage(topic, argument);
                    } else if (command.equals("send") && "subscriber".equals(role)) {
                        out.println("Il topic '" + topic + "' è attualmente in fase di ispezione. Per inviare un messaggio devi prima registrarti come publisher utilizzando il comando 'publish <topic>'");
                    }
                    continue;
                }

                switch (command) {
                    case "publish":
                        role = "publisher";
                        topic = argument;
                        if (argument.isEmpty()) {
                            out.println("Devi inserire il titolo del topic");
                        } else {
                            out.println("Publisher registrato per il topic: '" + argument + "'");
                            // Crea il topic se non esiste e inizializza le strutture dati
                            ServerState.topics.putIfAbsent(argument, new ArrayList<>());
                            ServerState.publisherMessages.putIfAbsent(username, new ConcurrentHashMap<>());
                            ServerState.publisherMessages.get(username).putIfAbsent(argument, new ArrayList<>());
                            ServerState.lastMessageId.putIfAbsent(argument, 0);
                        }
                        break;
                    case "subscribe":
                        role = "subscriber";
                        topic = argument;
                        if (ServerState.topics.containsKey(argument)) {
                            subscribeToTopic(argument);
                            out.println("Iscritto al topic: " + argument);
                        } else {
                            out.println("Il topic '" + argument + "' non esiste.");
                        }
                        break;
                    case "send":
                        if ("publisher".equals(role) && !argument.isEmpty()) {
                            sendMessage(topic, argument);
                            out.println("Messaggio inviato sul topic: " + topic);
                        } else if (argument.isEmpty()) {
                            out.println("Il messaggio è vuoto, riprova");
                        } else {
                            out.println("Devi prima registrarti come publisher utilizzando il comando 'publish <topic>'");
                        }
                        break;
                    case "list":
                        if ("publisher".equals(role)) {
                            listMessages(out, topic, false);
                        } else {
                            out.println("Devi prima registrarti come publisher utilizzando il comando 'publish <topic>'");
                        }
                        break;
                    case "listall":
                        if (topic != null) {
                            listMessages(out, topic, true);
                        } else {
                            out.println("Devi prima registrarti come publisher o subscriber utilizzando il comando 'publish <topic>' o 'subscribe <topic>'");
                        }
                        break;
                    case "help":
                        out.println(getHelp());
                        break;
                    case "show":
                        showTopics(out);
                        break;
                    case "quit":
                        //out.println("Arrivederci!");
                        disconnect();
                        return;
                    default:
                        out.println("Comando sconosciuto: " + command);
                }
                // Aggiunta di notifica dello stato corrente del client
                if ("publisher".equals(role)) {
                    out.println("Stato corrente: Publisher del topic '" + topic + "'");
                } else if ("subscriber".equals(role)) {
                    out.println("Stato corrente: Subscriber del topic '" + topic + "'");
                }
            }
        } catch (IOException e) {
            if (server.isRunning()) {
                e.printStackTrace();
            }
        } finally {
            disconnect(); // Assicura la disconnessione e la rimozione dell'username
        }
    }

    // Verifica se il topic è bloccato per operazioni di ispezione
    private boolean isTopicLocked(String command, String topic) {
        return ServerState.lockedTopics.contains(topic) &&
                (command.equals("send") || command.equals("list") || command.equals("listall"));
    }

    // Aggiunge un messaggio alla coda dei messaggi in attesa per il topic
    private void enqueueMessage(String topic, String messageText) {
        ServerState.pendingMessages.get(topic).add(new PendingMessage(username, messageText));
    }

    // Metodo per disconnettere il client
    public void disconnect() {
        try {
            if (username != null) {
                ServerState.usernamesInUse.remove(username);
            }
            if (!socket.isClosed()) {
                out.println("Il server ha terminato la connessione");
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Fornisce la lista dei comandi disponibili al client
    private static String getHelp() {

        return "Comandi disponibili:\n" +
                "publish <topic>: Registra un publisher per il topic specificato.\n" +
                "subscribe <topic>: Iscrive il client al topic specificato.\n" +
                "send <message>: Invia un messaggio al topic specificato.\n" +
                "list: Elenca tutti i messaggi mandati dal publisher corrente per il suo topic.\n" +
                "listall: Elenca tutti i messaggi mandati da tutti i publisher per un topic.\n" +
                "show: Mostra tutti i topic creati da tutti i publisher.\n" +
                "quit: Disconnette il client dal server.\n" +
                "help: Mostra questa lista di comandi.";
    }

    // Invia un messaggio al topic e notifica i subscribers
    private void sendMessage(String topic, String messageText) {
        List<Message> messages = ServerState.topics.get(topic);
        Message newMessage = new Message(0, messageText, username); // ID sarà impostato successivamente


        // Controlla l'accesso concorrente gestendo la sincronizzazione
        // Nel caso ad esempio due pubblisher dello stesso topic inviino un messaggio allo stesso momento
        // Solo un thread alla volta può eseguire il codice all'interno del blocco synchronized
        // Nessun altro thread può accederci finchè quello in corso non ha finito

        synchronized (messages) {
            newMessage.setId(getNextMessageId(topic));
            messages.add(newMessage);
        }
        // Aggiorna la lista dei messaggi del publisher
        Map<String, List<Message>> clientMessages = ServerState.publisherMessages.get(username);
        List<Message> publisherMsgs = clientMessages.get(topic);
        synchronized (publisherMsgs) {
            publisherMsgs.add(newMessage);
        }
        // Notifica i subscribers del topic
        notifySubscribers(topic, newMessage);
    }

    // Elenca i messaggi del topic, può includere solo quelli del publisher corrente o tutti
    private void listMessages(PrintWriter out, String topic, boolean includeAll) {
        List<Message> messages;
        if (includeAll) {
            messages = ServerState.topics.get(topic);
        } else {
            messages = ServerState.publisherMessages.get(username).get(topic);
        }
        synchronized (messages) {
            int messageCount = messages.size();
            out.println("Sono stati inviati " + messageCount + " messaggi in questo topic.");

            if (messages.isEmpty()) {
                out.println("Nessun messaggio trovato.");
            } else {
                out.println("Messaggi:");
                for (Message msg : messages) {
                    out.println("- ID: " + msg.getId());
                    out.println("  Utente: " + msg.getPublisherUsername());
                    out.println("  Testo: " + msg.getText());
                    out.println("  Data: " + msg.getTimestamp());
                }
                out.flush();
            }
        }
    }

    // Registra il client come subscriber al topic specificato
    /*private void subscribeToTopic(String topic) {
        ServerState.subscribers.putIfAbsent(topic, ConcurrentHashMap.newKeySet());
        synchronized (ServerState.subscribers.get(topic)) {
            ServerState.subscribers.get(topic).add(socket);
        }
    }

     */
    /*Sincronizza l'intero metodo su ServerState.subscribers o una parte dell'operazione.
    In questo caso, stai sincronizzando l'accesso sia a putIfAbsent che all'operazione successiva.
    Ora l'intera operazione è atomica e non ci saranno interferenze tra i thread.
    Il lock viene acquisito una sola volta su ServerState.subscribers,
    garantendo che solo un thread alla volta esegua sia il controllo che l'azione.*/
    private void subscribeToTopic(String topic) {
        synchronized (ServerState.subscribers) {
            ServerState.subscribers.putIfAbsent(topic, ConcurrentHashMap.newKeySet());
            ServerState.subscribers.get(topic).add(socket);
        }
    }


    // Notifica tutti i subscribers del topic di un nuovo messaggio
    private void notifySubscribers(String topic, Message message) {
        Set<Socket> subscriberSockets = ServerState.subscribers.get(topic);
        if (subscriberSockets != null) {
            synchronized (subscriberSockets) {
                for (Socket subscriberSocket : subscriberSockets) {
                    try {
                        // Evita di notificare il Publisher stesso
                        if (!subscriberSocket.equals(socket)) {
                            PrintWriter subscriberOut = new PrintWriter(subscriberSocket.getOutputStream(), true);
                            subscriberOut.println("Nuovo messaggio su " + topic + " da " + message.getPublisherUsername() + ":");
                            subscriberOut.println("- ID: " + message.getId());
                            subscriberOut.println("  Testo: " + message.getText());
                            subscriberOut.println("  Data: " + message.getTimestamp());
                            subscriberOut.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // Mostra la lista di tutti i topic disponibili
    private void showTopics(PrintWriter out) {
        Set<String> topicList = ServerState.topics.keySet();
        if (topicList.isEmpty()) {
            out.println("Nessun topic trovato.");
        } else {
            out.println("Topics:");
            for (String topic : topicList) {
                out.println("- " + topic);
            }
        }
    }

    // Genera un nuovo ID per un messaggio nel topic specificato
    private int getNextMessageId(String topic) {
        List<Message> messages = ServerState.topics.get(topic);
        synchronized (messages) {
            int newId = ServerState.lastMessageId.getOrDefault(topic, 0) + 1;
            ServerState.lastMessageId.put(topic, newId);
            return newId;
        }
    }

    // Invia un messaggio al client
    public void sendMessageToClient(String message) {
        out.println(message);
        out.flush();
    }

    // Getter per il socket del client
    public Socket getSocket() {
        return socket;
    }

    // Getter per l'indirizzo del client
    public String getClientAddress() {
        return clientAddress;
    }

    // Getter per l'username del client
    public String getUsername() {
        return username;
    }
}