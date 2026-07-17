package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;

public class SpecTests {
       private final YamlAstParser parser = new YamlAstParser();

    @Test
    public void testLiteral0() {
        String yaml = """
            yaml: |
                --- |0
            """;

        parser.setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlNode root = parser.parse(yaml.getBytes());

        assertNotNull(root);
        assertEquals("", root.asString());
    }

    // @Test
    // public void testLiteral10() {
    //     YamlNode root = parser.parse("""
    //         --- |10
    //         """.getBytes());
    //     assertNotNull(root);
    //     assertEquals("", root.asDocument().asScalar().value());
    // }

    // @Test
    // public void testLiteral1Minus() {
    //     YamlNode root = parser.parse("""
    //         --- |1-
    //         """.getBytes());
    //     assertNotNull(root);
    //     assertEquals("", root.asDocument().asScalar().value());
    // }

    // @Test
    // public void testLiteral1Plus() {
    //     YamlNode root = parser.parse("""
    //         --- |1+
    //         """.getBytes());
    //     assertNotNull(root);
    //     assertEquals("", root.asDocument().asScalar().value());
    // }
}
