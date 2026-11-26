package network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Needs an active network, because broadcasts do not work over localhost alone.
 */
@Timeout(10)
class DiscoveryTest {

    private int discoveryPort;
    private Discovery host;

    @BeforeEach
    void setUp() {
        // Random port, so a game running on the default port does not interfere with the test
        discoveryPort = ThreadLocalRandom.current().nextInt(51000, 59000);
        host = new Discovery(discoveryPort);
    }

    @AfterEach
    void tearDown() {
        host.stopAnnouncing();
    }

    @Test
    void searchWithoutHostFindsNothing() throws Exception {
        assertTrue(new Discovery(discoveryPort).search(1200).isEmpty());
    }

    @Test
    void announcedGameIsFoundOnce() throws Exception {
        host.startAnnouncing("host", 4242);

        // Longer than two announcements, so the game arrives more than once
        List<Discovery.GameInfo> games = new Discovery(discoveryPort).search(2500);

        assertEquals(1, games.size());
        assertEquals("host", games.get(0).peerId());
        assertEquals(4242, games.get(0).port());
    }

    @Test
    void stoppedGameIsNotFound() throws Exception {
        host.startAnnouncing("host", 4242);
        host.stopAnnouncing();
        Thread.sleep(300);

        assertTrue(new Discovery(discoveryPort).search(1500).isEmpty());
    }
}
