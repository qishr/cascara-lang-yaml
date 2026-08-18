package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlDocument;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;

import static org.junit.jupiter.api.Assertions.*;

public class AstParserSpecTests3 extends AstParserTestBase {

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
}
