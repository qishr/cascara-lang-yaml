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

import io.github.qishr.cascara.lang.yaml.ast.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YamlFlowParserTest {

    private final YamlAstParser parser = new YamlAstParser();

    @Test
    void testEmptyFlowContainers() {
        // Test empty flow map
        YamlNode emptyMapRoot = parser.parse("{}");
        assertTrue(emptyMapRoot instanceof YamlMapNode);
        assertTrue(((YamlMapNode) emptyMapRoot).isEmpty());

        // Test empty flow sequence
        YamlNode emptySeqRoot = parser.parse("[]");
        assertTrue(emptySeqRoot instanceof YamlSequenceNode);
        assertEquals(0, ((YamlSequenceNode) emptySeqRoot).size());
    }

    @Test
    void testStandardFlowSequence() {
        String yaml = "[macos, windows, linux]";
        YamlNode root = parser.parse(yaml);

        assertTrue(root instanceof YamlSequenceNode);
        YamlSequenceNode seq = (YamlSequenceNode) root;

        assertEquals(3, seq.size());
        assertEquals("macos", ((YamlScalarNode) seq.get(0)).asString());
        assertEquals("windows", ((YamlScalarNode) seq.get(1)).asString());
        assertEquals("linux", ((YamlScalarNode) seq.get(2)).asString());
    }

    @Test
    void testFlowMapWithTrailingComma() {
        String yaml = "{ name: Cascara, version: 0.4.0, }";
        YamlNode root = parser.parse(yaml);

        assertTrue(root instanceof YamlMapNode);
        YamlMapNode map = (YamlMapNode) root;

        assertEquals(2, map.size());
        // Verify entries map properly even with trailing comma block execution
        assertNotNull(map.get("name"));
        assertNotNull(map.get("version"));
    }

    @Test
    void testNestedFlowContainers() {
        String yaml = "{ platform: macos, dependencies: [java25, javafx] }";
        YamlNode root = parser.parse(yaml);

        assertTrue(root instanceof YamlMapNode);
        YamlMapNode map = (YamlMapNode) root;

        YamlNode deps = map.get("dependencies");
        assertTrue(deps instanceof YamlSequenceNode);
        assertEquals(2, ((YamlSequenceNode) deps).size());
    }
}