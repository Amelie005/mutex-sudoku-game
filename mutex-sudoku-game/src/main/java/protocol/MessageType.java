package protocol;

/**
 * All message types of the protocol.
 * The name ends up as the "type" field in the JSON, so renaming one changes the protocol between peers.
 */
public enum MessageType {
    JOIN_REQUEST,
    JOIN_ACK,
    PLAYER_FINISHED,
    PLAYER_LOST,
    GAME_OVER
}
