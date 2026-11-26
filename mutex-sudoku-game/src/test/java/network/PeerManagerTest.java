package network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import game.Difficulty;
import protocol.Message;

/**
 * Testet PeerManager und PeerServer zusammen über echte Verbindungen auf localhost.
 */
// Höher als 5 Sekunden, weil ein Test den Handshake-Timeout abwartet
@Timeout(15)
class PeerManagerTest {

    private static final String LOCALHOST = "127.0.0.1";

    private PeerManager host;
    private PeerServer server;
    private final BlockingQueue<Message> atHost = new LinkedBlockingQueue<>();

    @BeforeEach
    void startHost() throws IOException {
        host = new PeerManager();
        // Antwortet wie der spätere GameController auf jede JoinRequest
        host.setOnMessageReceived(message -> {
            atHost.add(message);
            if (message instanceof Message.JoinRequest join) {
                host.sendTo(join.peerId(), new Message.JoinAck("host", 7L, Difficulty.EASY));
            }
        });
        server = new PeerServer(0, host);
        server.start();
    }

    @AfterEach
    void stopHost() {
        server.close();
        host.getConnectedPeerIds().forEach(host::removeConnection);
    }

    @Test
    void guestJoinsAndReceivesSeed() throws Exception {
        BlockingQueue<Message> atGuest = new LinkedBlockingQueue<>();
        PeerManager guest = guest(atGuest);

        assertEquals("host", guest.connectTo(LOCALHOST, server.getPort(), "gast"));

        assertEquals(new Message.JoinAck("host", 7L, Difficulty.EASY), atGuest.poll(2, TimeUnit.SECONDS));
        assertEquals(new Message.JoinRequest("gast"), atHost.poll(2, TimeUnit.SECONDS));
        assertEquals(Set.of("host"), guest.getConnectedPeerIds());
        assertTrue(waitFor(() -> host.getConnectedPeerIds().equals(Set.of("gast"))));
    }

    @Test
    void broadcastReachesAllGuests() throws Exception {
        BlockingQueue<Message> atFirst = new LinkedBlockingQueue<>();
        BlockingQueue<Message> atSecond = new LinkedBlockingQueue<>();
        guest(atFirst).connectTo(LOCALHOST, server.getPort(), "erster");
        guest(atSecond).connectTo(LOCALHOST, server.getPort(), "zweiter");
        atFirst.poll(2, TimeUnit.SECONDS);
        atSecond.poll(2, TimeUnit.SECONDS);

        Message finished = new Message.PlayerFinished("host", 123L);
        host.broadcast(finished);

        assertEquals(finished, atFirst.poll(2, TimeUnit.SECONDS));
        assertEquals(finished, atSecond.poll(2, TimeUnit.SECONDS));
    }

    @Test
    void guestCanSendToHost() throws Exception {
        PeerManager guest = guest(new LinkedBlockingQueue<>());
        guest.connectTo(LOCALHOST, server.getPort(), "gast");
        atHost.poll(2, TimeUnit.SECONDS);

        guest.sendTo("host", new Message.PlayerLost("gast"));

        assertEquals(new Message.PlayerLost("gast"), atHost.poll(2, TimeUnit.SECONDS));
    }

    @Test
    void disconnectedGuestIsRemoved() throws Exception {
        PeerManager guest = guest(new LinkedBlockingQueue<>());
        guest.connectTo(LOCALHOST, server.getPort(), "gast");
        assertTrue(waitFor(() -> host.getConnectedPeerIds().contains("gast")));

        guest.removeConnection("host");

        assertTrue(waitFor(() -> host.getConnectedPeerIds().isEmpty()));
    }

    @Test
    void invalidFirstMessageIsRejected() throws Exception {
        try (Socket socket = new Socket(LOCALHOST, server.getPort())) {
            new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8).println("kaputt");

            // -1 heißt: Der Server hat die Verbindung geschlossen
            assertEquals(-1, socket.getInputStream().read());
        }
        assertTrue(host.getConnectedPeerIds().isEmpty());
    }

    @Test
    void silentPeerDoesNotBlockOthers() throws Exception {
        try (Socket silent = new Socket(LOCALHOST, server.getPort())) {
            PeerManager guest = guest(new LinkedBlockingQueue<>());

            long start = System.currentTimeMillis();
            guest.connectTo(LOCALHOST, server.getPort(), "gast");

            assertTrue(System.currentTimeMillis() - start < 2000);
            // Nach dem Handshake-Timeout schließt der Server die stumme Verbindung
            assertEquals(-1, silent.getInputStream().read());
        }
    }

    @Test
    void connectToClosedPortFails() throws IOException {
        int closedPort;
        try (ServerSocket temporary = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = temporary.getLocalPort();
        }
        PeerManager guest = guest(new LinkedBlockingQueue<>());

        assertThrows(IOException.class, () -> guest.connectTo(LOCALHOST, closedPort, "gast"));
        assertTrue(guest.getConnectedPeerIds().isEmpty());
    }

    private static PeerManager guest(BlockingQueue<Message> inbox) {
        PeerManager guest = new PeerManager();
        guest.setOnMessageReceived(inbox::add);
        return guest;
    }

    // Abbruch durch Gegenstellen läuft in anderen Threads, darum kurz warten statt sofort prüfen
    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
