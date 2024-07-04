import java.io.*;
import java.net.*;

public class Client {
    private static String SERVER_ADDRESS = "127.0.0.1";
    private static int SERVER_PORT = 9000;

    public static void main(String[] args) {
        if (args.length == 2) {
            SERVER_ADDRESS = args[0];
            SERVER_PORT = Integer.parseInt(args[1]);
        }

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connesso al server su " + SERVER_ADDRESS + ":" + SERVER_PORT);
            String input;
            while ((input = userInput.readLine()) != null) {
                out.println(input);
                String response = in.readLine();
                System.out.println("Risposta del server: " + response);
                if (input.equals("quit")) {
                    System.out.println("Disconnesso dal server.");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Impossibile connettersi al server: " + e.getMessage());
        }
    }
}
