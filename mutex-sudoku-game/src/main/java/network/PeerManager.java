package network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import protocol.Message;

/**
 * Verwaltet die Verbindungen zu allen Peers.
 * So muss die Spiellogik nur Peer-IDs kennen und nichts über Sockets wissen.
 */
public class PeerManager {

    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    // Begrenzt, damit ein stummer Peer den wartenden Thread nicht dauerhaft blockiert
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 5000;

    // Concurrent, weil jeder Listener-Thread beim Verbindungsabbruch die Map ändert
    private final ConcurrentHashMap<String, PeerConnection> connections = new ConcurrentHashMap<>();

    // Volatile, weil der Callback aus einem anderen Thread gesetzt wird als die Listener lesen
    private volatile Consumer<Message> onMessageReceived =
            message -> log.debug("Kein Callback gesetzt, Nachricht verworfen: {}", message);

    /**
     * Registriert einen Peer und beginnt, seine Nachrichten zu empfangen.
     * Eine bestehende Verbindung mit derselben ID wird ersetzt.
     * @param peerId die ID des Peers
     * @param connection die Verbindung zum Peer
     */
    public void addConnection(String peerId, PeerConnection connection) {
        register(peerId, connection);
        listen(peerId, connection);
    }

    /**
     * Registriert einen Peer, der sich selbst verbunden hat.
     * Seine ID steht erst in der ersten Nachricht, darum muss das eine JoinRequest sein.
     * Blockiert bis zum Handshake-Timeout, also nicht direkt in der Accept-Schleife aufrufen.
     * @param connection die neue Verbindung
     * @return die ID des Peers
     * @throws IOException wenn der Handshake scheitert, die Verbindung ist dann geschlossen
     */
    public String addIncomingConnection(PeerConnection connection) throws IOException {
        Message.JoinRequest join = readHandshake(connection, Message.JoinRequest.class, Message.JoinRequest::peerId);
        admit(join.peerId(), connection, join);
        return join.peerId();
    }

    /**
     * Verbindet sich mit einem anderen Peer und tritt seinem Spiel bei.
     * Der JoinAck mit Seed und Schwierigkeit kommt wie jede Nachricht über den Callback.
     * Blockiert bis zu den Timeouts, also nicht im UI-Thread aufrufen.
     * @param host Adresse des Peers
     * @param port Port des Peers
     * @param ownPeerId die eigene ID
     * @return die ID des anderen Peers
     * @throws IOException wenn Verbindung oder Handshake scheitern
     */
    public String connectTo(String host, int port, String ownPeerId) throws IOException {
        PeerConnection connection = open(host, port);
        try {
            connection.send(new Message.JoinRequest(ownPeerId));
        } catch (IOException e) {
            connection.close();
            throw e;
        }
        Message.JoinAck ack = readHandshake(connection, Message.JoinAck.class, Message.JoinAck::peerId);
        admit(ack.peerId(), connection, ack);
        return ack.peerId();
    }

    /**
     * Entfernt einen Peer und schließt seine Verbindung.
     * @param peerId die ID des Peers
     */
    public void removeConnection(String peerId) {
        PeerConnection connection = connections.remove(peerId);
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Schickt eine Nachricht an alle verbundenen Peers.
     * @param message die zu sendende Nachricht
     */
    public void broadcast(Message message) {
        connections.forEach((peerId, connection) -> send(peerId, connection, message));
    }

    /**
     * Schickt eine Nachricht an einen bestimmten Peer.
     * @param peerId die ID des Peers
     * @param message die zu sendende Nachricht
     */
    public void sendTo(String peerId, Message message) {
        PeerConnection connection = connections.get(peerId);
        if (connection == null) {
            log.warn("Peer {} ist nicht verbunden, Nachricht verworfen: {}", peerId, message);
            return;
        }
        send(peerId, connection, message);
    }

    /**
     * Setzt den Callback für eingehende Nachrichten.
     * Er läuft im Listener-Thread der jeweiligen Verbindung, nicht im UI-Thread.
     * @param onMessageReceived bekommt jede empfangene Nachricht
     */
    public void setOnMessageReceived(Consumer<Message> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    /**
     * Gibt die IDs aller verbundenen Peers zurück.
     * @return unveränderliche Momentaufnahme der Peer-IDs
     */
    public Set<String> getConnectedPeerIds() {
        // Kopie, weil keySet() direkt auf die Map zeigt und Aufrufer so Peers entfernen könnten
        return Set.copyOf(connections.keySet());
    }

    private PeerConnection open(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            // Ohne Timeout hängt connect bei einer unerreichbaren Adresse sehr lange
            socket.connect(new InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MILLIS);
            return new PeerConnection(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private <T extends Message> T readHandshake(PeerConnection connection, Class<T> type, Function<T, String> idOf)
            throws IOException {
        try {
            Message first = connection.receive(HANDSHAKE_TIMEOUT_MILLIS);
            if (type.isInstance(first)) {
                T message = type.cast(first);
                String peerId = idOf.apply(message);
                // Gson füllt fehlende Felder mit null, eine Map verträgt aber keine null-ID
                if (peerId != null && !peerId.isBlank()) {
                    return message;
                }
            }
            throw new IOException("Unerwartete erste Nachricht: " + first);
        } catch (IOException | IllegalArgumentException e) {
            connection.close();
            throw new IOException("Handshake fehlgeschlagen", e);
        }
    }

    private void admit(String peerId, PeerConnection connection, Message firstMessage) {
        // Vor dem Callback registrieren, damit er per sendTo direkt antworten kann
        register(peerId, connection);
        try {
            onMessageReceived.accept(firstMessage);
        } catch (RuntimeException e) {
            log.warn("Callback für erste Nachricht von {} fehlgeschlagen", peerId, e);
        }
        // Erst nach dem Callback starten, damit keine spätere Nachricht die erste überholt
        listen(peerId, connection);
    }

    private void register(String peerId, PeerConnection connection) {
        PeerConnection previous = connections.put(peerId, connection);
        if (previous != null) {
            previous.close();
        }
    }

    private void listen(String peerId, PeerConnection connection) {
        // Der Callback wird pro Nachricht gelesen, damit er auch nach addConnection gesetzt werden kann
        connection.startListening(
                message -> onMessageReceived.accept(message),
                () -> remove(peerId, connection));
    }

    private void send(String peerId, PeerConnection connection, Message message) {
        try {
            connection.send(message);
        } catch (IOException e) {
            // Ein toter Peer soll den Broadcast an die übrigen Peers nicht abbrechen
            log.warn("Senden an Peer {} fehlgeschlagen, Peer wird entfernt", peerId, e);
            remove(peerId, connection);
        }
    }

    private void remove(String peerId, PeerConnection connection) {
        // Nur genau diese Verbindung entfernen, ein Reconnect mit derselben ID soll bestehen bleiben
        connections.remove(peerId, connection);
        connection.close();
    }
}
