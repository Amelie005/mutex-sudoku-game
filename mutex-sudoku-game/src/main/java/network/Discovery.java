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
 * Finds open games in the local network via UDP broadcast.
 * This way nobody has to type in the host's IP address and port by hand.
 */
public class Discovery {

    /** Port on which all games send and receive their announcements. */
    public static final int DEFAULT_PORT = 50505;

    // Identifier, so other programs on the same port are ignored
    private static final String GAME_ID = "mutex-sudoku";
    private static final int ANNOUNCE_INTERVAL_MILLIS = 1000;
    private static final int MAX_PACKET_SIZE = 512;

    private static final Logger log = LoggerFactory.getLogger(Discovery.class);
    private static final Gson GSON = new Gson();

    /**
     * A game that was found.
     * @param peerId the ID of the host
     * @param address the IP address of the host
     * @param port the TCP port of the PeerServer
     */
    public record GameInfo(String peerId, String address, int port) {}

    // Content of a broadcast packet; the address comes from the packet itself and does not need to be included
    private record Announcement(String game, String peerId, int port) {}

    private final int discoveryPort;
    private volatile DatagramSocket announceSocket;

    /**
     * Uses the default port {@link #DEFAULT_PORT}.
     */
    public Discovery() {
        this(DEFAULT_PORT);
    }

    /**
     * @param discoveryPort the UDP port; must be the same for all players
     */
    public Discovery(int discoveryPort) {
        this.discoveryPort = discoveryPort;
    }

    /**
     * Announces our own game in the local network every second.
     * Calling it again replaces the previous announcement.
     * @param peerId our own ID
     * @param gamePort the port of our own PeerServer
     * @throws SocketException if no UDP socket can be opened
     */
    public synchronized void startAnnouncing(String peerId, int gamePort) throws SocketException {
        stopAnnouncing();
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        announceSocket = socket;
        byte[] data = GSON.toJson(new Announcement(GAME_ID, peerId, gamePort)).getBytes(StandardCharsets.UTF_8);
        // Daemon, so a running announcement does not keep the game from shutting down
        Thread.ofPlatform().daemon().name("discovery-announce").start(() -> announceLoop(socket, data));
    }

    /**
     * Stops the announcement, for example as soon as the game starts.
     */
    public synchronized void stopAnnouncing() {
        if (announceSocket != null) {
            announceSocket.close();
            announceSocket = null;
        }
    }

    /**
     * Searches for announced games for a while.
     * Blocks for that time, so do not call it on the UI thread.
     * @param timeoutMillis search duration; should be longer than one second so every host sends at least once
     * @return all games found, each one only once
     * @throws IOException if the discovery port cannot be opened
     */
    public List<GameInfo> search(int timeoutMillis) throws IOException {
        Set<GameInfo> found = new LinkedHashSet<>();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        try (DatagramSocket socket = new DatagramSocket(null)) {
            // Allows several searches on one machine, otherwise the port would be taken after the first one
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
        // To all devices in the local network, because the host does not know the guests yet
        DatagramPacket packet = new DatagramPacket(data, data.length,
                new InetSocketAddress("255.255.255.255", discoveryPort));
        while (!socket.isClosed()) {
            try {
                socket.send(packet);
                Thread.sleep(ANNOUNCE_INTERVAL_MILLIS);
            } catch (IOException e) {
                // Short Wi-Fi dropouts should not end the announcement for good
                if (!socket.isClosed()) {
                    log.warn("Could not send announcement", e);
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
            // Drop packets from other programs or with missing fields
            if (a == null || !GAME_ID.equals(a.game()) || a.peerId() == null || a.peerId().isBlank()
                    || a.port() < 1 || a.port() > 65535) {
                log.debug("Ignored foreign packet: {}", text);
                return null;
            }
            return new GameInfo(a.peerId(), packet.getAddress().getHostAddress(), a.port());
        } catch (RuntimeException e) {
            log.debug("Ignored invalid packet: {}", text, e);
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
