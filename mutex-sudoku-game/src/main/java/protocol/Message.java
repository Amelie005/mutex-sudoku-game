package protocol;

import game.Difficulty;

/**
 * Alle Nachrichten, die Peers austauschen.
 * Sealed, damit der Compiler meldet, wenn ein Switch einen neuen Nachrichtentyp vergisst.
 */
public sealed interface Message {

    // Der Timestamp entscheidet, wer gewinnt, wenn zwei Peers dieselbe Zelle gleichzeitig wollen
    record LockRequest(String peerId, int row, int col, long timestamp) implements Message {}

    record LockGrant(String peerId, int row, int col) implements Message {}

    record LockRelease(String peerId, int row, int col) implements Message {}

    record MoveResult(String peerId, int row, int col, int value, boolean correct) implements Message {}

    record JoinRequest(String peerId) implements Message {}

    // Seed und Schwierigkeit reichen, weil der Generator daraus bei jedem Peer dasselbe Board erzeugt
    record JoinAck(String peerId, long seed, Difficulty difficulty) implements Message {}
}
