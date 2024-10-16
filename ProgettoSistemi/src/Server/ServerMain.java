package Server;

/*
Per eseguire il codice dal terminale, digitare:
javac Server/ServerMain.java
java Server/ServerMain
Se non si include il percorso, darà errore.
Mi raccomando, eseguire nella cartella che contiene la cartella Server, non in Server.
*/

public class ServerMain
{
    public static void main(String[] args) {
        // Crea un'istanza del server
        Server server = new Server();
        // Avvia il server
        server.start();
    }
}