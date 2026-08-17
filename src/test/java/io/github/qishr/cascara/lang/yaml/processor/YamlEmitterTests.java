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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class YamlEmitterTests extends BaseAstParserTest {
    @Test
    void testEmitterRoundTrip() {
        String original = "name: Cascara\nversion: 1.0\ntags:\n  - java\n  - yaml";

        YamlNode root = parser.parse(original);

        YamlEmitter emitter = new YamlEmitter();
        String emitted = emitter.emit(root);

        if (DEBUG) {
            System.out.println("IN:");
            System.out.println(StringUtils.debugString(original));
            System.out.println("OUT:");
            System.out.println(StringUtils.debugString(emitted));
        }

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


        // TODO: make diagnostics for all tests configurable in one place
        // parser.setReporter(new StandardReporter((s) -> {
        //     System.err.print(s);
        // }).setLevel(Level.TRACE));

        YamlMap yaml = (YamlMap)parser.parse(original);

        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        YamlEmitter emitter = new YamlEmitter();
        String result = emitter.emit(yaml);

        // Using trim to ignore trailing whitespace differences
        assertEquals(original.trim(), result.trim());
    }

    @Test
    void test_emitter_anchorRoundTrip() {
        String yaml = "key: &myAnchor value\ncopy: *myAnchor\n";

        YamlNode root = parser.parse(yaml);

        YamlEmitter emitter = new YamlEmitter();
        String output = emitter.emit(root);

        assertEquals(yaml, output);
    }

    @Disabled("This is invalid. The emitter should emit the same as the YAML being parsed.")
    @Test
    void testExpandedStyleSequence() {
        // Create a simple sequence: ["java", "yaml"]
        YamlSequence seq = new YamlSequence();
        seq.add(new YamlScalar("java", ScalarStyle.PLAIN));
        seq.add(new YamlScalar("yaml", ScalarStyle.PLAIN));

        // Wrap it in a document for the emitter
        YamlMap root = new YamlMap();

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

    @Disabled("This will not work if the map style isn't store in the AST")
    @Test
    void test_emitter_lexeme() {
        String yaml = "key:\n  \"one\n\n  two\"";
        YamlNode root = parser.parse(yaml);

        YamlEmitter emitter = new YamlEmitter();
        String output = emitter.emit(root);

        System.out.println("IN:");
        System.out.println(StringUtils.debugString(yaml));
        System.out.println("OUT:");
        System.out.println(StringUtils.debugString(output));

        TestUtils.assertEquals(yaml, output);
    }

    @Test
    void test_emitter_lexeme2() {
        String yaml = "\"one\\ntwo\"";
        YamlNode root = parser.parse(yaml);

        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        YamlEmitter emitter = new YamlEmitter();
        String output = emitter.emit(root);

        TestUtils.assertEquals(yaml, output);
    }






    //
    // New Emitter Tests
    //

    private void testIntegrity(String yaml) {
        YamlNode root = parser.parse(yaml);
        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokens());
        }
        YamlAstEmitter emitter = new YamlAstEmitter();

        emitter.setReporter(
            new StandardReporter()
                .setLevel(Level.TRACE)
                .setAnsiColoringEnabled(true)
        );

        String output = emitter.toString(root);

        if (yaml.endsWith("\n") && !output.endsWith("\n")) {
            output += "\n";
        }

        TestUtils.assertEquals(yaml, output);
    }

    @Test
    void test_scalar() {
        testIntegrity("a");
        testIntegrity("'a'");
        testIntegrity("\"a\"");
        testIntegrity("a\nb");

        testIntegrity("1");
        testIntegrity("1.2");
        testIntegrity("1.0");
    }

    @Test
    void test_sequence() {
        testIntegrity("  - a\n  - b\n  - c");
    }

    @Test
    void test_map() {
        testIntegrity("a: x\nb: y");
    }

    @Test
    void test_map_valuesOnNewline() {
        testIntegrity("a:\n  x\nb:\n  y");
    }

    @Test
    void test_sequence_ofMap() {
        testIntegrity("- a: x\n  b: y\n  c: z");
    }

    @Test
    void test_sequence_ofMap_ofSequence() {
        testIntegrity(
            """
            - a:
                - x
                - y
            """
        );
    }

    @Test
    void test_sequence_ofMap_ofMap() {
        testIntegrity(
            """
            - a:
                x: 1
                y: 2
            """
        );
    }
}
