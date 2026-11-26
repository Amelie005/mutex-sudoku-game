package protocol;

/**
 * Alle Nachrichtentypen des Protokolls.
 * Der Name landet als "type"-Feld im JSON, darum ändert Umbenennen das Protokoll zwischen den Peers.
 */
public enum MessageType {
    JOIN_REQUEST,
    JOIN_ACK,
    PLAYER_FINISHED,
    PLAYER_LOST
}
