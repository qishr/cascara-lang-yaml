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

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.streaming.StreamingEvent;
import io.github.qishr.cascara.common.lang.streaming.StreamingEventType;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.streaming.YamlStreamingEvent;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class YamlPullParserTest extends BasePullParserTest {
    @Test
    public void testExplicitDocStart() throws Exception {
        String yaml = "---\nscalar";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());

            YamlStreamingEvent doc = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_DOCUMENT, doc.getType());
            assertEquals("---", doc.getContent());

            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testExplicitDocEnd() throws Exception {
        String yaml = "---\nscalar\n...\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());

            YamlStreamingEvent doc = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.END_DOCUMENT, doc.getType());
            assertEquals("...", doc.getContent());

            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testImplicitDocStart() throws Exception {
        String yaml = "!!str scalar";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());

            YamlStreamingEvent doc = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_DOCUMENT, doc.getType());
            assertEquals("", doc.getContent());

            YamlStreamingEvent body = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, body.getType());
            assertEquals("!!str", body.getTag());
            assertEquals("tag:yaml.org,2002:str", body.getResolvedTag());

            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testAnchorOnScalar() throws Exception {
        String yaml = "a: &a1 b\nc: d\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {

            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());

            if (parser.next() instanceof YamlStreamingEvent event) {
                assertEquals(StreamingEventType.VALUE_SCALAR, event.getType());
                String anchor = event.getAnchor();
                assertEquals("a1", anchor);
            }
        }
    }

    @Test
    public void testTagInSequence() throws Exception {
        String yaml = "- !!str s1\n- !!str s2\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());

            YamlStreamingEvent item1 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, item1.getType());
            assertEquals("s1", item1.getContent());
            assertEquals("!!str", item1.getTag());
            assertEquals("tag:yaml.org,2002:str", item1.getResolvedTag());

            YamlStreamingEvent item2 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, item2.getType());
            assertEquals("s2", item2.getContent());
            assertEquals("!!str", item2.getTag());
            assertEquals("tag:yaml.org,2002:str", item2.getResolvedTag());

            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testAnchorOnMap() throws Exception {
        String yaml = "a: &a1\n  c: d\n";

        // YamlNode root = new YamlAstParser().parse(yaml);
        // YamlNode resolved = CascaraYaml.resolve(root);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {

            // parser.setReporter(new StandardReporter()
            //     .setLevel(Level.TRACE)
            //     .setAnsiColoringEnabled(true)
            // );

            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());

            if (parser.next() instanceof YamlStreamingEvent event) {
                assertEquals(StreamingEventType.START_OBJECT, event.getType());
                String anchor = event.getAnchor();
                assertEquals("a1", anchor);
            }
        }
    }

    @Test
    public void testAliasAndEmptyKeysAreParsed() throws Exception {
        String yaml = """
            "top1" :
                "key1" : &alias1 scalar1
            'top2' :
                'key2' : &alias2 scalar2
            top3: &node3
                *alias1 : scalar3
            top4:
                *alias2 : scalar4
            top5   :
                scalar5
            top6:
                &anchor6 'key6' : scalar6
            """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());

            // top1
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            // top2
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            // top3
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.ALIAS, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            // top4
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.ALIAS, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            // top5
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());

            // top6
            YamlStreamingEvent top6 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, top6.getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            YamlStreamingEvent key6 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key6.getType());
            YamlStreamingEvent scalar6 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, scalar6.getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            assertEquals("", top6.getAnchor());
            assertEquals("anchor6", key6.getAnchor());
            assertEquals("", scalar6.getAnchor());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());

        }
    }

    @Test
    public void testSimpleMapping() throws Exception {
        String yaml = "a: b";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
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

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {

            List<StreamingEvent> events = new ArrayList<>();

            parser.forEachRemaining(e ->{
                if (e!=null) {
                    parserReporter.debug("Event: " + e.getType() + ": " + e.getContent());
                    events.add(e);
                }
            });

            new EventValidator(events)//.verbose()
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

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            List<StreamingEvent> events = new ArrayList<>();

            parser.forEachRemaining(e -> {
                if (e != null) events.add(e);
            });

            new EventValidator(events)//.verbose()
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

    // This occasionally hangs when reporting is on (like 1 in 100 times)
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

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            List<StreamingEvent> events = new ArrayList<>();

            parser.forEachRemaining(e -> {
                if (e != null) events.add(e);
            });

            new EventValidator(events)//.verbose()
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

    @Test
    public void testEmptyLiteral() throws Exception {
        String yaml = "--- |0\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());

            YamlStreamingEvent doc = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_DOCUMENT, doc.getType());
            assertEquals("---", doc.getContent());

            assertFalse(parser.hasNext());
        }
    }

    @Test
    public void testEmptyKey() throws Exception {
        String yaml = ": a";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());

            YamlStreamingEvent key = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key.getType());
            assertEquals("", key.getContent());

            YamlStreamingEvent value = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, value.getType());
            assertEquals("a", value.getContent());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testSetWithTag() throws Exception {
        String yaml = "--- !!set\n? key\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent map = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, map.getType());
            assertEquals("tag:yaml.org,2002:set", map.getResolvedTag());

            YamlStreamingEvent key = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key.getType());
            assertEquals("key", key.getContent());

            YamlStreamingEvent value = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, value.getType());
            assertEquals(null, value.getContent());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testFlowMap() throws Exception {
        String yaml = "{\na: b\n}\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent map = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, map.getType());
            assertEquals(NodeStyle.FLOW, map.getNodeStyle());

            YamlStreamingEvent key = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key.getType());
            assertEquals("a", key.getContent());

            YamlStreamingEvent value = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, value.getType());
            assertEquals("b", value.getContent());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void test4FJ6() throws Exception {
        String yaml = "[\n  [ a, [ [[b,c]]: d, e]]: 23\n]";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType()); // [

            YamlStreamingEvent outerMap = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, outerMap.getType());
            assertEquals(NodeStyle.FLOW, outerMap.getNodeStyle());

            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType()); // a

            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType()); // b
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType()); // c
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType()); // d
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType()); // e

            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType()); // 23

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType()); // ]
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testLocalTag() throws Exception {
        String yaml = "%TAG !m! !my-\n---\n!m! a\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent value = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, value.getType());
            assertEquals("a", value.getContent());
            assertEquals("!my-", value.getResolvedTag());

            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void test6BFJ() throws Exception {
        String yaml = "---\n&mapping\n&key [ &item a, b, c ]: value\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent map = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, map.getType());
            assertEquals("mapping", map.getAnchor(), "&mapping should belong to the map");

            YamlStreamingEvent seq = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_ARRAY, seq.getType());
            assertEquals("key", seq.getAnchor(), "&key should belong to the sequence");

            YamlStreamingEvent item = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, item.getType());
            assertEquals("item", item.getAnchor(), "&item should belong to the first item");
            assertEquals("a", item.getContent());

            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void test753E() throws Exception {
        String yaml = "--- |-\n a\n\n\n...\n";

        // parserOptions.setPreloadTokenBuffer(true);
        // reporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // astParser.getTokenizer() .setReporter(reporter);
        // YamlNode root = astParser.parseMulti(yaml);
        // TestUtils.dumpTokens(astParser.getTokens());


        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());

            YamlStreamingEvent endDoc = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.END_DOCUMENT, endDoc.getType());
            assertEquals("...", endDoc.getContent());

            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    void test_pull_7BMT() throws Exception {
        String yaml = """
            ---
            top1: &node1
              &k1 key1: one
            """;

        // parserOptions.setPreloadTokenBuffer(true);
        // parserReporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // YamlNode root = astParser.parseMulti(yaml);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent outer = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, outer.getType());
            // System.out.println("outer: " + outer.getAnchor());
            // assertEquals("node1", outer.getAnchor());

            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType()); //top1

            YamlStreamingEvent inner = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, inner.getType());
            // System.out.println("inner: " + inner.getAnchor());
            assertEquals("node1", inner.getAnchor());

            YamlStreamingEvent key1 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key1.getType());
            assertEquals("k1", key1.getAnchor());
            assertEquals("key1", key1.getContent());

            YamlStreamingEvent val = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, val.getType());
            assertEquals("one", val.getContent());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }

    }

    @Test
    public void testAnchorOnMap2() throws Exception {
        String yaml = """
            top6: &anchor
                &k1 'key6': scalar6
            """;

        // parserOptions.setPreloadTokenBuffer(true);
        // parserReporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // YamlNode root = astParser.parseMulti(yaml);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent outer = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, outer.getType());
            // System.out.println("outer: " + outer.getAnchor());
            // assertEquals("node1", outer.getAnchor());

            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType()); //top1

            YamlStreamingEvent inner = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, inner.getType());
            // System.out.println("inner: " + inner.getAnchor());
            assertEquals("anchor", inner.getAnchor());

            YamlStreamingEvent key1 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key1.getType());
            assertEquals("k1", key1.getAnchor());
            assertEquals("key6", key1.getContent());

            YamlStreamingEvent val = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, val.getType());
            assertEquals("scalar6", val.getContent());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testAnchorOnKey() throws Exception {
        String yaml = """
            top6:
                &k1 'key6': scalar6
            """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());

            YamlStreamingEvent outer = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, outer.getType());

            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType()); //top1

            YamlStreamingEvent inner = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.START_OBJECT, inner.getType());
            // System.out.println("inner: " + inner.getAnchor());
            assertEquals("", inner.getAnchor());

            YamlStreamingEvent key1 = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.FIELD_NAME, key1.getType());
            assertEquals("k1", key1.getAnchor());
            assertEquals("key6", key1.getContent());

            YamlStreamingEvent val = (YamlStreamingEvent) parser.next();
            assertEquals(StreamingEventType.VALUE_SCALAR, val.getType());
            assertEquals("scalar6", val.getContent());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testImplicitSecondDocument() throws Exception {
        String yaml = "---\nscalar1\n...\nkey: value\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());

            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());

            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());

            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    @Test
    public void testNestedSequences() throws Exception {
        String yaml = "---\n" + //
                        "nested sequences:\n" + //
                        "- - - []\n" + //
                        "- - - {}\n" + //
                        "key1: []\n" + //
                        "key2: {}\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());

            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType()); // nested sequences

            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType()); //[]
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType()); // {}

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());


            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType()); // key1
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType()); //[]
            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType()); // key2
            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType()); // {}
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    // @Disabled // TODO
    @Test
    public void testTagAndAnchorOrderWithScalars() throws Exception {
        String yaml = """
            - &a1
              !!str
              x1

            - !!str
              &b1
              y1

            - *a1

            - *b1
            """;

        // parserOptions.setPreloadTokenBuffer(true);
        // parserReporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // YamlNode root = astParser.parseMulti(yaml);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());


            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());


            assertEquals(StreamingEventType.ALIAS, parser.next().getType());
            assertEquals(StreamingEventType.ALIAS, parser.next().getType());


            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    //TODO
    // @Disabled
    @Test
    public void testTagAndAnchorOrderWithMaps() throws Exception {
        String yaml = """
            - &a1
              !!str
              c: x
            - !!str
              &a2
              c: x
            - *a1
            - *a2
            """;

        // parserOptions.setPreloadTokenBuffer(true);
        // parserReporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // YamlNode root = astParser.parseMulti(yaml);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());

            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());

            assertEquals(StreamingEventType.START_OBJECT, parser.next().getType());
            assertEquals(StreamingEventType.FIELD_NAME, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.END_OBJECT, parser.next().getType());


            assertEquals(StreamingEventType.ALIAS, parser.next().getType());
            assertEquals(StreamingEventType.ALIAS, parser.next().getType());

            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }

    // TODO
    @Disabled
    @Test
    public void testTagAndAnchorOrderWithMapsAndScalars() throws Exception {
        String yaml = """
            - !!str
              &m1
              &s1 c: x
            - &m2
              !!str
              &s2 c: x
            - *m1
            - *s1
            - *m2
            - *s2
            """;

        // parserOptions.setPreloadTokenBuffer(true);
        // parserReporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // YamlNode root = astParser.parseMulti(yaml);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());


            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());


            assertEquals(StreamingEventType.ALIAS, parser.next().getType());
            assertEquals(StreamingEventType.ALIAS, parser.next().getType());


            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());
        }
    }


    // TODO: This should not fully parse
    @Disabled
    @Test
    public void testSetItemsMustHaveNullValues() throws Exception {
        String yaml = """
            - &c1
              !!set
              c: x
            """;

        // parserOptions.setPreloadTokenBuffer(true);
        // parserReporter.setLevel(Level.TRACE);

        // YamlAstParser astParser = new YamlAstParser();
        // YamlNode root = astParser.parseMulti(yaml);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        try (YamlPullParser parser = newParser(inputStream)) {
            assertEquals(StreamingEventType.START_STREAM, parser.next().getType());
            assertEquals(StreamingEventType.START_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.START_ARRAY, parser.next().getType());


            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());
            assertEquals(StreamingEventType.VALUE_SCALAR, parser.next().getType());


            assertEquals(StreamingEventType.ALIAS, parser.next().getType());
            assertEquals(StreamingEventType.ALIAS, parser.next().getType());


            assertEquals(StreamingEventType.END_ARRAY, parser.next().getType());
            assertEquals(StreamingEventType.END_DOCUMENT, parser.next().getType());
            assertEquals(StreamingEventType.END_STREAM, parser.next().getType());

            assertTrue(false); // TODO: This test should not get to here
        }
    }


}
