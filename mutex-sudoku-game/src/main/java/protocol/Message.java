package protocol;

import game.Difficulty;

/**
 * All messages exchanged between peers.
 * Sealed, so the compiler reports any switch that forgets a new message type.
 */
public sealed interface Message {

    record JoinRequest(String peerId) implements Message {}

    // Seed and difficulty are enough, because the generator creates the same board from them on every peer
    record JoinAck(String peerId, long seed, Difficulty difficulty) implements Message {}

    // The time is sent along so the finish time can be shown or compared later
    record PlayerFinished(String peerId, long finishedAtMillis) implements Message {}

    // No numbers or moves, because everyone plays on their own board and must not see the others' entries
    record PlayerLost(String peerId) implements Message {}

    // Only the host sends the result, so everyone shows the same winner; null means everyone lost
    record GameOver(String winnerId) implements Message {}
}
