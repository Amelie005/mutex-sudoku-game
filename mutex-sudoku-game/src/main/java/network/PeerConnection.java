package network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import protocol.Message;
import protocol.MessageSerializer;

/**
 * TCP-Verbindung zu einem einzelnen Peer.
 * Jede Nachricht ist genau eine Zeile JSON, weil TCP selbst keine Nachrichtengrenzen kennt.
 */
public class PeerConnection {

    private static final Logger log = LoggerFactory.getLogger(PeerConnection.class);

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    /**
     * Baut die Streams für einen bereits verbundenen Socket auf.
     * @param socket der verbundene Socket
     * @throws IOException wenn die Streams nicht geöffnet werden können
     */
    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        // UTF-8 fest vorgeben, sonst hängt die Kodierung vom Betriebssystem des Peers ab
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Schickt eine Nachricht als eine Zeile JSON an den Peer.
     * @param message die zu sendende Nachricht
     * @throws IOException wenn die Verbindung unterbrochen ist
     */
    // Synchronized, weil sich sonst die Zeilen paralleler Sender vermischen würden
    public synchronized void send(Message message) throws IOException {
        writer.write(MessageSerializer.serialize(message));
        // Fest \n statt newLine(), damit das Protokoll nicht vom Betriebssystem abhängt
        writer.write('\n');
        // Ohne flush bliebe die Nachricht im Puffer liegen und käme nie an
        writer.flush();
    }

    /**
     * Liest genau eine Nachricht und wartet dabei höchstens bis zum Timeout.
     * Nur vor startListening aufrufen, sonst nehmen sich beide gegenseitig die Zeilen weg.
     * @param timeoutMillis maximale Wartezeit in Millisekunden
     * @return die empfangene Nachricht
     * @throws IOException wenn nichts ankommt oder die Verbindung endet
     * @throws IllegalArgumentException wenn die Zeile keine gültige Nachricht ist
     */
    public Message receive(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new EOFException("Verbindung wurde vom Peer beendet");
            }
            return MessageSerializer.deserialize(line);
        } finally {
            // Zurücksetzen, sonst würde der Listener bei jeder Spielpause abbrechen
            socket.setSoTimeout(0);
        }
    }

    /**
     * Startet einen Thread, der eingehende Nachrichten an den Consumer gibt.
     * @param onMessage bekommt jede empfangene Nachricht
     */
    public void startListening(Consumer<Message> onMessage) {
        startListening(onMessage, () -> {});
    }

    /**
     * Startet einen Thread, der eingehende Nachrichten an den Consumer gibt.
     * @param onMessage bekommt jede empfangene Nachricht
     * @param onDisconnect wird einmal aufgerufen, sobald die Verbindung beendet ist
     */
    public void startListening(Consumer<Message> onMessage, Runnable onDisconnect) {
        Thread listener = new Thread(() -> listen(onMessage, onDisconnect),
                "peer-listener-" + socket.getRemoteSocketAddress());
        // Daemon, damit eine offene Verbindung das Beenden des Spiels nicht blockiert
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Schließt den Socket. Mehrfaches Aufrufen ist unkritisch.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            log.warn("Socket konnte nicht geschlossen werden", e);
        }
    }

    private void listen(Consumer<Message> onMessage, Runnable onDisconnect) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    onMessage.accept(MessageSerializer.deserialize(line));
                } catch (RuntimeException e) {
                    // Eine kaputte Nachricht soll nicht die ganze Verbindung beenden
                    log.warn("Nachricht verworfen: {}", line, e);
                }
            }
        } catch (IOException e) {
            // Nach unserem eigenen close() ist der Abbruch gewollt und kein Fehler
            if (!socket.isClosed()) {
                log.warn("Verbindung zu {} abgebrochen", socket.getRemoteSocketAddress(), e);
            }
        } finally {
            close();
            onDisconnect.run();
        }
    }
}
