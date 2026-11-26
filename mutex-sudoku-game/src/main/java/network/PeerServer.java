package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nimmt Verbindungen anderer Peers an und gibt sie an den PeerManager weiter.
 */
public class PeerServer {

    private static final Logger log = LoggerFactory.getLogger(PeerServer.class);

    private final ServerSocket serverSocket;
    private final PeerManager peerManager;

    /**
     * Belegt den Port, nimmt aber erst nach start() Verbindungen an.
     * @param port der Port, 0 wählt automatisch einen freien
     * @param peerManager bekommt alle erfolgreich verbundenen Peers
     * @throws IOException wenn der Port nicht belegt werden kann
     */
    public PeerServer(int port, PeerManager peerManager) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.peerManager = peerManager;
    }

    /**
     * Startet einen Thread, der eingehende Verbindungen annimmt.
     */
    public void start() {
        // Daemon, damit ein offener Server das Beenden des Spiels nicht blockiert
        Thread.ofPlatform().daemon().name("peer-server-" + getPort()).start(this::acceptLoop);
    }

    /**
     * Gibt den belegten Port zurück.
     * Nötig, weil bei Port 0 erst das Betriebssystem den Port festlegt.
     * @return der Port des Servers
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Nimmt keine neuen Verbindungen mehr an.
     * Bestehende Peers bleiben verbunden, die verwaltet der PeerManager.
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
                // Eigener Thread, weil ein stummer Peer sonst alle anderen bis zum Timeout aussperrt
                Thread.ofPlatform().daemon()
                        .name("peer-handshake-" + socket.getRemoteSocketAddress())
                        .start(() -> handshake(socket));
            }
        } catch (IOException e) {
            // Nach unserem eigenen close() ist der Abbruch gewollt und kein Fehler
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

    // Der Socket bleibt sonst offen, wenn schon der Konstruktor von PeerConnection scheitert
    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            log.debug("Socket konnte nicht geschlossen werden", e);
        }
    }
}
