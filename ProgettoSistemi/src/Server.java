import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 9000;
    private static Map<String, List<String>> topics = new ConcurrentHashMap<>();
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

        public ClientHandler(Socket socket) {
            this.socket = socket;
            System.out.println("Nuovo client connesso: " + socket.getInetAddress().getHostAddress());
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
                            out.println("Publisher registrato per il topic: " + argument);
                            topics.putIfAbsent(argument, new ArrayList<>());
                            break;
                        case "subscribe":
                            out.println("Iscritto al topic: " + argument);
                            subscribers.putIfAbsent(argument, new ArrayList<>());
                            subscribers.get(argument).add(socket);
                            break;
                        case "send":
                            String[] msgParts = argument.split(" ", 2);
                            String topic = msgParts[0];
                            String message = msgParts[1];
                            topics.get(topic).add(message);
                            if (subscribers.containsKey(topic)) {
                                for (Socket s : subscribers.get(topic)) {
                                    PrintWriter clientOut = new PrintWriter(s.getOutputStream(), true);
                                    clientOut.println("Nuovo messaggio su " + topic + ": " + message);
                                }
                            }
                            break;
                        case "list":
                            out.println("Messaggi: " + String.join(", ", topics.getOrDefault(argument, Collections.emptyList())));
                            break;
                        case "listall":
                            StringBuilder allMessages = new StringBuilder();
                            for (Map.Entry<String, List<String>> entry : topics.entrySet()) {
                                allMessages.append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue())).append("\n");
                            }
                            out.println(allMessages.toString());
                            break;
                        case "quit":
                            out.println("Arrivederci!");
                            socket.close();
                            System.out.println("Client disconnesso: " + socket.getInetAddress().getHostAddress());
                            return;
                        default:
                            out.println("Comando sconosciuto: " + command);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
