package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.streaming.Event;
import io.github.qishr.cascara.common.lang.streaming.EventType;
import io.github.qishr.cascara.lang.yaml.YamlOptions;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class YamlPullParserTest {

    @Test
    public void testNoCrash() throws Exception {
        String yaml = "a: b";

        YamlOptions options = new YamlOptions().setIncludeComments(false);
        Reporter reporter = new StandardReporter().setLevel(Level.TRACE);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = new YamlPullParser(inputStream)) {
            parser.setOptions(options).setReporter(reporter);
            while (parser.hasNext()) {
                Event event = parser.next();
                assertNotNull(event);
            }
        }
    }

    @Test
    public void testNestedMappingStreaming() throws Exception {
        String yaml = """
            # Project Configuration
            services:
              web:
                port: 8080
            """;

        YamlOptions options = new YamlOptions().setIncludeComments(false);
        Reporter reporter = new StandardReporter().setLevel(Level.TRACE);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = new YamlPullParser(inputStream)) {

            List<Event> events = new ArrayList<>();

            parser.setOptions(options);
            parser.setReporter(reporter);
            // parser.forEachRemaining(events::add);
            parser.forEachRemaining(e ->{
                if (e!=null) {
                    reporter.debug("Event: " + e.getType() + ": " + e.getContent());
                    events.add(e);
                }
            });

            // Assertions to verify our translated token-stream boundaries
            assertFalse(events.isEmpty());

            // First structural marker for the root level mapping block
            assertEquals(EventType.START_OBJECT, events.get(0).getType());

            // Verify 'services' field identification via our lookahead pairs
            assertEquals(EventType.FIELD_NAME, events.get(1).getType());
            assertEquals("services", events.get(1).getContent());

            // Verify nested structure matches indent tracking shifts
            assertEquals(EventType.START_OBJECT, events.get(2).getType());
            assertEquals(EventType.FIELD_NAME, events.get(3).getType());
            assertEquals("web", events.get(3).getContent());

            // Verify final structural collapse drains completely
            assertEquals(EventType.END_DOCUMENT, events.get(events.size() - 1).getType());
        }
    }

    @Test
    public void testMixedMappingAndSequenceStreaming() throws Exception {
        String yaml = """
            project: Cascara
            targets:
              - macos
              - windows
            """;

        YamlOptions options = new YamlOptions().setIncludeComments(false);
        Reporter reporter = new StandardReporter().setLevel(Level.TRACE);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = new YamlPullParser(inputStream)) {
            List<Event> events = new ArrayList<>();

            parser.setOptions(options);
            parser.setReporter(reporter);
            parser.forEachRemaining(e ->{
                if (e!=null) {
                    reporter.debug("Event: " + e.getType() + ": " + e.getContent());
                    events.add(e);
                }
            });

            // Assertions to verify the full streaming event lifecycle
            assertFalse(events.isEmpty());

            // 1. Root Object Context
            assertEquals(EventType.START_OBJECT, events.get(0).getType());

            // 2. Simple Field "project: Cascara"
            assertEquals(EventType.FIELD_NAME, events.get(1).getType());
            assertEquals("project", events.get(1).getContent());
            assertEquals(EventType.VALUE_SCALAR, events.get(2).getType());
            assertEquals("Cascara", events.get(2).getContent());

            // 3. Block Sequence Key "targets:"
            assertEquals(EventType.FIELD_NAME, events.get(3).getType());
            assertEquals("targets", events.get(3).getContent());

            // 4. Lookahead should detect '-' following the indent and open an array
            assertEquals(EventType.START_ARRAY, events.get(4).getType());

            // 5. Sequence Elements
            assertEquals(EventType.VALUE_SCALAR, events.get(5).getType());
            assertEquals("macos", events.get(5).getContent());

            assertEquals(EventType.VALUE_SCALAR, events.get(6).getType());
            assertEquals("windows", events.get(6).getContent());

            // 6. Tail End Unwinding
            // The dedent/EOF collapse should close the array, then the root object, then end the doc
            int total = events.size();
            assertEquals(EventType.END_ARRAY, events.get(total - 3).getType());
            assertEquals(EventType.END_OBJECT, events.get(total - 2).getType());
            assertEquals(EventType.END_DOCUMENT, events.get(total - 1).getType());
        }
    }

    @Test
    public void testExplicitKeysAndBlockScalars() throws Exception {
        String yaml = """
            ? explicit_key
            : |
              Literal block scalar
              retains newlines.
            folded_key: >
              Folded block scalar
              removes single newlines.
            """;

        YamlOptions options = new YamlOptions().setIncludeComments(false);
        Reporter reporter = new StandardReporter().setLevel(Level.TRACE);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = new YamlPullParser(inputStream)) {
            List<Event> events = new ArrayList<>();
            parser.setOptions(options);
            parser.setReporter(reporter);
            // parser.forEachRemaining(events::add);
            parser.forEachRemaining(e ->{
                if (e!=null) {
                    reporter.debug("Event: " + e.getType() + ": " + e.getContent());
                    events.add(e);
                }
            });

            assertFalse(events.isEmpty());

            // 1. Document start
            assertEquals(EventType.START_OBJECT, events.get(0).getType());

            // 2. Explicit Key processing
            // '?' changes the scalar interpretation to a FIELD_NAME even without a trailing colon on the same token line
            assertEquals(EventType.FIELD_NAME, events.get(1).getType());
            assertEquals("explicit_key", events.get(1).getContent());

            // 3. Literal Block Scalar value matching ':' and '|'
            assertEquals(EventType.VALUE_SCALAR, events.get(2).getType());
            assertEquals("Literal block scalar\nretains newlines.\n", events.get(2).getContent());

            // 4. Folded Block Scalar key/value pairing
            assertEquals(EventType.FIELD_NAME, events.get(3).getType());
            assertEquals("folded_key", events.get(3).getContent());

            assertEquals(EventType.VALUE_SCALAR, events.get(4).getType());
            assertEquals("Folded block scalar removes single newlines.\n", events.get(4).getContent());

            // 5. Unwinding
            int total = events.size();
            assertEquals(EventType.END_OBJECT, events.get(total - 2).getType());
            assertEquals(EventType.END_DOCUMENT, events.get(total - 1).getType());
        }
    }
}