package coordination;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import protocol.Message;

/**
 * Entscheidet, wer das Spiel gewinnt.
 * Läuft nur beim Host, weil nur er die Meldungen aller Spieler bekommt.
 * Es gewinnt, wessen PlayerFinished zuerst beim Host ankommt: Die Uhren verschiedener
 * Rechner gehen nicht gleich, die Reihenfolge beim Host ist dagegen eindeutig.
 */
public class Resolver {

    private final Set<String> players;
    private final Set<String> lost = new HashSet<>();
    private boolean gameOver;

    /**
     * @param playerIds alle Spieler beim Spielstart, der Host selbst eingeschlossen
     */
    public Resolver(Collection<String> playerIds) {
        if (playerIds.isEmpty()) {
            throw new IllegalArgumentException("Ein Spiel braucht mindestens einen Spieler");
        }
        this.players = Set.copyOf(playerIds);
    }

    /**
     * Verarbeitet die Meldung eines Spielers, auch die des Hosts selbst.
     * Andere Nachrichten, unbekannte Spieler und alles nach dem Spielende werden ignoriert.
     * @param message eine empfangene oder selbst erzeugte Nachricht
     * @return das Ergebnis zum Verschicken an alle, sobald das Spiel entschieden ist
     */
    // Synchronized, weil Meldungen verschiedener Spieler in verschiedenen Listener-Threads ankommen
    public synchronized Optional<Message.GameOver> handle(Message message) {
        if (gameOver) {
            return Optional.empty();
        }
        return switch (message) {
            case Message.PlayerFinished finished when isStillPlaying(finished.peerId()) ->
                    end(finished.peerId());
            case Message.PlayerLost loser when isStillPlaying(loser.peerId()) -> {
                lost.add(loser.peerId());
                // Haben alle verloren, gibt es keinen Gewinner mehr, auf den man warten könnte
                yield lost.size() == players.size() ? end(null) : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /**
     * @return true, sobald ein Ergebnis feststeht
     */
    public synchronized boolean isGameOver() {
        return gameOver;
    }

    // Wer schon verloren hat, darf mit einer späten PlayerFinished nicht noch gewinnen
    private boolean isStillPlaying(String peerId) {
        return players.contains(peerId) && !lost.contains(peerId);
    }

    private Optional<Message.GameOver> end(String winnerId) {
        gameOver = true;
        return Optional.of(new Message.GameOver(winnerId));
    }
}
