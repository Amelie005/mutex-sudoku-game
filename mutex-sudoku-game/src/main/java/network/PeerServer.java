package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accepts connections from other peers and hands them to the PeerManager.
 */
public class PeerServer {

    private static final Logger log = LoggerFactory.getLogger(PeerServer.class);

    private final ServerSocket serverSocket;
    private final PeerManager peerManager;

    /**
     * Binds the port, but only accepts connections after start().
     * @param port the port; 0 picks a free one automatically
     * @param peerManager receives all successfully connected peers
     * @throws IOException if the port cannot be bound
     */
    public PeerServer(int port, PeerManager peerManager) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.peerManager = peerManager;
    }

    /**
     * Starts a thread that accepts incoming connections.
     */
    public void start() {
        // Daemon, so an open server does not keep the game from shutting down
        Thread.ofPlatform().daemon().name("peer-server-" + getPort()).start(this::acceptLoop);
    }

    /**
     * Returns the bound port.
     * Needed because with port 0 the operating system picks the port.
     * @return the port of the server
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Stops accepting new connections.
     * Existing peers stay connected; the PeerManager manages them.
     */
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.warn("Server-Socket konnte nicht geschlossen werden", e);
        }
    }

    private void acceptLoop() {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                // A separate thread, because a silent peer would otherwise lock out everyone else until the timeout
                Thread.ofPlatform().daemon()
                        .name("peer-handshake-" + socket.getRemoteSocketAddress())
                        .start(() -> handshake(socket));
            }
        } catch (IOException e) {
            // After our own close() the interruption is intended and not an error
            if (!serverSocket.isClosed()) {
                log.error("Server nimmt keine Verbindungen mehr an", e);
            }
        }
    }

    private void handshake(Socket socket) {
        try {
            String peerId = peerManager.addIncomingConnection(new PeerConnection(socket));
            log.info("Peer {} verbunden von {}", peerId, socket.getRemoteSocketAddress());
        } catch (IOException e) {
            log.warn("Verbindung von {} abgelehnt", socket.getRemoteSocketAddress(), e);
            closeQuietly(socket);
        }
    }

    // Otherwise the socket stays open if the PeerConnection constructor already fails
    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            log.debug("Socket konnte nicht geschlossen werden", e);
        }
    }
}
