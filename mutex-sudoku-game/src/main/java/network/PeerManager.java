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
 * Manages the connections to all peers.
 * This way the game logic only needs to know peer IDs and nothing about sockets.
 */
public class PeerManager {

    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    // Limited, so a silent peer cannot block the waiting thread forever
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 5000;

    // Concurrent, because every listener thread changes the map when its connection drops
    private final ConcurrentHashMap<String, PeerConnection> connections = new ConcurrentHashMap<>();

    // Volatile, because the callback is set from a different thread than the listeners read it from
    private volatile Consumer<Message> onMessageReceived =
            message -> log.debug("Kein Callback gesetzt, Nachricht verworfen: {}", message);

    /**
     * Registers a peer and starts receiving its messages.
     * An existing connection with the same ID is replaced.
     * @param peerId the ID of the peer
     * @param connection the connection to the peer
     */
    public void addConnection(String peerId, PeerConnection connection) {
        register(peerId, connection);
        listen(peerId, connection);
    }

    /**
     * Registers a peer that connected to us.
     * Its ID is only in the first message, so that message must be a JoinRequest.
     * Blocks until the handshake timeout, so do not call it directly in the accept loop.
     * @param connection the new connection
     * @return the ID of the peer
     * @throws IOException if the handshake fails; the connection is closed in that case
     */
    public String addIncomingConnection(PeerConnection connection) throws IOException {
        Message.JoinRequest join = readHandshake(connection, Message.JoinRequest.class, Message.JoinRequest::peerId);
        admit(join.peerId(), connection, join);
        return join.peerId();
    }

    /**
     * Connects to another peer and joins its game.
     * The JoinAck with seed and difficulty arrives through the callback like any other message.
     * Blocks until the timeouts, so do not call it on the UI thread.
     * @param host address of the peer
     * @param port port of the peer
     * @param ownPeerId our own ID
     * @return the ID of the other peer
     * @throws IOException if the connection or the handshake fails
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
     * Removes a peer and closes its connection.
     * @param peerId the ID of the peer
     */
    public void removeConnection(String peerId) {
        PeerConnection connection = connections.remove(peerId);
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Sends a message to all connected peers.
     * @param message the message to send
     */
    public void broadcast(Message message) {
        connections.forEach((peerId, connection) -> send(peerId, connection, message));
    }

    /**
     * Sends a message to one specific peer.
     * @param peerId the ID of the peer
     * @param message the message to send
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
     * Sets the callback for incoming messages.
     * It runs on the listener thread of the connection, not on the UI thread.
     * @param onMessageReceived receives every incoming message
     */
    public void setOnMessageReceived(Consumer<Message> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    /**
     * Returns the IDs of all connected peers.
     * @return unmodifiable snapshot of the peer IDs
     */
    public Set<String> getConnectedPeerIds() {
        // A copy, because keySet() is a live view of the map and callers could remove peers through it
        return Set.copyOf(connections.keySet());
    }

    private PeerConnection open(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            // Without a timeout, connect hangs for a very long time on an unreachable address
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
                // Gson fills missing fields with null, but the map cannot hold a null ID
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
        // Register before the callback, so it can reply right away with sendTo
        register(peerId, connection);
        try {
            onMessageReceived.accept(firstMessage);
        } catch (RuntimeException e) {
            log.warn("Callback für erste Nachricht von {} fehlgeschlagen", peerId, e);
        }
        // Start only after the callback, so no later message can overtake the first one
        listen(peerId, connection);
    }

    private void register(String peerId, PeerConnection connection) {
        PeerConnection previous = connections.put(peerId, connection);
        if (previous != null) {
            previous.close();
        }
    }

    private void listen(String peerId, PeerConnection connection) {
        // The callback is read for every message, so it can also be set after addConnection
        connection.startListening(
                message -> onMessageReceived.accept(message),
                () -> remove(peerId, connection));
    }

    private void send(String peerId, PeerConnection connection, Message message) {
        try {
            connection.send(message);
        } catch (IOException e) {
            // One dead peer should not stop the broadcast to the remaining peers
            log.warn("Senden an Peer {} fehlgeschlagen, Peer wird entfernt", peerId, e);
            remove(peerId, connection);
        }
    }

    private void remove(String peerId, PeerConnection connection) {
        // Only remove exactly this connection, so a reconnect with the same ID stays registered
        connections.remove(peerId, connection);
        connection.close();
    }
}
