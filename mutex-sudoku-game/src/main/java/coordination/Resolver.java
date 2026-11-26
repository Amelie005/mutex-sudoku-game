package coordination;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import protocol.Message;

/**
 * Decides who wins the game.
 * Runs only on the host, because only the host receives the reports of all players.
 * Whoever's PlayerFinished reaches the host first wins: the clocks of different
 * machines are not in sync, but the order of arrival at the host is unambiguous.
 */
public class Resolver {

    private final Set<String> players;
    private final Set<String> lost = new HashSet<>();
    private boolean gameOver;

    /**
     * @param playerIds all players at the start of the game, including the host itself
     */
    public Resolver(Collection<String> playerIds) {
        if (playerIds.isEmpty()) {
            throw new IllegalArgumentException("Ein Spiel braucht mindestens einen Spieler");
        }
        this.players = Set.copyOf(playerIds);
    }

    /**
     * Processes a player's report, including the host's own.
     * Other messages, unknown players and anything after the game has ended are ignored.
     * @param message a received or self-created message
     * @return the result to send to everyone, as soon as the game is decided
     */
    // Synchronized, because reports from different players arrive in different listener threads
    public synchronized Optional<Message.GameOver> handle(Message message) {
        if (gameOver) {
            return Optional.empty();
        }
        return switch (message) {
            case Message.PlayerFinished finished when isStillPlaying(finished.peerId()) ->
                    end(finished.peerId());
            case Message.PlayerLost loser when isStillPlaying(loser.peerId()) -> {
                lost.add(loser.peerId());
                // If everyone has lost, there is no winner left to wait for
                yield lost.size() == players.size() ? end(null) : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /**
     * @return true as soon as a result has been decided
     */
    public synchronized boolean isGameOver() {
        return gameOver;
    }

    // A player who already lost must not win with a late PlayerFinished
    private boolean isStillPlaying(String peerId) {
        return players.contains(peerId) && !lost.contains(peerId);
    }

    private Optional<Message.GameOver> end(String winnerId) {
        gameOver = true;
        return Optional.of(new Message.GameOver(winnerId));
    }
}
