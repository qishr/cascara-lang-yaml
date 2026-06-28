package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlParser;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

class YamlDirectoryTestSuite {

    private YamlOptions options;
    private YamlParser parser;
    private Reporter reporter;

    @BeforeEach
    void init() {
        reporter = new StandardReporter();
        options = new YamlOptions().setStrict(true);
        parser = new YamlParser()
            .setOptions(options)
            .setReporter(reporter);
    }

    @ParameterizedTest(name = "Validating: {0}")
    @MethodSource("getValidFiles")
    void testValidFiles(String fileName, String content) {
        assertDoesNotThrow(() -> parser.parse(content), "Should have parsed: " + fileName);
    }

    @ParameterizedTest(name = "Invalidating: {0}")
    @MethodSource("getInvalidFiles")
    void testInvalidFiles(String fileName, String content) {
        parser.setReporter(new StandardReporter().setLevel(Level.TRACE));
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

        // TODO: diagnostic level in one place for all tests?
        reporter.setLevel(Level.TRACE);

        YamlMapNode doc = (YamlMapNode)parser.parse(content);

        // PrintWriter pw = new PrintWriter(System.err);
        // Tree<AstTreeData,AstNode> tree = new Tree<>();
        // tree.setRoot(new AstTreeData(doc));
        // tree.render(pw);
        // pw.flush();


        // 1. Setup ONE emitter with desired options
        YamlOptions testOptions = new YamlOptions().setExpandedStyle(true);
        YamlEmitter emitter = new YamlEmitter();
        emitter.setOptions(testOptions);

        // 2. First Emit
        String emitted = emitter.emit(doc);

        List<YamlToken> list = parser.getTokens();
        System.out.println("\n---1");
        System.out.println(emitted);
        System.out.println("---\n");

        // 3. Re-parse
        YamlMapNode reParsedDoc = (YamlMapNode)parser.parse(emitted);

        // 4. Second Emit (using the SAME emitter instance)
        String secondEmit = emitter.emit(reParsedDoc);

        System.out.println("\n---2");
        System.out.println(emitted);
        System.out.println("---\n");

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