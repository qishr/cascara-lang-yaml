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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlAlias;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;

public class ParserTests extends BaseAstParserTest {
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
        if (statusValue instanceof YamlAnchor wrapper) {
            YamlNode inner = wrapper.getInnerNode();
            assertInstanceOf(YamlScalar.class, inner);
            assertEquals("active", ((YamlScalar)inner).getPrimitive()); // or .getString() if it exists there
        } else {
            // If it's not a wrapper, it must be the scalar itself
            assertEquals("active", ((YamlScalar)statusValue).getPrimitive());
        }

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
}
