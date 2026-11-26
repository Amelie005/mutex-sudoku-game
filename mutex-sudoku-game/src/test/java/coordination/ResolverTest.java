package coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import protocol.Message;

class ResolverTest {

    private final Resolver resolver = new Resolver(List.of("host", "anna", "ben"));

    @Test
    void firstFinishedPlayerWins() {
        assertEquals(Optional.of(new Message.GameOver("anna")),
                resolver.handle(new Message.PlayerFinished("anna", 200L)));
        assertTrue(resolver.isGameOver());
    }

    @Test
    void arrivalOrderCountsNotTimestamp() {
        resolver.handle(new Message.PlayerFinished("anna", 500L));

        // Ben meldet einen früheren Zeitpunkt, kommt aber später an und gewinnt darum nicht
        assertEquals(Optional.empty(), resolver.handle(new Message.PlayerFinished("ben", 100L)));
    }

    @Test
    void nothingHappensAfterGameOver() {
        resolver.handle(new Message.PlayerFinished("anna", 1L));

        assertEquals(Optional.empty(), resolver.handle(new Message.PlayerFinished("host", 2L)));
        assertEquals(Optional.empty(), resolver.handle(new Message.PlayerLost("ben")));
    }

    @Test
    void playerWhoLostCannotWinLater() {
        resolver.handle(new Message.PlayerLost("anna"));

        assertEquals(Optional.empty(), resolver.handle(new Message.PlayerFinished("anna", 1L)));
        assertFalse(resolver.isGameOver());
    }

    @Test
    void remainingPlayerCanStillWin() {
        resolver.handle(new Message.PlayerLost("anna"));
        resolver.handle(new Message.PlayerLost("ben"));

        assertEquals(Optional.of(new Message.GameOver("host")),
                resolver.handle(new Message.PlayerFinished("host", 1L)));
    }

    @Test
    void allLostMeansNoWinner() {
        resolver.handle(new Message.PlayerLost("anna"));
        resolver.handle(new Message.PlayerLost("ben"));

        assertEquals(Optional.of(new Message.GameOver(null)), resolver.handle(new Message.PlayerLost("host")));
        assertTrue(resolver.isGameOver());
    }

    @Test
    void repeatedLossCountsOnce() {
        resolver.handle(new Message.PlayerLost("anna"));
        resolver.handle(new Message.PlayerLost("anna"));
        resolver.handle(new Message.PlayerLost("ben"));

        assertFalse(resolver.isGameOver());
    }

    @Test
    void unknownPlayerIsIgnored() {
        assertEquals(Optional.empty(), resolver.handle(new Message.PlayerFinished("fremd", 1L)));
        assertFalse(resolver.isGameOver());
    }

    @Test
    void otherMessagesAreIgnored() {
        assertEquals(Optional.empty(), resolver.handle(new Message.JoinRequest("anna")));
        assertEquals(Optional.empty(), resolver.handle(new Message.GameOver("anna")));
        assertFalse(resolver.isGameOver());
    }

    @Test
    void gameNeedsAtLeastOnePlayer() {
        assertThrows(IllegalArgumentException.class, () -> new Resolver(List.of()));
    }

    @Test
    void exactlyOneWinnerWhenAllFinishAtOnce() throws Exception {
        List<String> ids = java.util.stream.IntStream.range(0, 50).mapToObj(i -> "p" + i).toList();
        Resolver crowded = new Resolver(ids);
        Set<Message.GameOver> results = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);

        // Alle Spieler gleichzeitig, wie Nachrichten aus verschiedenen Listener-Threads
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (String id : ids) {
            pool.submit(() -> {
                start.await();
                crowded.handle(new Message.PlayerFinished(id, 1L)).ifPresent(results::add);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(1, results.size());
    }
}
