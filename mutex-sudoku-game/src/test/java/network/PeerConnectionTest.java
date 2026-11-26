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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import protocol.Message;

// Timeout, so a bug in the network code cannot make the build hang forever
@Timeout(10)
class PeerConnectionTest {

    private Socket senderSocket;
    private PeerConnection sender;
    private PeerConnection receiver;

    @BeforeEach
    void connect() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            senderSocket = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
            sender = new PeerConnection(senderSocket);
            receiver = new PeerConnection(server.accept());
        }
    }

    @AfterEach
    void close() {
        sender.close();
        receiver.close();
    }

    @Test
    void sentMessageArrives() throws Exception {
        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        receiver.startListening(received::add);

        Message message = new Message.PlayerFinished("gast", 42L);
        sender.send(message);

        assertEquals(message, received.poll(2, TimeUnit.SECONDS));
    }

    @Test
    void invalidLineDoesNotEndConnection() throws Exception {
        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        receiver.startListening(received::add);

        // Write directly to the socket, because send() can only produce valid messages
        new PrintWriter(senderSocket.getOutputStream(), true, StandardCharsets.UTF_8).println("kaputt");
        Message message = new Message.PlayerLost("gast");
        sender.send(message);

        assertEquals(message, received.poll(2, TimeUnit.SECONDS));
    }

    @Test
    void parallelSendsDoNotMixLines() throws Exception {
        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        receiver.startListening(received::add);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 200; i++) {
            String id = "x".repeat(500) + i;
            pool.submit(() -> {
                sender.send(new Message.JoinRequest(id));
                return null;
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        // Mixed-up lines would not be valid JSON and would not arrive here
        for (int i = 0; i < 200; i++) {
            assertTrue(received.poll(2, TimeUnit.SECONDS) instanceof Message.JoinRequest);
        }
    }

    @Test
    void disconnectIsReported() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        receiver.startListening(message -> {}, disconnected::countDown);

        sender.close();

        assertTrue(disconnected.await(2, TimeUnit.SECONDS));
    }

    @Test
    void receiveTimesOutWhenNothingArrives() {
        assertThrows(IOException.class, () -> receiver.receive(200));
    }

    @Test
    void sendAfterCloseFails() {
        sender.close();
        assertThrows(IOException.class, () -> sender.send(new Message.PlayerLost("gast")));
    }
}
