package Server;


import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler extends Thread {
    private Socket socket;
    private Server server;
    private String topic;
    private String clientAddress;
    private String role;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        System.out.println("Nuovo client connesso: " + clientAddress);
    }

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
                        break;
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
                    return;  // Esci dal thread se interrotto
                }

                System.out.println("Comando ricevuto da " + username + ": " + line);
                String[] parts = line.split(" ", 2);
                String command = parts[0];
                String argument = parts.length > 1 ? parts[1] : "";

                if (topic != null && isTopicLocked(command, topic)) {
                    out.println("Fase Ispettiva del topic '" + topic + "' in corso. Attendere il termine o riprova più tardi...");
                    if (command.equals("send") && "publisher".equals(role)) {
                        out.println("Il topic '" + topic + "' è attualmente in fase di ispezione. Il messaggio sarà inviato alla fine della fase di ispezione.");
                        enqueueMessage(topic, argument);
                    } else if (command.equals("send") && "subscriber".equals(role)) {
                        out.println("Il topic '" + topic + "' è attualmente in fase di ispezione.. Per inviare un messaggio devi prima registrarti come publisher utilizzando il comando 'publish <topic>'");
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
                        out.println("Arrivederci!");
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

    private boolean isTopicLocked(String command, String topic) {
        return ServerState.lockedTopics.contains(topic) &&
                (command.equals("send") || command.equals("list") || command.equals("listall"));
    }

    private void enqueueMessage(String topic, String messageText) {
        ServerState.pendingMessages.get(topic).add(new PendingMessage(username, messageText));
    }

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

    private static String getHelp() {

        return "Comandi disponibili:\n" +
                "publish <topic>: Registra un publisher per il topic specificato.\n" +
                "subscribe <topic>: Iscrive il client al topic specificato.\n" +
                "send <message>: Invia un messaggio al topic specificato.\n" +
                "list: Elenca tutti i messaggi mandati dal publish corrente per il suo topic.\n" +
                "listall: Elenca tutti i messaggi mandati da tutti i publisher per un topic.\n" +
                "show: Mostra tutti i topic creati da tutti i publisher.\n" +
                "quit: Disconnette il client dal server.\n" +
                "help: Mostra questa lista di comandi.";
    }

    private void sendMessage(String topic, String messageText) {
        List<Message> messages = ServerState.topics.get(topic);
        Message newMessage = new Message(0, messageText, username); // ID sarà settato correttamente
        synchronized (messages) {
            newMessage.setId(getNextMessageId(topic));
            messages.add(newMessage);
        }
        Map<String, List<Message>> clientMessages = ServerState.publisherMessages.get(username);
        List<Message> publisherMsgs = clientMessages.get(topic);
        synchronized (publisherMsgs) {
            publisherMsgs.add(newMessage);
        }
        notifySubscribers(topic, newMessage);
    }

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

    private void subscribeToTopic(String topic) {
        ServerState.subscribers.putIfAbsent(topic, ConcurrentHashMap.newKeySet());
        synchronized (ServerState.subscribers.get(topic)) {
            ServerState.subscribers.get(topic).add(socket);
        }
    }

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

    private int getNextMessageId(String topic) {
        List<Message> messages = ServerState.topics.get(topic);
        synchronized (messages) {
            int newId = ServerState.lastMessageId.getOrDefault(topic, 0) + 1;
            ServerState.lastMessageId.put(topic, newId);
            return newId;
        }
    }

    public void sendMessageToClient(String message) {
        out.println(message);
        out.flush();
    }

    public Socket getSocket() {
        return socket;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public String getUsername() {
        return username;
    }
}