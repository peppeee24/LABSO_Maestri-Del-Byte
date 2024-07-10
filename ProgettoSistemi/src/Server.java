import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 9000;
    private static Map<String, List<Message>> topics = new ConcurrentHashMap<>();
    private static Map<String, Map<String, List<Message>>> publisherMessages = new ConcurrentHashMap<>();
    private static Map<String, Set<Socket>> subscribers = new ConcurrentHashMap<>();
    private static List<ClientHandler> clientHandlers = new ArrayList<>();
    private static boolean isRunning = true;
    private static Set<String> lockedTopics = ConcurrentHashMap.newKeySet();
    private static Map<String, Queue<PendingMessage>> pendingMessages = new ConcurrentHashMap<>();
    private static Map<String, Integer> lastMessageId = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new CommandHandler().start();  // Start the command handler thread
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Il server è in ascolto sulla porta " + PORT);
            while (isRunning) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(socket);
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
            // Close all client connections when server shuts down
            disconnectAllClients();
            System.exit(0);
        }
    }

    private static void disconnectAllClients() {
        for (ClientHandler clientHandler : clientHandlers) {
            clientHandler.interrupt();
            clientHandler.disconnect();
        }
    }

    private static class CommandHandler extends Thread {
        public void run() {
            try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
                String command;
                while ((command = consoleReader.readLine()) != null) {
                    String[] parts = command.split(" ", 2);
                    String mainCommand = parts[0];
                    String argument = parts.length > 1 ? parts[1] : "";

                    synchronized (lockedTopics) {
                        if (lockedTopics.isEmpty()) {
                            switch (mainCommand) {
                                case "quit":
                                    isRunning = false;
                                    System.out.println("Il server sta chiudendo...");
                                    disconnectAllClients();
                                    System.exit(0);
                                    return;  // Exit the thread
                                case "show":
                                    showTopics();
                                    break;
                                case "admin":
                                    showAdminCommands();
                                    break;
                                case "inspect":
                                    inspectTopic(consoleReader, argument);
                                    break;
                                default:
                                    System.out.println("Comando sconosciuto: " + mainCommand);
                            }
                        } else {
                            System.out.println("Comando disabilitato durante una sessione interattiva.");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void showAdminCommands() {
            System.out.println("Comandi amministrativi disponibili:");
            System.out.println("quit: disconnette tutti i client");
            System.out.println("show: mostra la lista di tutti i topic");
            System.out.println("inspect <topic>: apre una sessione interattiva per analizzare un topic");
        }

        private void showTopics() {
            Set<String> topicList = topics.keySet();
            System.out.println("Sono stati trovati " + topicList.size() + " topics.");
            if (topicList.isEmpty()) {
                System.out.println("Nessun topic trovato.");
            } else {
                System.out.println("Topics:");
                for (String topic : topicList) {
                    System.out.println("- " + topic);
                }
            }
        }

        private void inspectTopic(BufferedReader consoleReader, String topic) throws IOException {
            if (!topics.containsKey(topic)) {
                System.out.println("Il topic '" + topic + "' non esiste.");
                return;
            }

            synchronized (lockedTopics) {
                lockedTopics.add(topic);
                pendingMessages.putIfAbsent(topic, new LinkedList<>());
            }

            System.out.println("Sessione interattiva per il topic: '" + topic + "'");
            System.out.println("Comandi disponibili: :listall, :delete <id>, :end");

            String line;
            while ((line = consoleReader.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                String command = parts[0];
                String argument = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    case ":listall":
                        listAllMessages(topic);
                        break;
                    case ":delete":
                        try {
                            int id = Integer.parseInt(argument);
                            deleteMessage(topic, id);
                        } catch (NumberFormatException e) {
                            System.out.println("ID non valido: " + argument);
                        }
                        break;
                    case ":end":
                        System.out.println("Sessione interattiva terminata.");
                        synchronized (lockedTopics) {
                            lockedTopics.remove(topic);
                            processPendingMessages(topic);
                        }
                        return;
                    default:
                        System.out.println("Comando interattivo sconosciuto: " + command);
                }
            }
        }

        private void listAllMessages(String topic) {
            List<Message> messages = topics.get(topic);
            if (messages == null) {
                System.out.println("Il topic '" + topic + "' non esiste.");
                return;
            }
            synchronized (messages) {
                int messageCount = messages.size();
                System.out.println("Sono stati inviati " + messageCount + " messaggi in questo topic.");
                if (messages.isEmpty()) {
                    System.out.println("Nessun messaggio trovato.");
                } else {
                    System.out.println("Tutti i messaggi sul topic " + topic + ": ");
                    for (Message msg : messages) {
                        System.out.println("- ID: " + msg.getId());
                        System.out.println("  Testo: " + msg.getText());
                        System.out.println("  Data: " + msg.getTimestamp());
                    }
                }
            }
        }

        private void deleteMessage(String topic, int id) {
            List<Message> messages = topics.get(topic);
            synchronized (messages) {
                boolean removed = messages.removeIf(msg -> msg.getId() == id);
                if (removed) {
                    System.out.println("Messaggio con ID " + id + " eliminato.");
                } else {
                    System.out.println("Messaggio con ID " + id + " non trovato.");
                }
            }
        }

        private void processPendingMessages(String topic) {
            Queue<PendingMessage> queue = pendingMessages.get(topic);
            if (queue != null) {
                while (!queue.isEmpty()) {
                    PendingMessage pendingMessage = queue.poll();
                    Message message = new Message(0, pendingMessage.messageText, pendingMessage.clientSocket);
                    sendMessage(topic, message);
                    notifyClient(pendingMessage.clientSocket, "Il tuo messaggio è stato inviato sul topic: " + topic);
                }
                pendingMessages.remove(topic);
            }
        }

        private void sendMessage(String topic, Message message) {
            List<Message> messages = topics.get(topic);
            synchronized (messages) {
                message.setId(getNextMessageId(topic));
                messages.add(message);
            }
            // Aggiorna la lista dei messaggi del publisher
            updatePublisherMessages(topic, message);
            notifySubscribers(topic, message);
        }

        private void updatePublisherMessages(String topic, Message message) {
            for (ClientHandler clientHandler : clientHandlers) {
                if (clientHandler.getSocket().equals(message.getPublisherSocket())) {
                    Map<String, List<Message>> clientMessages = publisherMessages.get(clientHandler.getClientAddress());
                    if (clientMessages != null) {
                        List<Message> publisherMsgs = clientMessages.get(topic);
                        if (publisherMsgs != null) {
                            synchronized (publisherMsgs) {
                                publisherMsgs.add(message);
                            }
                        }
                    }
                    break;
                }
            }
        }

        private void notifySubscribers(String topic, Message message) {
            Set<Socket> subscriberSockets = subscribers.get(topic);
            if (subscriberSockets != null) {
                synchronized (subscriberSockets) {
                    for (Socket subscriberSocket : subscriberSockets) {
                        try {
                            PrintWriter subscriberOut = new PrintWriter(subscriberSocket.getOutputStream(), true);
                            subscriberOut.println("Nuovo messaggio su " + topic + ":");
                            subscriberOut.println("- ID: " + message.getId());
                            subscriberOut.println("  Testo: " + message.getText());
                            subscriberOut.println("  Data: " + message.getTimestamp());
                            subscriberOut.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        private void notifyClient(Socket clientSocket, String message) {
            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println(message);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private int getNextMessageId(String topic) {
            List<Message> messages = topics.get(topic);
            synchronized (messages) {
                int newId = lastMessageId.getOrDefault(topic, 0) + 1;
                lastMessageId.put(topic, newId);
                return newId;
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private String topic;
        private String clientAddress;
        private String role;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            System.out.println("Nuovo client connesso: " + clientAddress);
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);
                String line;
                while ((line = in.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;  // Exit the thread if interrupted
                    }

                    System.out.println("Comando ricevuto: " + line);
                    String[] parts = line.split(" ", 2);
                    String command = parts[0];
                    String argument = parts.length > 1 ? parts[1] : "";

                    if (topic != null && isTopicLocked(command, topic)) {
                        out.println("Il topic '" + topic + "' è attualmente in fase di ispezione. Il messaggio sarà inviato alla fine della fase di ispezione.");
                        if (command.equals("send") && "publisher".equals(role)) {
                            enqueueMessage(topic, argument);
                        }
                        continue;
                    }

                    switch (command) {
                        case "publish":
                            role = "publisher";
                            topic = argument;
                            if (argument.isEmpty()) {
                                out.println("Devi inserire il titolo del topic");
                                break;
                            }
                            out.println("Publisher registrato per il topic: '" + argument + "'");
                            topics.putIfAbsent(argument, new ArrayList<>());
                            publisherMessages.putIfAbsent(clientAddress, new ConcurrentHashMap<>());
                            publisherMessages.get(clientAddress).putIfAbsent(argument, new ArrayList<>());
                            lastMessageId.putIfAbsent(argument, 0);
                            break;
                        case "subscribe":
                            role = "subscriber";
                            topic = argument;
                            if (topics.containsKey(argument)) {
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
                                listAllMessages(out, topic);
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
                if (isRunning) {
                    e.printStackTrace();
                }
            }
        }

        private boolean isTopicLocked(String command, String topic) {
            return lockedTopics.contains(topic) &&
                    (command.equals("send") || command.equals("list") || command.equals("listall"));
        }

        private void enqueueMessage(String topic, String messageText) {
            pendingMessages.get(topic).add(new PendingMessage(socket, messageText));
        }

        public void disconnect() {
            try {
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
                    "list: Elenca tutti i messaggi per il topic specificato.\n" +
                    "listall: Elenca tutti i messaggi per tutti i topic.\n" +
                    "show: Mostra tutti i topic creati da tutti i client.\n" +
                    "quit: Disconnette il client dal server.\n" +
                    "help: Mostra questa lista di comandi.";
        }

        private void sendMessage(String topic, String messageText) {
            List<Message> messages = topics.get(topic);
            Message newMessage = new Message(0, messageText, socket); // ID sarà settato correttamente
            synchronized (messages) {
                newMessage.setId(getNextMessageId(topic));
                messages.add(newMessage);
            }
            Map<String, List<Message>> clientMessages = publisherMessages.get(clientAddress);
            List<Message> publisherMsgs = clientMessages.get(topic);
            synchronized (publisherMsgs) {
                publisherMsgs.add(newMessage);
            }
            notifySubscribers(topic, newMessage);
        }

        private void listMessages(PrintWriter out, String topic, boolean includeAll) {
            List<Message> messages;
            if (includeAll) {
                messages = topics.get(topic);
            } else {
                messages = publisherMessages.get(clientAddress).get(topic);
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
                        out.println("  Testo: " + msg.getText());
                        out.println("  Data: " + msg.getTimestamp());
                    }
                    out.flush();
                }
            }
        }

        private void listAllMessages(PrintWriter out, String topic) {
            List<Message> messages = topics.get(topic);
            if (messages == null) {
                out.println("Il topic '" + topic + "' non esiste.");
                return;
            }
            synchronized (messages) {
                int messageCount = messages.size();
                out.println("Sono stati inviati " + messageCount + " messaggi in questo topic.");
                if (messages.isEmpty()) {
                    out.println("Nessun messaggio trovato.");
                } else {
                    out.println("Tutti i messaggi sul topic " + topic + ": ");
                    for (Message msg : messages) {
                        out.println("- ID: " + msg.getId());
                        out.println("  Testo: " + msg.getText());
                        out.println("  Data: " + msg.getTimestamp());
                    }
                    out.flush();
                }
            }
        }

        private void subscribeToTopic(String topic) {
            subscribers.putIfAbsent(topic, ConcurrentHashMap.newKeySet());
            synchronized (subscribers.get(topic)) {
                subscribers.get(topic).add(socket);
            }
        }

        private void notifySubscribers(String topic, Message message) {
            Set<Socket> subscriberSockets = subscribers.get(topic);
            if (subscriberSockets != null) {
                synchronized (subscriberSockets) {
                    for (Socket subscriberSocket : subscriberSockets) {
                        try {
                            // Evita di notificare il Publisher stesso
                            if (!subscriberSocket.equals(message.getPublisherSocket())) {
                                PrintWriter subscriberOut = new PrintWriter(subscriberSocket.getOutputStream(), true);
                                subscriberOut.println("Nuovo messaggio su " + topic + ":");
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
            Set<String> topicList = topics.keySet();
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
            List<Message> messages = topics.get(topic);
            synchronized (messages) {
                int newId = lastMessageId.getOrDefault(topic, 0) + 1;
                lastMessageId.put(topic, newId);
                return newId;
            }
        }

        public Socket getSocket() {
            return socket;
        }

        public String getClientAddress() {
            return clientAddress;
        }
    }

    private static class Message {
        private int id;
        private final String text;
        private final String timestamp;
        private final Socket publisherSocket;

        public Message(int id, String text, Socket publisherSocket) {
            this.id = id;
            this.text = text;
            this.timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss").format(new Date());
            this.publisherSocket = publisherSocket;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public Socket getPublisherSocket() {
            return publisherSocket;
        }
    }

    private static class PendingMessage {
        private final Socket clientSocket;
        private final String messageText;

        public PendingMessage(Socket clientSocket, String messageText) {
            this.clientSocket = clientSocket;
            this.messageText = messageText;
        }
    }
}
