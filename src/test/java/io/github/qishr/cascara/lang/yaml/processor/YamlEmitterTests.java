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

import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.util.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class YamlEmitterTests extends BaseAstParserTest {

    private void testIntegrity(String yaml) {
        YamlNode root = parser.parse(yaml);
        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // YamlNode resolvedRoot = CascaraYaml.resolve(root);

        YamlOptions emitterOptions = new YamlOptions()
            .setOutputResolvedAliases(false);

        YamlAstEmitter emitter = new YamlAstEmitter()
            .setOptions(emitterOptions);

        emitter.setReporter(
            new StandardReporter()
                .setLevel(Level.TRACE)
                .setAnsiColoringEnabled(true)
        );

        String emitted = emitter.toString(root);

        // TODO: Parser needs to ensure trailing whitespace is correct

        if (yaml.endsWith("\n") && !emitted.endsWith("\n")) {
            emitted += "\n";
        }
        if (!yaml.endsWith("\n") && emitted.endsWith("\n")) {
            yaml += "\n";
        }

        if (!yaml.equals(emitted)) {
            try {
                reporter.debug("Original YAML:");
				reporter.getWriter(Level.DEBUG).write(2, yaml);
                reporter.debug("Emitted YAML:");
                reporter.getWriter(Level.DEBUG).write(2, emitted);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        TestUtils.assertEquals(yaml, emitted);
    }

    @Test
    void testEmitterRoundTrip() {
        testIntegrity("name: Cascara\nversion: 1.0\ntags:\n  - java\n  - yaml");
    }

    @Test
    void testComplexCommentRoundTrip() {
        String yaml =
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

        testIntegrity(yaml);
    }

    @Test
    void test_emitter_anchorRoundTrip() {
        testIntegrity("key: &myAnchor value\ncopy: *myAnchor\n");
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
        YamlOptions options = new YamlOptions().setOutputExpandedStyle(true);
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
        testIntegrity("key:\n  \"one\n\n  two\"");
    }

    @Test
    void test_emitter_lexeme2() {
        testIntegrity("\"one\\ntwo\"");
    }






    //
    // New Emitter Tests
    //

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


    @Test
    void test_tagOnSeparateLine() {
        testIntegrity("!!str\na");
    }

    @Test
    void test_anchorAndTag() {
        testIntegrity("&a1 !!str a");
    }

    @Test
    void test_tagAndAnchor() {
        testIntegrity("!!str &a1 a");
    }

    // TODO: parser bug
    @Disabled
    @Test
    void test_anchorAndTagOnSeparateLines() {
        testIntegrity("&a1\n!!str a");
    }
}
