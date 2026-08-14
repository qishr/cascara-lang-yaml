package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.util.JreUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

import static org.junit.jupiter.api.Assertions.*;

public class SpecTests3 extends BaseAstParserTest {

    @Test
    public void test4ABK() {
        String yaml = """
            {
            unquoted : "separate",
            http://foo.com,
            omitted value:,
            }
            """;

        tokenize(yaml);

        YamlStream stream = parser.parseMulti(yaml);
        assertEquals(1, stream.getDocuments().size());
        YamlDocument doc = stream.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        String emitted = emitter.emit(body);
        assertTrue(emitted.contains("null"), "Emitted YAML should contain explicit nulls");
        // TODO: re-parse this and check the colon after the url didn't become part of the key

        YamlMap map = (YamlMap) body;

        YamlMapEntry entry0 = map.getEntry(0);
        TestUtils.assertEquals("unquoted", entry0.getKeyString());
        TestUtils.assertEquals("separate", entry0.getValue().asString());

        YamlMapEntry entry1 = map.getEntry(1);
        TestUtils.assertEquals("http://foo.com", entry1.getKeyString());
        TestUtils.assertEquals(null, entry1.getValue().asString());

        YamlMapEntry entry2 = map.getEntry(2);
        TestUtils.assertEquals("omitted value", entry2.getKeyString());
        TestUtils.assertEquals(null, entry2.getValue().asString());

    }

    @Test
    public void test26DV() {
        String yaml = """
            "top1":
                "key1": &alias1 scalar1
            'top2':
                'key2': &alias2 scalar2
            top3: &node3
                *alias1 : scalar3
            top4:
                *alias2 : scalar4
            top5: scalar5
            top6:
                &anchor6 'key6': scalar6
            """;

        tokenize(yaml);

        YamlStream stream0 = parser.parseMulti(yaml);

        YamlEmitter emitter = new YamlEmitter()
            // .setOptions(YamlOptions.CANONICAL)
            .setOptions(new YamlOptions().setIndentSize(4))
            .setReporter(parserReporter);

        String emittedYaml = emitter.emit(stream0);

        YamlStream stream1;
        try {
            stream1 = parser.parseMulti(emittedYaml);
        } catch (YamlParserException e) {

            System.out.println("Failed to parse emitted YAML: " + e.getMessage());
            System.out.println(emittedYaml);
            // reporter.debug("Emitted YAML:\n" + emittedYaml);

            assertTrue(false);
            return;
        }

        if (DEBUG) {
            parserReporter.debug("Emitted YAML:\n" + emittedYaml);
        }

        assertEquals(1, stream1.getDocuments().size());

        YamlDocument doc = stream1.getDocuments().getFirst();
        YamlNode body = normalize(doc.getBody());

        YamlMap map = (YamlMap) body;
        assertEquals(6, map.size());

        YamlMap map3 = map.getMap("top3");
        YamlMapEntry map3e = map3.getEntry(0);
        YamlAlias map3k = (YamlAlias) map3e.getKey();
        YamlScalar map3v = (YamlScalar) map3e.getValue();

        TestUtils.assertEquals("alias1", map3k.getName());
        TestUtils.assertEquals("scalar3", map3v.asString());

    }
}
