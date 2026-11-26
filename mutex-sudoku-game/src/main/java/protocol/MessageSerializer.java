package protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Wandelt Nachrichten in JSON um und zurück.
 * Das "type"-Feld ist nötig, weil der Empfänger sonst nicht weiß, welcher Record im JSON steckt.
 */
public final class MessageSerializer {

    private static final String TYPE_FIELD = "type";

    // Gson ist thread-safe, also reicht eine Instanz für alle Verbindungen
    private static final Gson GSON = new Gson();

    private MessageSerializer() {}

    /**
     * Wandelt eine Nachricht in einen einzeiligen JSON-String um.
     * @param message die zu sendende Nachricht
     * @return JSON der Nachricht inklusive "type"-Feld
     */
    public static String serialize(Message message) {
        JsonObject json = GSON.toJsonTree(message).getAsJsonObject();
        json.addProperty(TYPE_FIELD, typeOf(message).name());
        return GSON.toJson(json);
    }

    /**
     * Baut aus einem JSON-String wieder den passenden Record.
     * @param json das empfangene JSON
     * @return die Nachricht als passender Record
     * @throws IllegalArgumentException wenn das JSON keine gültige Nachricht ist
     */
    public static Message deserialize(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            MessageType type = MessageType.valueOf(obj.get(TYPE_FIELD).getAsString());
            return switch (type) {
                case LOCK_REQUEST -> GSON.fromJson(obj, Message.LockRequest.class);
                case LOCK_GRANT -> GSON.fromJson(obj, Message.LockGrant.class);
                case LOCK_RELEASE -> GSON.fromJson(obj, Message.LockRelease.class);
                case MOVE_RESULT -> GSON.fromJson(obj, Message.MoveResult.class);
                case JOIN_REQUEST -> GSON.fromJson(obj, Message.JoinRequest.class);
                case JOIN_ACK -> GSON.fromJson(obj, Message.JoinAck.class);
            };
        } catch (RuntimeException e) {
            // Die Daten kommen ungeprüft von fremden Peers: Der Aufrufer soll nur einen Fehlerfall abfangen müssen
            throw new IllegalArgumentException("Ungültige Nachricht: " + json, e);
        }
    }

    // Ohne default, damit ein neuer Record ohne Eintrag hier gar nicht erst kompiliert
    private static MessageType typeOf(Message message) {
        return switch (message) {
            case Message.LockRequest m -> MessageType.LOCK_REQUEST;
            case Message.LockGrant m -> MessageType.LOCK_GRANT;
            case Message.LockRelease m -> MessageType.LOCK_RELEASE;
            case Message.MoveResult m -> MessageType.MOVE_RESULT;
            case Message.JoinRequest m -> MessageType.JOIN_REQUEST;
            case Message.JoinAck m -> MessageType.JOIN_ACK;
        };
    }
}
