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
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
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

        List<StreamingEvent> events = handler.getEvents();
        assertFalse(events.isEmpty(), "Should produce streaming events");

        // Simple values are wrapped inside a root object context
        assertEquals(StreamingEventType.START_OBJECT, events.get(0).getType());
    }

    @Test
    void testSimpleMap() {
        InputStream input = createStream("key: value");
        pushParser.parse(input, handler);

        List<StreamingEvent> events = handler.getEvents();
        assertFalse(events.isEmpty());

        assertEquals(StreamingEventType.START_OBJECT, events.get(0).getType());

        assertEquals(StreamingEventType.FIELD_NAME, events.get(1).getType());
        assertEquals("key", events.get(1).getContent());

        assertEquals(StreamingEventType.VALUE_SCALAR, events.get(2).getType());
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

        List<StreamingEvent> events = handler.getEvents();
        assertFalse(events.isEmpty());

        // Count standard document boundary completions if emitted by the engine
        long documentEnds = events.stream()
                .filter(e -> e.getType() == StreamingEventType.END_DOCUMENT)
                .count();

        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamingEventType.START_OBJECT));
    }

    //
    // Test Infrastructure
    //

    private static class TrackingStreamHandler implements StreamHandler {
        private final List<StreamingEvent> events = new ArrayList<>();

        public List<StreamingEvent> getEvents() {
            return events;
        }

        @Override
        public void onEvent(StreamingEvent event) {
            if (event != null) {
                events.add(event);
            }
        }
    }
}