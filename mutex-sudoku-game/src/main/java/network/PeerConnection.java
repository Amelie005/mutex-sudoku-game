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
 * TCP connection to a single peer.
 * Every message is exactly one line of JSON, because TCP itself has no message boundaries.
 */
public class PeerConnection {

    private static final Logger log = LoggerFactory.getLogger(PeerConnection.class);

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    /**
     * Sets up the streams for an already connected socket.
     * @param socket the connected socket
     * @throws IOException if the streams cannot be opened
     */
    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        // Fixed UTF-8, otherwise the encoding would depend on the peer's operating system
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Sends a message to the peer as one line of JSON.
     * @param message the message to send
     * @throws IOException if the connection is broken
     */
    // Synchronized, because otherwise the lines of parallel senders would get mixed up
    public synchronized void send(Message message) throws IOException {
        writer.write(MessageSerializer.serialize(message));
        // Always \n instead of newLine(), so the protocol does not depend on the operating system
        writer.write('\n');
        // Without flush the message would stay in the buffer and never arrive
        writer.flush();
    }

    /**
     * Reads exactly one message, waiting at most until the timeout.
     * Only call this before startListening, otherwise both would take lines away from each other.
     * @param timeoutMillis maximum waiting time in milliseconds
     * @return the received message
     * @throws IOException if nothing arrives or the connection ends
     * @throws IllegalArgumentException if the line is not a valid message
     */
    public Message receive(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new EOFException("Connection closed by peer");
            }
            return MessageSerializer.deserialize(line);
        } finally {
            // Reset, otherwise the listener would stop during every pause in the game
            socket.setSoTimeout(0);
        }
    }

    /**
     * Starts a thread that passes incoming messages to the consumer.
     * @param onMessage receives every incoming message
     */
    public void startListening(Consumer<Message> onMessage) {
        startListening(onMessage, () -> {});
    }

    /**
     * Starts a thread that passes incoming messages to the consumer.
     * @param onMessage receives every incoming message
     * @param onDisconnect called once as soon as the connection has ended
     */
    public void startListening(Consumer<Message> onMessage, Runnable onDisconnect) {
        Thread listener = new Thread(() -> listen(onMessage, onDisconnect),
                "peer-listener-" + socket.getRemoteSocketAddress());
        // Daemon, so an open connection does not keep the game from shutting down
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Closes the socket. Calling it more than once is harmless.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            log.warn("Could not close socket", e);
        }
    }

    private void listen(Consumer<Message> onMessage, Runnable onDisconnect) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    onMessage.accept(MessageSerializer.deserialize(line));
                } catch (RuntimeException e) {
                    // One broken message should not end the whole connection
                    log.warn("Dropped invalid message: {}", line, e);
                }
            }
        } catch (IOException e) {
            // After our own close() the interruption is intended and not an error
            if (!socket.isClosed()) {
                log.warn("Connection to {} lost", socket.getRemoteSocketAddress(), e);
            }
        } finally {
            close();
            onDisconnect.run();
        }
    }
}
