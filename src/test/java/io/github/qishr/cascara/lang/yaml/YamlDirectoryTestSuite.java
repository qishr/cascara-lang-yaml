package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.*;
import java.util.stream.Stream;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;

class YamlDirectoryTestSuite {

    private Reporter reporter = new SimpleReporter().setLevel(Level.TRACE);
    private final YamlOptions options = new YamlOptions().setStrict(true);
    private final YamlParser parser = new YamlParser().setOptions(options).setReporter(reporter);

    @ParameterizedTest(name = "Validating: {0}")
    @MethodSource("getValidFiles")
    void testValidFiles(String fileName, String content) {
        assertDoesNotThrow(() -> parser.parse(content), "Should have parsed: " + fileName);
    }

    @ParameterizedTest(name = "Invalidating: {0}")
    @MethodSource("getInvalidFiles")
    void testInvalidFiles(String fileName, String content) {
        assertThrows(Exception.class, () -> parser.parse(content), "Should have failed: " + fileName);
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
        YamlDocument doc = parser.parse(content);

        // 1. Setup ONE emitter with your desired options
        YamlOptions testOptions = new YamlOptions().setExpandedStyle(true);
        YamlEmitter emitter = new YamlEmitter();
        emitter.setOptions(testOptions);

        // 2. First Emit
        String emitted = emitter.emit(doc);

        // 3. Re-parse
        YamlDocument reParsedDoc = parser.parse(emitted);

        // 4. Second Emit (using the SAME emitter instance)
        String secondEmit = emitter.emit(reParsedDoc);

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
}