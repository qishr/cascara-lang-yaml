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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;
import io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;

public class YamlEmitterTests {
    @Test
    void testEmitterRoundTrip() {
        String original = "name: Cascara\nversion: 1.0\ntags:\n  - java\n  - yaml";
        YamlAstParser parser = new YamlAstParser();
        YamlNode root = parser.parse(original);

        YamlEmitter emitter = new YamlEmitter();
        String emitted = emitter.emit(root);

        assertEquals(original.trim(), emitted.trim());
    }

    @Test
    void testComplexCommentRoundTrip() {
        String original =
            "# Project Configuration\n" +
            "project: Cascara # Structural Editor\n" +
            "settings: # Global settings\n" +
            "  # Enable experimental features\n" +
            "  debug: true\n" +
            "  modes:\n" +
            "    - fast # High performance\n" +
            "    - safe";

        YamlAstParser parser = new YamlAstParser();

        parser.setReporter(new StandardReporter().setLevel(Level.TRACE));

        // TODO: make diagnostics for all tests configurable in one place
        // parser.setReporter(new StandardReporter((s) -> {
        //     System.err.print(s);
        // }).setLevel(Level.TRACE));

        YamlMapNode yaml = (YamlMapNode)parser.parse(original);

        YamlEmitter emitter = new YamlEmitter();
        String result = emitter.emit(yaml);

        // Using trim to ignore trailing whitespace differences
        assertEquals(original.trim(), result.trim());
    }

    @Test
    void test_emitter_anchorRoundTrip() {
        String yaml = "key: &myAnchor value\ncopy: *myAnchor\n";
        YamlAstParser parser = new YamlAstParser().setReporter(new StandardReporter().setLevel(Level.TRACE));
        YamlNode root = parser.parse(yaml);

        YamlEmitter emitter = new YamlEmitter();
        String output = emitter.emit(root);

        assertEquals(yaml, output);
    }

    @Test
    void testExpandedStyleSequence() {
        // Create a simple sequence: ["java", "yaml"]
        YamlSequenceNode seq = new YamlSequenceNode();
        seq.add(new YamlScalarNode("java", QuoteStyle.PLAIN));
        seq.add(new YamlScalarNode("yaml", QuoteStyle.PLAIN));

        // Wrap it in a document for the emitter
        YamlMapNode root = new YamlMapNode();

        root.put("tags", seq);

        // Act: Use the new fluent expanded style
        YamlOptions options = new YamlOptions().setExpandedStyle(true);
        String output = new YamlEmitter()
                .setOptions(options)
                .emit(root);

        // Assert: Check for the characteristic newline-and-indent after the dash
        // Assert: tags: must be at column 0 for a root-level map
        String expected =
            "tags:\n" +
            "  -\n" +
            "    java\n" +
            "  -\n" +
            "    yaml";

        assertEquals(expected.trim(), output.trim(), "Expanded style should place scalars on a new indented line.");
    }
}
