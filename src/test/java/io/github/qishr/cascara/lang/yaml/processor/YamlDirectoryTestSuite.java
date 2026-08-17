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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.*;
import java.util.stream.Stream;

import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

class YamlDirectoryTestSuite extends BaseAstParserTest {

    @BeforeEach
    protected void setup() {
        super.setup();

        parser.getOptions()
            .setStrict(true);

        emitter.setOptions(new YamlOptions()
            .setExpandedStyle(true)
        );

    }


    @ParameterizedTest(name = "Validating: {0}")
    @MethodSource("getValidFiles")
    void testValidFiles(String fileName, String content) {
        assertDoesNotThrow(() -> parser.parse(content), "Should have parsed: " + fileName);
    }

    @ParameterizedTest(name = "Invalidating: {0}")
    @MethodSource("getInvalidFiles")
    void testInvalidFiles(String fileName, String content) {
        assertThrows(YamlParserException.class, () -> parser.parse(content), "Should have failed: " + fileName);
    }

    static Stream<Arguments> getValidFiles() throws Exception {
        return scanFolder("src/test/resources/yaml-suite/valid");
    }

    static Stream<Arguments> getInvalidFiles() throws Exception {
        return scanFolder("src/test/resources/yaml-suite/invalid");
    }

    private static Stream<Arguments> scanFolder(String pathStr) throws Exception {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) return Stream.empty();
        return Files.walk(path)
                .filter(p -> p.toString().endsWith(".yaml"))
                .map(p -> {
                    try { return Arguments.of(p.getFileName().toString(), Files.readString(p)); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
    }

    @ParameterizedTest(name = "Round Trip: {0}")
    @MethodSource("getValidFiles")
    void testRoundTripStability(String fileName, String content) throws Exception {

        // reporter.setLevel(Level.TRACE);

        YamlMap doc = (YamlMap)parser.parse(content);

        // 1. Setup ONE emitter with desired options
        YamlOptions testOptions = new YamlOptions();
        //.setExpandedStyle(true);
        // YamlEmitter emitter = new YamlEmitter();
        emitter.setOptions(testOptions);

        // 2. First Emit
        String emitted = emitter.emit(doc);

        if (DEBUG) {
            System.out.println("=== FIRST EMIT ===");
            System.out.println(emitted);
        }

        // 3. Re-parse
        YamlMap reParsedDoc = (YamlMap)parser.parse(emitted);

        // 4. Second Emit (using the SAME emitter instance)
        String secondEmit = emitter.emit(reParsedDoc);

        if (DEBUG) {
            if (fileName.contains("01-nested")) {
                System.out.println("=== FIRST EMIT ===");
                System.out.println(emitted);

                System.out.println("=== SECOND EMIT ===");
                reParsedDoc = (YamlMap)parser.parse(emitted);
                System.out.println(secondEmit);
            }
        }

        if (!emitted.equals(secondEmit)) {
            fail(generateDiffMessage(fileName, emitted, secondEmit));
        }
    }

    private String generateDiffMessage(String fileName, String expected, String actual) {
        String[] expLines = expected.split("\n");
        String[] actLines = actual.split("\n");
        StringBuilder diff = new StringBuilder("\nDiff failure in " + fileName + ":\n");

        int max = Math.max(expLines.length, actLines.length);
        for (int i = 0; i < max; i++) {
            String e = i < expLines.length ? expLines[i] : "<EOF>";
            String a = i < actLines.length ? actLines[i] : "<EOF>";

            if (!e.equals(a)) {
                diff.append(String.format("Line %d:\n  Exp: [%s]\n  Act: [%s]\n", i + 1, e, a));
            }
        }
        return diff.toString();
    }

    @Test
    void testExpandedListItemComment() {
        String yaml = """
            # Header
            key: value # Inline
            # Middle
            list:
              - item # List comment
            # Footer
            """;

        YamlMap doc = (YamlMap) parser.parse(yaml);

        String emitted = emitter.emit(doc);

        if (DEBUG) {
            System.out.println("=== FIRST EMIT ===");
            System.out.println(emitted);
        }

        YamlMap reparsed = (YamlMap) parser.parse(emitted);
        String secondEmit = emitter.emit(reparsed);

        if (DEBUG) {
            System.out.println("=== SECOND EMIT ===");
            System.out.println(secondEmit);
        }
    }

    @Test
    void testOne() {
        String content = """
          # Header
          x: a # Inline
          # Middle
          y:
            - b
            - c
          z:
            - d # List comment
          # Footer
          """;

        content = """
          map: {}
          list: []
          nested_empty:
            - []
            - {}
          """;

        YamlMap doc = (YamlMap)parser.parse(content);

        // 1. Setup ONE emitter with desired options
        YamlOptions testOptions = new YamlOptions();
        // testOptions.setExpandedStyle(true);

        YamlEmitter emitter = new YamlEmitter();
        emitter.setOptions(testOptions);

        // 2. First Emit
        String emitted = emitter.emit(doc);

        // 3. Re-parse
        YamlMap reParsedDoc = (YamlMap)parser.parse(emitted);

        // 4. Second Emit (using the SAME emitter instance)
        String secondEmit = emitter.emit(reParsedDoc);

        if (DEBUG) {
            System.out.println("=== FIRST EMIT ===");
            System.out.println(emitted);
            System.out.println("=== SECOND EMIT ===");

            reParsedDoc = (YamlMap)parser.parse(emitted);
            System.out.println(secondEmit);
        }

        if (!emitted.equals(secondEmit)) {
            fail(generateDiffMessage("fileName", emitted, secondEmit));
        }

    }

}