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

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

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
                StreamingEvent event = parser.next();
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

            List<StreamingEvent> events = new ArrayList<>();

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
            assertEquals(StreamingEventType.START_OBJECT, events.get(0).getType());

            // Verify 'services' field identification via our lookahead pairs
            assertEquals(StreamingEventType.FIELD_NAME, events.get(1).getType());
            assertEquals("services", events.get(1).getContent());

            // Verify nested structure matches indent tracking shifts
            assertEquals(StreamingEventType.START_OBJECT, events.get(2).getType());
            assertEquals(StreamingEventType.FIELD_NAME, events.get(3).getType());
            assertEquals("web", events.get(3).getContent());

            // Verify final structural collapse drains completely
            assertEquals(StreamingEventType.END_DOCUMENT, events.get(events.size() - 1).getType());
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
            List<StreamingEvent> events = new ArrayList<>();

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
            assertEquals(StreamingEventType.START_OBJECT, events.get(0).getType());

            // 2. Simple Field "project: Cascara"
            assertEquals(StreamingEventType.FIELD_NAME, events.get(1).getType());
            assertEquals("project", events.get(1).getContent());
            assertEquals(StreamingEventType.VALUE_SCALAR, events.get(2).getType());
            assertEquals("Cascara", events.get(2).getContent());

            // 3. Block Sequence Key "targets:"
            assertEquals(StreamingEventType.FIELD_NAME, events.get(3).getType());
            assertEquals("targets", events.get(3).getContent());

            // 4. Lookahead should detect '-' following the indent and open an array
            assertEquals(StreamingEventType.START_ARRAY, events.get(4).getType());

            // 5. Sequence Elements
            assertEquals(StreamingEventType.VALUE_SCALAR, events.get(5).getType());
            assertEquals("macos", events.get(5).getContent());

            assertEquals(StreamingEventType.VALUE_SCALAR, events.get(6).getType());
            assertEquals("windows", events.get(6).getContent());

            // 6. Tail End Unwinding
            // The dedent/EOF collapse should close the array, then the root object, then end the doc
            int total = events.size();
            assertEquals(StreamingEventType.END_ARRAY, events.get(total - 3).getType());
            assertEquals(StreamingEventType.END_OBJECT, events.get(total - 2).getType());
            assertEquals(StreamingEventType.END_DOCUMENT, events.get(total - 1).getType());
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
            List<StreamingEvent> events = new ArrayList<>();
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
            assertEquals(StreamingEventType.START_OBJECT, events.get(0).getType());

            // 2. Explicit Key processing
            // '?' changes the scalar interpretation to a FIELD_NAME even without a trailing colon on the same token line
            assertEquals(StreamingEventType.FIELD_NAME, events.get(1).getType());
            assertEquals("explicit_key", events.get(1).getContent());

            // 3. Literal Block Scalar value matching ':' and '|'
            assertEquals(StreamingEventType.VALUE_SCALAR, events.get(2).getType());
            assertEquals("Literal block scalar\nretains newlines.\n", events.get(2).getContent());

            // 4. Folded Block Scalar key/value pairing
            assertEquals(StreamingEventType.FIELD_NAME, events.get(3).getType());
            assertEquals("folded_key", events.get(3).getContent());

            assertEquals(StreamingEventType.VALUE_SCALAR, events.get(4).getType());
            assertEquals("Folded block scalar removes single newlines.\n", events.get(4).getContent());

            // 5. Unwinding
            int total = events.size();
            assertEquals(StreamingEventType.END_OBJECT, events.get(total - 2).getType());
            assertEquals(StreamingEventType.END_DOCUMENT, events.get(total - 1).getType());
        }
    }
}