package protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import game.Difficulty;

class MessageSerializerTest {

    static Stream<Message> allMessageTypes() {
        return Stream.of(
                new Message.JoinRequest("gast-ü"),
                // Largest long value, because JSON numbers can easily lose precision
                new Message.JoinAck("host", Long.MAX_VALUE, Difficulty.HARD),
                new Message.PlayerFinished("gast", 1_764_000_000_000L),
                new Message.PlayerLost("gast"),
                new Message.GameOver("gast"),
                // No winner, because everyone lost
                new Message.GameOver(null));
    }

    @ParameterizedTest
    @MethodSource("allMessageTypes")
    void roundTripKeepsMessageUnchanged(Message message) {
        assertEquals(message, MessageSerializer.deserialize(MessageSerializer.serialize(message)));
    }

    @ParameterizedTest
    @MethodSource("allMessageTypes")
    void serializedMessageIsOneLineWithType(Message message) {
        String json = MessageSerializer.serialize(message);
        // The network separates messages by line breaks, so the JSON must not contain one
        assertFalse(json.contains("\n"));
        assertTrue(json.contains("\"type\":"));
    }

    @Test
    void everyMessageTypeIsCovered() {
        long distinctTypes = allMessageTypes().map(Object::getClass).distinct().count();
        assertEquals(MessageType.values().length, distinctTypes);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "kein json",
            "[]",
            "{}",
            "{\"type\":\"GIBT_ES_NICHT\"}",
            "{\"type\":{}}",
            "{\"type\":\"JOIN_ACK\",\"seed\":\"keine Zahl\"}"
    })
    void invalidInputIsRejected(String json) {
        assertThrows(IllegalArgumentException.class, () -> MessageSerializer.deserialize(json));
    }
}
