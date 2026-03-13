package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlSerializerException;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlSerializer;
import io.github.qishr.cascara.lang.yaml.testclass.ContentTypeRegistryTestClass;
import io.github.qishr.cascara.lang.yaml.testclass.ContentTypeTestClass;

public class SerializerRegressionTests {
    @Test
    void test_contentTypes() throws YamlSerializerException {

        ContentTypeRegistryTestClass registry = new ContentTypeRegistryTestClass();
        ContentTypeTestClass type1 = new ContentTypeTestClass();
        type1.getMimeTypes().add("text/plain");
        type1.setCanonicalId("text/plain");
        type1.setCanonicalName("Plain Text");
        type1.getSuffixes().add(".text");
        type1.getSuffixes().add(".txt");
        registry.getRecords().add(type1);


        YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = yamlSerializer.toAst(registry);
        assertInstanceOf(YamlMapNode.class, yaml);

        YamlMapNode registryNode = (YamlMapNode) yaml;

        YamlNode recordsNode = registryNode.get("records");
        assertInstanceOf(YamlSequenceNode.class, recordsNode);

        YamlSequenceNode recordsSequence = (YamlSequenceNode) recordsNode;

        YamlNode element1 = recordsSequence.get(0);
        assertInstanceOf(YamlMapNode.class, element1);

        YamlMapNode item1MapNode = (YamlMapNode) element1;
        assertEquals("Plain Text", item1MapNode.getString("canonicalName"));
     }
}
