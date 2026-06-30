package io.github.qishr.cascara.lang.yaml.processor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;
import io.github.qishr.cascara.common.lang.streaming.StreamHandler;

/// Test suite validating Event-Driven Push Parser behaviors across document boundaries,
/// collections, and streaming configurations using core Cascara Event types.
public class YamlPushParserTests {

    private TrackingStreamHandler handler;
    private YamlPushParser pushParser;

    @BeforeEach
    void setUp() {
        handler = new TrackingStreamHandler();
        pushParser = new YamlPushParser(); // SPI compliance check
    }

    private InputStream createStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testSimpleScalar() {
        InputStream input = createStream("value");
        pushParser.parse(input, handler);

        List<Event> events = handler.getEvents();
        assertFalse(events.isEmpty(), "Should produce streaming events");

        // Simple values are wrapped inside a root object context
        assertEquals(EventType.START_OBJECT, events.get(0).getType());
    }

    @Test
    void testSimpleMap() {
        InputStream input = createStream("key: value");
        pushParser.parse(input, handler);

        List<Event> events = handler.getEvents();
        assertFalse(events.isEmpty());

        assertEquals(EventType.START_OBJECT, events.get(0).getType());

        assertEquals(EventType.FIELD_NAME, events.get(1).getType());
        assertEquals("key", events.get(1).getContent());

        assertEquals(EventType.VALUE_SCALAR, events.get(2).getType());
        assertEquals("value", events.get(2).getContent());
    }

    @Test
    void testMultiDocumentStreamEvents() {
        String yaml = """
        ---
        doc1
        ---
        doc2
        """;
        InputStream input = createStream(yaml);

        pushParser.setReporter(new StandardReporter().setLevel(Level.TRACE));
        pushParser.getOptions().setMultiDocument(true);
        pushParser.parse(input, handler);

        List<Event> events = handler.getEvents();
        assertFalse(events.isEmpty());

        // Count standard document boundary completions if emitted by the engine
        long documentEnds = events.stream()
                .filter(e -> e.getType() == EventType.END_DOCUMENT)
                .count();

        assertTrue(events.stream().anyMatch(e -> e.getType() == EventType.START_OBJECT));
    }

    //
    // Test Infrastructure
    //

    private static class TrackingStreamHandler implements StreamHandler {
        private final List<Event> events = new ArrayList<>();

        public List<Event> getEvents() {
            return events;
        }

        @Override
        public void onEvent(Event event) {
            if (event != null) {
                events.add(event);
            }
        }
    }
}