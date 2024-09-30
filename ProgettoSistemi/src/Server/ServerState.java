package Server;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Classe per mantenere lo stato globale del server
public class ServerState {
    public static Map<String, List<Message>> topics = new ConcurrentHashMap<>(); // Mappa dei topics e relativi messaggi
    public static Map<String, Map<String, List<Message>>> publisherMessages = new ConcurrentHashMap<>(); // Messaggi per publisher
    public static Map<String, Set<Socket>> subscribers = new ConcurrentHashMap<>(); // Subscribers per topic
    public static Set<String> lockedTopics = ConcurrentHashMap.newKeySet(); // Topics bloccati per ispezione
    public static Map<String, Queue<PendingMessage>> pendingMessages = new ConcurrentHashMap<>(); // Messaggi in attesa per topic
    public static Map<String, Integer> lastMessageId = new ConcurrentHashMap<>(); // Ultimo ID del messaggio per topic
    public static Map<String, ClientHandler> usernamesInUse = new ConcurrentHashMap<>(); // Username dei client connessi
}