package protocol;

/**
 * Alle Nachrichtentypen des Protokolls.
 * Der Name landet als "type"-Feld im JSON, darum ändert Umbenennen das Protokoll zwischen den Peers.
 */
public enum MessageType {
    LOCK_REQUEST,
    LOCK_GRANT,
    LOCK_RELEASE,
    MOVE_RESULT,
    JOIN_REQUEST,
    JOIN_ACK
}
