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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

            new EventValidator(events).verbose()
                .expect(StreamingEventType.START_STREAM)
                .expect(StreamingEventType.START_DOCUMENT)
                .expect(StreamingEventType.START_OBJECT)
                .expect(StreamingEventType.FIELD_NAME, "services")
                .expect(StreamingEventType.START_OBJECT)
                .expect(StreamingEventType.FIELD_NAME, "web")
                .expect(StreamingEventType.START_OBJECT)
                .expect(StreamingEventType.FIELD_NAME, "port")
                .expect(StreamingEventType.VALUE_SCALAR, "8080")
                .expect(StreamingEventType.END_OBJECT)
                .expect(StreamingEventType.END_OBJECT)
                .expect(StreamingEventType.END_OBJECT)
                .expect(StreamingEventType.END_DOCUMENT)
                .expect(StreamingEventType.END_STREAM)
                .validate();
        }
    }

    @Test
    void testMixedMappingAndSequenceStreaming() throws Exception {
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

            parser.forEachRemaining(e -> {
                if (e != null) events.add(e);
            });

            new EventValidator(events).verbose()
                .expect(StreamingEventType.START_STREAM)
                .expect(StreamingEventType.START_DOCUMENT)
                .expect(StreamingEventType.START_OBJECT)
                .expect(StreamingEventType.FIELD_NAME, "project")
                .expect(StreamingEventType.VALUE_SCALAR, "Cascara")
                .expect(StreamingEventType.FIELD_NAME, "targets")
                .expect(StreamingEventType.START_ARRAY)
                .expect(StreamingEventType.VALUE_SCALAR, "macos")
                .expect(StreamingEventType.VALUE_SCALAR, "windows")
                .expect(StreamingEventType.END_ARRAY)
                .expect(StreamingEventType.END_OBJECT)
                .expect(StreamingEventType.END_DOCUMENT)
                .expect(StreamingEventType.END_STREAM)
                .validate();
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

            parser.forEachRemaining(e -> {
                if (e != null) events.add(e);
            });

            new EventValidator(events).verbose()
                .expect(StreamingEventType.START_STREAM)
                .expect(StreamingEventType.START_DOCUMENT)
                .expect(StreamingEventType.START_OBJECT)
                .expect(StreamingEventType.FIELD_NAME, "explicit_key")
                .expect(StreamingEventType.VALUE_SCALAR, "Literal block scalar\nretains newlines.\n")
                .expect(StreamingEventType.FIELD_NAME, "folded_key")
                .expect(StreamingEventType.VALUE_SCALAR, "Folded block scalar removes single newlines.\n")
                .expect(StreamingEventType.END_OBJECT)
                .expect(StreamingEventType.END_DOCUMENT)
                .expect(StreamingEventType.END_STREAM)
                .validate();
        }
    }
}