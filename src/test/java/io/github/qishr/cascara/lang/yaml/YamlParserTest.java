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


package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.*;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;
import io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;

class YamlAstParserTest {

    private final YamlAstParser parser = new YamlAstParser();

    @Test
    void testNewLineInsideNestedObject() throws Exception {
        String yaml = """
            a:
                b: 1

                c: 2
            """;

        // TODO: diagnostic level in one place for all tests?
        // Reporter reporter = new StandardReporter().setLevel(Level.TRACE);
        // YamlAstParser parser = new YamlAstParser().setReporter(reporter);

        YamlAstParser parser = new YamlAstParser();
        parser.parse(yaml);

    }


    @Test
    void testIndentedScalarInSequence() throws Exception {
        String yaml = """
                mimeTypes:
                  -
                    "text/css"
                """;

        YamlMapNode rootMap = (YamlMapNode)parser.parse(yaml);

        // Use the get(String key) helper from MapAstNode
        YamlNode rootValue = rootMap.get("mimeTypes");

        assertTrue(rootValue instanceof YamlSequenceNode, "Expected a SequenceNode for mimeTypes");
        YamlSequenceNode seq = (YamlSequenceNode) rootValue;

        // SequenceAstNode uses get(index)
        YamlNode firstItem = seq.get(0);
        assertTrue(firstItem instanceof YamlScalarNode, "Expected a ScalarNode inside the sequence");

        YamlScalarNode scalar = (YamlScalarNode) firstItem;
        // ScalarAstNode uses getString() or getPrimitive()
        assertEquals("text/css", scalar.asString(), "Should parse indented scalar without quotes");
    }

    @Test
    void testEmptyFileDoesNotCrash() throws Exception {
        String yaml = "";
        assertDoesNotThrow(() -> {
            YamlNode doc = parser.parse(yaml);
            // doc.getRoot() might be a MapNode with no entries
            if (doc instanceof YamlMapNode map) {
                assertTrue(map.getEntries().isEmpty());
            }
        });
    }

    @Test
    void testNestedBlockMap() throws Exception {
        String yaml = """
                records:
                  -
                    id: 1
                    name: "test"
                """;

        YamlMapNode rootMap = (YamlMapNode) parser.parse(yaml);

        YamlSequenceNode seq = (YamlSequenceNode) rootMap.get("records");
        // Get the first item in sequence, then cast to map
        YamlMapNode innerMap = (YamlMapNode) seq.get(0);

        assertEquals(2, innerMap.getEntries().size());
        assertEquals(1, innerMap.getInteger("id"));
        assertEquals("test", innerMap.getString("name"));
    }

    @Test
    void testMixedStyles() throws Exception {
        String yaml = """
                compact: [1, 2, 3]
                expanded:
                  -
                    1
                  -
                    2
                """;
        YamlMapNode root = (YamlMapNode) parser.parse(yaml);

        // Accessing values by key and checking style
        YamlSequenceNode compact = (YamlSequenceNode) root.get("compact");
        YamlSequenceNode expanded = (YamlSequenceNode) root.get("expanded");

        assertEquals(CollectionStyle.FLOW, compact.getStyle());
        assertEquals(CollectionStyle.BLOCK, expanded.getStyle());
    }

    @Test
    void testTokenBalance() {
        String yaml = """
                root:
                level1:
                    - item1
                """;
        // Assuming YamlTokenizer exists and returns a List of YamlToken
        List<YamlToken> tokens = new YamlTokenizer().tokenize(yaml);

        long indents = tokens.stream().filter(t -> t.getType() == YamlTokenType.INDENT).count();
        long dedents = tokens.stream().filter(t -> t.getType() == YamlTokenType.DEDENT).count();

        assertEquals(indents, dedents, "Every INDENT must be matched by a DEDENT");
        assertEquals(YamlTokenType.STREAM_START, tokens.get(0).getType());
        assertEquals(YamlTokenType.STREAM_END, tokens.get(tokens.size() - 1).getType());
    }
}