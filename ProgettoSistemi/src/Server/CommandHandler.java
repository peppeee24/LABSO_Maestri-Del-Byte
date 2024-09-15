package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class CommandHandler extends Thread {
    private Server server; // Riferimento al server principale


    public CommandHandler(Server server) {
        this.server = server;
    }
    // Gestisce i comandi del Server
    public void run() {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            // Legge i comandi dalla console del server
            while ((command = consoleReader.readLine()) != null) {
                String[] parts = command.split(" ", 2);
                String mainCommand = parts[0];
                String argument = parts.length > 1 ? parts[1] : "";

                synchronized (ServerState.lockedTopics) {
                    if (ServerState.lockedTopics.isEmpty()) {
                        switch (mainCommand) {
                            case "quit":
                                server.stop();
                                System.out.println("Il server sta chiudendo...");
                                server.disconnectAllClients();
                                System.exit(0);
                                return;  // Esce dal thread
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

    // Mostra i comandi amministrativi disponibili
    private void showAdminCommands() {
        System.out.println("Comandi amministrativi disponibili:");
        System.out.println("quit: disconnette tutti i client");
        System.out.println("show: mostra la lista di tutti i topic");
        System.out.println("inspect <topic>: apre una sessione interattiva per analizzare un topic");
    }

    // Mostra la lista di tutti i topic
    private void showTopics() {
        Set<String> topicList = ServerState.topics.keySet();
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

    // Inizia una sessione interattiva per ispezionare un topic
    private void inspectTopic(BufferedReader consoleReader, String topic) throws IOException {
        if (!ServerState.topics.containsKey(topic)) {
            System.out.println("Il topic '" + topic + "' non esiste.");
            return;
        }

        synchronized (ServerState.lockedTopics) {
            ServerState.lockedTopics.add(topic);
            ServerState.pendingMessages.putIfAbsent(topic, new LinkedList<>());
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
                    synchronized (ServerState.lockedTopics) {
                        ServerState.lockedTopics.remove(topic);
                        processPendingMessages(topic);
                    }
                    return;
                default:
                    System.out.println("Comando interattivo sconosciuto: " + command);
            }
        }
    }

    // Elenca tutti i messaggi di un topic
    private void listAllMessages(String topic) {
        List<Message> messages = ServerState.topics.get(topic);
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
                    System.out.println("  Utente: " + msg.getPublisherUsername());
                    System.out.println("  Testo: " + msg.getText());
                    System.out.println("  Data: " + msg.getTimestamp());
                }
            }
        }
    }

    // Elimina un messaggio dal topic specificato
    private void deleteMessage(String topic, int id) {
        List<Message> messages = ServerState.topics.get(topic);
        synchronized (messages) {
            boolean removed = messages.removeIf(msg -> msg.getId() == id);
            if (removed) {
                System.out.println("Messaggio con ID " + id + " eliminato.");
            } else {
                System.out.println("Messaggio con ID " + id + " non trovato.");
            }
        }
    }

    // Processa i messaggi in attesa dopo la fase di ispezione
    private void processPendingMessages(String topic) {
        Queue<PendingMessage> queue = ServerState.pendingMessages.get(topic);
        if (queue != null) {
            while (!queue.isEmpty()) {
                PendingMessage pendingMessage = queue.poll();
                Message message = new Message(0, pendingMessage.getMessageText(), pendingMessage.getUsername());
                sendMessage(topic, message);
                notifyClient(pendingMessage.getUsername(), "Il tuo messaggio è stato inviato sul topic: " + topic);
            }
            ServerState.pendingMessages.remove(topic);
        }
    }

    // Invia un messaggio al topic e notifica i subscribers
    private void sendMessage(String topic, Message message) {
        List<Message> messages = ServerState.topics.get(topic);
        synchronized (messages) {
            message.setId(getNextMessageId(topic));
            messages.add(message);
        }
        // Aggiorna la lista dei messaggi del publisher
        updatePublisherMessages(topic, message);
        // Notifica i subscribers del topic
        notifySubscribers(topic, message);
    }

    // Aggiorna la lista dei messaggi per il publisher
    private void updatePublisherMessages(String topic, Message message) {
        Map<String, List<Message>> clientMessages = ServerState.publisherMessages.get(message.getPublisherUsername());
        if (clientMessages != null) {
            List<Message> publisherMsgs = clientMessages.get(topic);
            if (publisherMsgs != null) {
                synchronized (publisherMsgs) {
                    publisherMsgs.add(message);
                }
            }
        }
    }

    // Notifica tutti i subscribers del topic di un nuovo messaggio
    private void notifySubscribers(String topic, Message message) {
        Set<Socket> subscriberSockets = ServerState.subscribers.get(topic);
        if (subscriberSockets != null) {
            synchronized (subscriberSockets) {
                for (Socket subscriberSocket : subscriberSockets) {
                    // Evita di notificare il Publisher stesso
                    if (!subscriberSocket.equals(message.getPublisherSocket())) {
                        try {
                            PrintWriter subscriberOut = new PrintWriter(subscriberSocket.getOutputStream(), true);
                            subscriberOut.println("Nuovo messaggio su " + topic + " da " + message.getPublisherUsername() + ":");
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
    }

    // Notifica un client specifico con un messaggio
    private void notifyClient(String username, String message) {
        ClientHandler clientHandler = ServerState.usernamesInUse.get(username);
        if (clientHandler != null) {
            clientHandler.sendMessageToClient(message);
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
}