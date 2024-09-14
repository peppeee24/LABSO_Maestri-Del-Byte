package Server;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
    public static Map<String, List<Message>> topics = new ConcurrentHashMap<>();
    public static Map<String, Map<String, List<Message>>> publisherMessages = new ConcurrentHashMap<>();
    public static Map<String, Set<Socket>> subscribers = new ConcurrentHashMap<>();
    public static Set<String> lockedTopics = ConcurrentHashMap.newKeySet();
    public static Map<String, Queue<PendingMessage>> pendingMessages = new ConcurrentHashMap<>();
    public static Map<String, Integer> lastMessageId = new ConcurrentHashMap<>();
    public static Map<String, ClientHandler> usernamesInUse = new ConcurrentHashMap<>();
}