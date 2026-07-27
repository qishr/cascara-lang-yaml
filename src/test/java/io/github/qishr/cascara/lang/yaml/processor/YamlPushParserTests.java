// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


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

        assertEquals(StreamingEventType.START_STREAM, events.get(0).getType());
        assertEquals(StreamingEventType.START_DOCUMENT, events.get(1).getType());
        assertEquals(StreamingEventType.VALUE_SCALAR, events.get(2).getType());
        assertEquals("value", events.get(2).getContent());
    }


    @Test
    void testSimpleMap() {
        InputStream input = createStream("key: value");
        pushParser.parse(input, handler);

        List<StreamingEvent> events = handler.getEvents();
        assertFalse(events.isEmpty());

        assertEquals(StreamingEventType.START_STREAM, events.get(0).getType());
        assertEquals(StreamingEventType.START_DOCUMENT, events.get(1).getType());
        assertEquals(StreamingEventType.START_OBJECT, events.get(2).getType());

        assertEquals(StreamingEventType.FIELD_NAME, events.get(3).getType());
        assertEquals("key", events.get(3).getContent());

        assertEquals(StreamingEventType.VALUE_SCALAR, events.get(4).getType());
        assertEquals("value", events.get(4).getContent());
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

        long docStarts = events.stream()
                .filter(e -> e.getType() == StreamingEventType.START_DOCUMENT)
                .count();
        assertEquals(2, docStarts);

        assertTrue(events.stream().anyMatch(e ->
            e.getType() == StreamingEventType.VALUE_SCALAR &&
            "doc1".equals(e.getContent())
        ));

        assertTrue(events.stream().anyMatch(e ->
            e.getType() == StreamingEventType.VALUE_SCALAR &&
            "doc2".equals(e.getContent())
        ));

        assertEquals(StreamingEventType.END_STREAM,
                     events.get(events.size() - 1).getType());
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