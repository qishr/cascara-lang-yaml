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

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

class YamlStandardComplianceTest {

    private YamlOptions options;
    private YamlAstParser parser;
    private Reporter reporter;
    private List<Diagnostic> diagnostics;

    public void collect(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void clear(URI uri) {
        diagnostics.clear();
    }


    @BeforeEach
    void init() {
        diagnostics = new ArrayList<>();
        reporter = new StandardReporter().setProblemCollector(this::collect);
        options = new YamlOptions().setStrict(true);
        parser = new YamlAstParser()
            .setOptions(options)
            .setReporter(reporter);
    }

    // 1. NESTED EMPTY COLLECTIONS
    @Test
    void testNestedEmptyCollections() throws Exception {
        String yaml = "empty_map: {}\nempty_seq: []\nnested: [[]]";
        YamlMapNode root = (YamlMapNode)parser.parse(yaml);

        assertTrue(((YamlMapNode)root.get("empty_map")).getEntries().isEmpty());
        assertEquals(0, ((YamlSequenceNode)root.get("empty_seq")).size());
    }

    // 2. MIXED BLOCK AND FLOW
    @Test
    void testMixedBlockAndFlow() throws Exception {
        String yaml = """
            block_map:
              flow_seq: [a, b, c]
              inner_block:
                - item
            """;
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);

        // The root contains one key "block_map" which is itself a map
        YamlMapNode blockMap = (YamlMapNode) root.get("block_map");
        assertEquals(2, blockMap.getEntries().size());
    }

    // 3. MULTI-LINE SCALARS (LITERAL)
    @Test
    void testLiteralBlockScalar() throws Exception {
        String yaml = "content: |\n  line one\n  line two";
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);

        YamlScalarNode scalar = (YamlScalarNode) root.get("content");
        assertTrue(scalar.asString().contains("\n"));
    }

    // 4. THE "RECORDS" REGRESSION (EXPANDED STYLE)
    @Test
    void testExpandedScalarRegression() throws Exception {
        String yaml = "key:\n  -\n    indented_value";
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);

        YamlSequenceNode seq = (YamlSequenceNode) root.get("key");
        assertEquals("indented_value", ((YamlScalarNode)seq.get(0)).asString());
    }

    // 5. TRAILING COMMENTS AT END OF FILE
    @Test
    void testTrailingComments() throws Exception {
        String yaml = "key: value\n# This comment is at the very end\n  # and indented weirdly";
        assertDoesNotThrow(() -> parser.parse(yaml));
    }

    // 6. DEEP NESTING LIMITS
    @Test
    void testDeepNesting() throws Exception {
        String yaml = "a: { b: { c: { d: { e: final } } } }";
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);
        assertNotNull(root.get("a"));
    }

    // 7. KEY WITH SPECIAL CHARACTERS
    @Test
    void testComplexKeys() throws Exception {
        String yaml = "\"quoted key\": value\n'single quoted': value";
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);

        assertEquals(2, root.getEntries().size());
        assertEquals("value", root.getString("quoted key"));
        assertEquals("value", root.getString("single quoted"));
    }

    // 8. DUPLICATE KEY HANDLING
    @Test
    void testDuplicateKeys() throws Exception {
        String yaml = "dup: first\ndup: second";

        // Assert that the parser fails on duplicate keys
        // assertThrows(YamlAstParserException.class, () -> {
            parser.parse(yaml);
        // });

        assertFalse(diagnostics.isEmpty());
    }

    // 9. TYPE INFERENCE
    @Test
    void testTypeInference() throws Exception {
        String yaml = "bool: true\nnum: 123.45";
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);

        assertTrue(root.getBoolean("bool"));
        assertEquals(123.45, root.getDouble("num"), 0.001);
    }

    // 10. EMPTY LINE NOISE
    @Test
    void testEmptyLinesAndTabs() throws Exception {
        String yaml = "key: value\n\n\n    \nnext: value";
        assertDoesNotThrow(() -> {
            YamlMapNode root = (YamlMapNode) parser.parse(yaml);
            assertEquals(2, root.getEntries().size());
        });
    }
}