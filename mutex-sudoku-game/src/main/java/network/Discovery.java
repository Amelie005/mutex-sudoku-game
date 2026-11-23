package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Findet offene Spiele im lokalen Netz per UDP-Broadcast.
 * So muss niemand IP-Adresse und Port des Hosts von Hand eintippen.
 */
public class Discovery {

    /** Port, auf dem alle Spiele ihre Ankündigungen senden und empfangen. */
    public static final int DEFAULT_PORT = 50505;

    // Erkennungsmerkmal, damit fremde Programme auf demselben Port ignoriert werden
    private static final String GAME_ID = "mutex-sudoku";
    private static final int ANNOUNCE_INTERVAL_MILLIS = 1000;
    private static final int MAX_PACKET_SIZE = 512;

    private static final Logger log = LoggerFactory.getLogger(Discovery.class);
    private static final Gson GSON = new Gson();

    /**
     * Ein gefundenes Spiel.
     * @param peerId die ID des Hosts
     * @param address die IP-Adresse des Hosts
     * @param port der TCP-Port des PeerServers
     */
    public record GameInfo(String peerId, String address, int port) {}

    // Inhalt eines Broadcast-Pakets, die Adresse kommt aus dem Paket selbst und muss nicht mit
    private record Announcement(String game, String peerId, int port) {}

    private final int discoveryPort;
    private volatile DatagramSocket announceSocket;

    /**
     * Nutzt den Standard-Port {@link #DEFAULT_PORT}.
     */
    public Discovery() {
        this(DEFAULT_PORT);
    }

    /**
     * @param discoveryPort der UDP-Port, muss bei allen Spielern gleich sein
     */
    public Discovery(int discoveryPort) {
        this.discoveryPort = discoveryPort;
    }

    /**
     * Kündigt das eigene Spiel jede Sekunde im lokalen Netz an.
     * Ein erneuter Aufruf ersetzt die vorherige Ankündigung.
     * @param peerId die eigene ID
     * @param gamePort der Port des eigenen PeerServers
     * @throws SocketException wenn kein UDP-Socket geöffnet werden kann
     */
    public synchronized void startAnnouncing(String peerId, int gamePort) throws SocketException {
        stopAnnouncing();
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        announceSocket = socket;
        byte[] data = GSON.toJson(new Announcement(GAME_ID, peerId, gamePort)).getBytes(StandardCharsets.UTF_8);
        // Daemon, damit eine laufende Ankündigung das Beenden des Spiels nicht blockiert
        Thread.ofPlatform().daemon().name("discovery-announce").start(() -> announceLoop(socket, data));
    }

    /**
     * Beendet die Ankündigung, zum Beispiel sobald das Spiel startet.
     */
    public synchronized void stopAnnouncing() {
        if (announceSocket != null) {
            announceSocket.close();
            announceSocket = null;
        }
    }

    /**
     * Sucht eine Zeit lang nach angekündigten Spielen.
     * Blockiert so lange, also nicht im UI-Thread aufrufen.
     * @param timeoutMillis Suchdauer, sollte länger als eine Sekunde sein, damit jeder Host einmal sendet
     * @return alle gefundenen Spiele, jedes nur einmal
     * @throws IOException wenn der Discovery-Port nicht geöffnet werden kann
     */
    public List<GameInfo> search(int timeoutMillis) throws IOException {
        Set<GameInfo> found = new LinkedHashSet<>();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        try (DatagramSocket socket = new DatagramSocket(null)) {
            // Erlaubt mehrere Suchende auf einem Rechner, sonst wäre der Port nach dem ersten belegt
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(discoveryPort));
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            long remaining;
            while ((remaining = deadline - System.currentTimeMillis()) > 0) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout((int) remaining);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                GameInfo game = parse(packet);
                if (game != null) {
                    found.add(game);
                }
            }
        }
        return List.copyOf(found);
    }

    private void announceLoop(DatagramSocket socket, byte[] data) {
        // An alle Geräte im lokalen Netz, weil der Host die Gäste noch nicht kennt
        DatagramPacket packet = new DatagramPacket(data, data.length,
                new InetSocketAddress("255.255.255.255", discoveryPort));
        while (!socket.isClosed()) {
            try {
                socket.send(packet);
                Thread.sleep(ANNOUNCE_INTERVAL_MILLIS);
            } catch (IOException e) {
                // Kurze WLAN-Aussetzer sollen die Ankündigung nicht dauerhaft beenden
                if (!socket.isClosed()) {
                    log.warn("Ankündigung konnte nicht gesendet werden", e);
                    sleepQuietly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private GameInfo parse(DatagramPacket packet) {
        String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
        try {
            Announcement a = GSON.fromJson(text, Announcement.class);
            // Pakete fremder Programme oder mit fehlenden Feldern verwerfen
            if (a == null || !GAME_ID.equals(a.game()) || a.peerId() == null || a.peerId().isBlank()
                    || a.port() < 1 || a.port() > 65535) {
                log.debug("Fremdes Paket ignoriert: {}", text);
                return null;
            }
            return new GameInfo(a.peerId(), packet.getAddress().getHostAddress(), a.port());
        } catch (RuntimeException e) {
            log.debug("Ungültiges Paket ignoriert: {}", text, e);
            return null;
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(ANNOUNCE_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
