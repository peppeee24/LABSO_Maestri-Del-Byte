package Client;

/*
Per eseguire il codice dal terminale, digitare:
javac Client/ClientMain.java
java Client/ClientMain 127.0.0.1 9000
Se non si include il percorso, darà errore.
Mi raccomando, eseguire nella cartella che contiene la cartella Client, non in Client.
*/

public class ClientMain {
    public static void main(String[] args) {
        // Crea un'istanza di ClientConnection passando gli argomenti della riga di comando
        ClientConnection clientConnection = new ClientConnection(args);
        // Avvia la connessione al server
        clientConnection.start();
    }
}