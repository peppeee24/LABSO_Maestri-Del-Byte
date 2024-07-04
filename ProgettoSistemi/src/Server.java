import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 9000;
    private static Map<String, List<Message>> topics = new ConcurrentHashMap<>();
    private static Map<String, Map<String, List<Message>>> publisherMessages = new ConcurrentHashMap<>();
    private static Map<String, List<Socket>> subscribers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Il server è in ascolto sulla porta " + PORT);
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private String topic;
        private String clientAddress;
        private String role;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            System.out.println("Nuovo client connesso: " + clientAddress);
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Comando ricevuto: " + line);
                    String[] parts = line.split(" ", 2);
                    String command = parts[0];
                    String argument = parts.length > 1 ? parts[1] : "";

                    switch (command) {
                        case "publish":
                            role = "publisher";
                            topic = argument;
                            out.println("Publisher registrato per il topic: " + argument);
                            topics.putIfAbsent(argument, new ArrayList<>());
                            publisherMessages.putIfAbsent(clientAddress, new ConcurrentHashMap<>());
                            publisherMessages.get(clientAddress).putIfAbsent(argument, new ArrayList<>());
                            break;
                        case "subscribe":
                            role = "subscriber";
                            topic = argument;
                            subscribeToTopic(argument);
                            out.println("Iscritto al topic: " + argument);
                            break;
                        case "send":
                            if ("publisher".equals(role)) {
                                sendMessage(topic, argument);
                                out.println("Messaggio inviato sul topic: " + topic);
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
                            socket.close();
                            System.out.println("Client disconnesso: " + clientAddress);
                            return;
                        default:
                            out.println("Comando sconosciuto: " + command);
                    }
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
                    "quit: Disconnette il client dal server.\n" +
                    "help: Mostra questa lista di comandi.";
        }

        private void sendMessage(String topic, String message) {
            List<Message> messages = topics.get(topic);
            Message newMessage = new Message(messages.size() + 1, message);
            synchronized (messages) {
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
            synchronized (messages) {
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
            subscribers.putIfAbsent(topic, new ArrayList<>());
            synchronized (subscribers.get(topic)) {
                subscribers.get(topic).add(socket);
            }
        }

        private void notifySubscribers(String topic, Message message) {
            List<Socket> subscriberSockets = subscribers.get(topic);
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
    }

    private static class Message {
        private final int id;
        private final String text;
        private final String timestamp;

        public Message(int id, String text) {
            this.id = id;
            this.text = text;
            this.timestamp = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss").format(new Date());
        }

        public int getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}
