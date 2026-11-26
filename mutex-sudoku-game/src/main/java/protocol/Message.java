package protocol;

import game.Difficulty;

/**
 * Alle Nachrichten, die Peers austauschen.
 * Sealed, damit der Compiler meldet, wenn ein Switch einen neuen Nachrichtentyp vergisst.
 */
public sealed interface Message {

    record JoinRequest(String peerId) implements Message {}

    // Seed und Schwierigkeit reichen, weil der Generator daraus bei jedem Peer dasselbe Board erzeugt
    record JoinAck(String peerId, long seed, Difficulty difficulty) implements Message {}

    // Der Zeitpunkt wird mitgeschickt, damit bei fast gleichzeitigem Fertigwerden entschieden werden kann
    record PlayerFinished(String peerId, long finishedAtMillis) implements Message {}

    // Keine Zahlen oder Züge, weil jeder auf seinem eigenen Board spielt und die anderen nichts sehen sollen
    record PlayerLost(String peerId) implements Message {}

    // Nur der Host verschickt das Ergebnis, damit alle denselben Gewinner anzeigen; null heißt, alle haben verloren
    record GameOver(String winnerId) implements Message {}
}
