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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.lang.exception.ParserException;
import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

class AstParserComprehensiveTests extends AstParserTestBase {

    // private final YamlAstParser parser = new YamlAstParser();
    // private final YamlTokenizer tokenizer = new YamlTokenizer();

    // private void assertTokenTypes(List<YamlToken> tokens, YamlTokenType... expected) {
    //     for (int i = 0; i < expected.length; i++) {
    //         if (i >= tokens.size()) fail("Missing token at index " + i + ". Expected " + expected[i]);
    //         assertEquals(expected[i], tokens.get(i).getType(), "Mismatch at token " + i);
    //     }
    // }

    // private YamlOptions options;
    // private YamlTokenizer tokenizer;
    // private YamlAstParser parser;
    // private Reporter reporter;

    private List<Diagnostic> diagnostics;

    public void collect(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void clear(URI uri) {
        diagnostics.clear();
    }


    @BeforeEach
    protected void setup() {
        super.setup();
        diagnostics = new ArrayList<>();

        parser.getOptions().setStrict(true);

    }


    // --- TOKENIZATION TESTS ---

    @Test
    void testTokenizerHandlesComplexIndentation() {
        String yaml = "key:\n  sub:\n    - item\n  next: val";
        List<YamlToken> tokens = tokenizer.tokenize(yaml);

        // Verify the Indent/Dedent balance
        long indents = tokens.stream().filter(t -> t.getType() == YamlTokenType.INDENT).count();
        long dedents = tokens.stream().filter(t -> t.getType() == YamlTokenType.DEDENT).count();

        assertEquals(2, indents, "Should have 2 indent levels");
        assertEquals(2, dedents, "Should have 2 dedent levels to return to root");
    }

    // --- PARSER EDGE CASES ---

    @Test
    void testImplicitNullValues() throws Exception {
        // Common in config: key followed by newline and another key
        String yaml = "empty_key:\nnext_key: value";

        YamlMap doc = (YamlMap)parser.parse(yaml);
        YamlNode val = doc.get("empty_key");
        assertTrue(val instanceof YamlScalar);
        assertNull(((YamlScalar)val).asString(), "Value-less key should result in null scalar");
    }

    @Test
    void testNestedFlowCollectionsInBlock() throws Exception {
        String yaml = "matrix: [[1, 2], [3, 4]]";

        YamlMap rootMap = (YamlMap)parser.parse(yaml);

        // 2. Get the value for "matrix"
        YamlNode matrixNode = rootMap.get("matrix");
        assertTrue(matrixNode instanceof YamlSequence);

        // 3. Now we are at the outer sequence: [[1, 2], [3, 4]]
        YamlSequence outer = (YamlSequence) matrixNode;
        assertEquals(2, outer.size());

        // 4. Get the first inner sequence: [1, 2]
        assertTrue(outer.get(0) instanceof YamlSequence);
        YamlSequence inner = (YamlSequence) outer.get(0);
        assertEquals(2, inner.size());

        // 5. Verify a leaf value
        assertEquals("1", inner.get(0).asString());
    }

    @Test
    void testAnchorAndAliasResolution() throws Exception {
        String yaml = """
                default: &def "base"
                custom: *def
                """;
        YamlMap doc = (YamlMap)parser.parse(yaml);

        // Use the common MapAstNode 'get' to find the alias node
        YamlNode customVal = doc.get("custom");

        assertTrue(customVal instanceof YamlAlias);
        assertEquals("def", ((YamlAlias)customVal).getAnchor());
    }

    // --- THE "ROUND-TRIP" STABILITY TEST ---


    @Test
    void testTabIndentationFails() {
        // YAML spec forbids tabs for indentation
        String yaml = "key:\n\t- item";

        if (DEBUG) {
            Reporter reporter = new StandardReporter()
            .setLevel(Level.DEBUG)
            .setAnsiColoringEnabled(true)
            .setStackTraceEnabled(true);

            YamlTokenizer tokenizer = new YamlTokenizer()
               .setReporter(reporter);

            TestUtils.dumpTokens(
                reporter.getWriter(Level.DEBUG),
                tokenizer.tokenize(yaml)
            );
        }

        assertThrows(ParserException.class, () -> parser.parse(yaml));
    }

    @Test
    void testMismatchedDedentFails() {
        String yaml = "parent:\n  child: val\n    - orphan_item";
        assertThrows(ParserException.class, () -> parser.parse(yaml));
        // parser.parse(yaml);
        // assertFalse(diagnostics.isEmpty());
    }}
