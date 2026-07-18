package io.github.qishr.cascara.lang.yaml;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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

    @Test
public void testAliasAndEmptyKeysAreParsed() {
    String yaml = """
        top1:
          key1: &alias1 scalar1
        top2:
          key2: &alias2 scalar2
        top3: &node3
          *alias1: scalar3
        top4:
        top5:
          scalar5
        top6:
          &anchor6 'key6': scalar6
        """;

    YamlAstParser parser = new YamlAstParser();
    YamlNode root = parser.parse(yaml);

    assertTrue(root instanceof YamlMapNode);
    YamlMapNode map = (YamlMapNode) root;

    // Extract keys as strings for easier comparison
    List<String> keys = map.getEntries().stream()
    .<String>map(e -> {
        YamlNode k = e.getKey();
        if (k instanceof YamlScalarNode s) return s.asString();
        if (k instanceof YamlAliasNode a) return "*" + a.getAlias();
        return "<complex>";
    })
    .toList();


    assertEquals(
        List.of("top1", "top2", "top3", "*alias1", "top4", "*alias2", "top5", "top6"),
        keys
    );
}

}
