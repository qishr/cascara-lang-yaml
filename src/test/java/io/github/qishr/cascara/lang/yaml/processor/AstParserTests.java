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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.token.YamlTokenType;
import io.github.qishr.cascara.lang.yaml.util.NodeStyle;

public class AstParserTests extends AstParserTestBase {
    @Test
    void test_parser_anchoredScalar() {
        String yaml = "status: &val active\nlink: *val";
        YamlAstParser parser = new YamlAstParser();
        YamlNode doc = parser.parse(yaml);

        assertInstanceOf(YamlMap.class, doc);
        YamlMap map = (YamlMap) doc;
        YamlNode statusValue = map.get("status");

        // Check Anchor
// Check Anchor
        assertEquals("val", statusValue.getAnchor());

        // We need to get the actual scalar content inside the anchor wrapper
        // if (statusValue instanceof YamlAnchor wrapper) {
        //     YamlNode inner = wrapper.getInnerNode();
        //     assertInstanceOf(YamlScalar.class, inner);
        //     assertEquals("active", ((YamlScalar)inner).getPrimitive()); // or .getString() if it exists there
        // } else {
            // If it's not a wrapper, it must be the scalar itself
            assertEquals("active", ((YamlScalar)statusValue).getPrimitive());
        // }

        // Check Alias
        YamlNode linkValue = map.get("link");
        assertTrue(linkValue instanceof YamlAlias);
        assertEquals("val", ((YamlAlias)linkValue).getName());
    }

    @Test
    void test_parser_anchoredMap() {
        String yaml = "defaults: &settings\n" +
                      "  debug: true\n" +
                      "  level: 1\n" +
                      "current: *settings";
        YamlAstParser parser = new YamlAstParser();
        YamlMap doc = (YamlMap)parser.parse(yaml);

        YamlNode defaults = doc.get("defaults");

        // The entire MapNode should have the anchorName
        assertTrue(defaults instanceof YamlMap);
        assertEquals("settings", defaults.getAnchor());

        YamlNode current = doc.get("current");
        assertTrue(current instanceof YamlAlias);
    }

    @Test
    void testNewLineInsideNestedObject() throws Exception {
        String yaml = """
            a:
                b: 1

                c: 2
            """;

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


        if (DEBUG) {
            YamlTokenizer tokenizer = new YamlTokenizer()
                .setReporter(new StandardReporter().setLevel(Level.DEBUG).setAnsiColoringEnabled(true));
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlMap rootMap = (YamlMap)parser.parse(yaml);

        // Use the get(String key) helper from MapAstNode
        YamlNode rootValue = rootMap.get("mimeTypes");

        assertTrue(rootValue instanceof YamlSequence, "Expected a SequenceNode for mimeTypes");
        YamlSequence seq = (YamlSequence) rootValue;

        // SequenceAstNode uses get(index)
        YamlNode firstItem = seq.get(0);
        assertTrue(firstItem instanceof YamlScalar, "Expected a ScalarNode inside the sequence");

        YamlScalar scalar = (YamlScalar) firstItem;
        // ScalarAstNode uses getString() or getPrimitive()
        assertEquals("text/css", scalar.asString(), "Should parse indented scalar without quotes");
    }

    @Test
    void testEmptyFileDoesNotCrash() throws Exception {
        String yaml = "";
        assertDoesNotThrow(() -> {
            YamlNode doc = parser.parse(yaml);
            // doc.getRoot() might be a MapNode with no entries
            if (doc instanceof YamlMap map) {
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

        YamlMap rootMap = (YamlMap) parser.parse(yaml);

        YamlSequence seq = (YamlSequence) rootMap.get("records");
        // Get the first item in sequence, then cast to map
        YamlMap innerMap = (YamlMap) seq.get(0);

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
        if (DEBUG) {
            YamlTokenizer tokenizer = new YamlTokenizer();
            List<YamlToken> tokens = tokenizer.tokenize(yaml);
            TestUtils.dumpTokens(tokens);
        }

        YamlMap root = (YamlMap) parser.parse(yaml);

        // Accessing values by key and checking style
        YamlSequence compact = (YamlSequence) root.get("compact");
        YamlSequence expanded = (YamlSequence) root.get("expanded");

        assertEquals(NodeStyle.FLOW, compact.getNodeStyle());
        assertEquals(NodeStyle.BLOCK, expanded.getNodeStyle());
    }

    @Test
    void testComments() {
        String yaml = """
            # Header
            key: value # Inline
            # Middle
            list:
              - item # List comment
            # Footer
            """;

        YamlMap root = (YamlMap) parser.parse(yaml);
        assertEquals(1, root.getComments().size());

        YamlMapEntry keyEntry = root.getEntry(0);
        YamlNode key = keyEntry.getKey();
        YamlNode value = keyEntry.getValue();


        // TODO: This comment ended up attached to the body map ?

        assertEquals(1, key.getComments().size());
        assertEquals(1, value.getComments().size());

        YamlMapEntry listEntry = root.getEntry(1);
        YamlNode list = listEntry.getKey();
        YamlSequence seq = (YamlSequence) listEntry.getValue();
        YamlNode item = seq.get(0);
        assertEquals(1, list.getComments().size());
        assertEquals(1, item.getComments().size());
    }

    @Disabled("Come back to this")
    @Test
    void testMixedKeysImplicitExplicitNull() {
        String yaml = """
            {
            ? explicit: entry,
            implicit: entry,
            ?
            }
            """;

        YamlMap root = (YamlMap) parser.parse(yaml);

        assertEquals(3, root.size());

        YamlMapEntry explicitKeyEntry = root.getEntry(0);
        YamlMapEntry implicitKeyEntry = root.getEntry(1);
        YamlMapEntry nullKeyEntry = root.getEntry(2);

        assertEquals("explicit", explicitKeyEntry.getKey().asString());
        assertEquals("implicit", implicitKeyEntry.getKey().asString());
        assertEquals(null, nullKeyEntry.getKey().asString());
    }

    @Test
    void testTagInSequence() {
        String yaml = """
            sequence: !!seq
            - entry
            - !!str
            -
              a
            """;

        YamlMap root = (YamlMap) parser.parse(yaml);
        assertEquals(1, root.size());

        YamlSequence seq = root.getSequence("sequence");
        assertEquals(3, seq.size());
        assertEquals("!!seq", seq.getTag());

        YamlScalar empty = seq.getScalar(1);
        assertEquals("", empty.asString());
    }
}
