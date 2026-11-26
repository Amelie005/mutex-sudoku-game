package protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Converts messages to JSON and back.
 * The "type" field is needed because the receiver would otherwise not know which record the JSON holds.
 */
public final class MessageSerializer {

    private static final String TYPE_FIELD = "type";

    // Gson is thread-safe, so one instance is enough for all connections
    private static final Gson GSON = new Gson();

    private MessageSerializer() {}

    /**
     * Converts a message into a single-line JSON string.
     * @param message the message to send
     * @return JSON of the message including the "type" field
     */
    public static String serialize(Message message) {
        JsonObject json = GSON.toJsonTree(message).getAsJsonObject();
        json.addProperty(TYPE_FIELD, typeOf(message).name());
        return GSON.toJson(json);
    }

    /**
     * Turns a JSON string back into the matching record.
     * @param json the received JSON
     * @return the message as the matching record
     * @throws IllegalArgumentException if the JSON is not a valid message
     */
    public static Message deserialize(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            MessageType type = MessageType.valueOf(obj.get(TYPE_FIELD).getAsString());
            return switch (type) {
                case JOIN_REQUEST -> GSON.fromJson(obj, Message.JoinRequest.class);
                case JOIN_ACK -> GSON.fromJson(obj, Message.JoinAck.class);
                case PLAYER_FINISHED -> GSON.fromJson(obj, Message.PlayerFinished.class);
                case PLAYER_LOST -> GSON.fromJson(obj, Message.PlayerLost.class);
                case GAME_OVER -> GSON.fromJson(obj, Message.GameOver.class);
            };
        } catch (RuntimeException e) {
            // The data comes unchecked from other peers: the caller should only have to handle one kind of error
            throw new IllegalArgumentException("Invalid message: " + json, e);
        }
    }

    // No default branch, so a new record without an entry here does not compile
    private static MessageType typeOf(Message message) {
        return switch (message) {
            case Message.JoinRequest m -> MessageType.JOIN_REQUEST;
            case Message.JoinAck m -> MessageType.JOIN_ACK;
            case Message.PlayerFinished m -> MessageType.PLAYER_FINISHED;
            case Message.PlayerLost m -> MessageType.PLAYER_LOST;
            case Message.GameOver m -> MessageType.GAME_OVER;
        };
    }
}
