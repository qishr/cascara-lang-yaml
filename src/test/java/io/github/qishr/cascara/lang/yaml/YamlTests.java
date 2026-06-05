package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

class YamlTests {

    @Test
    void test_rootLevel_arrayAfterEmptyArray() {
        String yamlString = "name: \"nameval\"\n" +
                            "emptyarray: \n" +
                            "array:\n" +
                            "  - value1\n" +
                            "  - value2\n" +
                            "";
        YamlParser parser = new YamlParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlMapNode yaml = (YamlMapNode)parser.parse(yamlString);
        List<YamlNode> array = yaml.getSequence("array").getChildren();
        assertEquals(2, array.size());
    }

    @Test
    void test_subLevel_arrayAfterEmptyArray() {
        String yamlString = "object:\n" + //
                            "  emptyarray:\n" + //
                            "  array:\n" + //
                            "    - value1\n" + //
                            "    - value2\n" + //
                            "";
        YamlParser parser = new YamlParser();
        YamlMapNode yaml = (YamlMapNode)parser.parse(yamlString);
        YamlMapNode object = yaml.getMap("object");
        List<YamlMapEntryNode> entries = object.getEntries();

        YamlMapEntryNode entry1 = entries.getFirst();
        YamlNode key1 = entry1.getKey();
        if (key1 instanceof YamlScalarNode scalar) {
            assertEquals("emptyarray", scalar.asString());
        }

        YamlMapEntryNode entry2 = entries.getLast();
        YamlNode key2 = entry2.getKey();
        if (key2 instanceof YamlScalarNode scalar) {
            assertEquals("array", scalar.asString());
        }
    }

    @Test
    void test_stringContaining_quotes() {
        String yamlString = "name: \"one \\\"two\\\" three\"";
        YamlParser parser = new YamlParser();
        YamlMapNode yaml = (YamlMapNode)parser.parse(yamlString);
        String name = yaml.getString("name");
        assertEquals("one \"two\" three", name);
    }

    @Test
    void test_stringContaining_newline() {
        String yamlString = "name: \"One\n" + //
                        "Two\"";
        YamlParser parser = new YamlParser();
        YamlMapNode yaml = (YamlMapNode)parser.parse(yamlString);
        String name = yaml.getString("name");
        assertEquals("One\nTwo", name);
    }

    @Test
    void test_startsWithComment() {
        String yamlString = "#comment\nkey: value\n";
        YamlParser parser = new YamlParser();
        YamlMapNode yaml = (YamlMapNode)parser.parse(yamlString);
        String value = yaml.getString("key");
        assertEquals("value", value);
    }
}

