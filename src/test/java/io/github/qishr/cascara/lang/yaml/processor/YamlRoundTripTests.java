package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class YamlRoundTripTests {
    private static String VALID_PATH = "src/test/resources/yaml-suite/valid";

    private YamlOptions options = new YamlOptions().setExpandedStyle(true);

    private YamlAstParser parser;
    private Reporter reporter;

    @BeforeEach
    void init() {
        options = new YamlOptions().setStrict(true);
        parser = new YamlAstParser()
            .setOptions(options);
        parser.setReporter(new StandardReporter().setLevel(Level.DEBUG));
    }

    @Test
    void valid_03_multiline_strings() {
        validate("03-multiline-strings.yaml");
    }

    @Test
    void valid_04_empty_edge_cases() {
        validate("04-empty-edge-cases.yaml");
    }

    @Test
    void valid_09_empty_collections() {
        validate("09-empty-collections.yaml");
    }

    @Test
    void valid_12_content_type_records() {
        validate("12-content-type-records.yaml");
    }

    private void validate(String filename) {
        Path path = Paths.get(VALID_PATH, filename);
        String fileContent = null;
        try {
            fileContent = Files.readString(path);
        } catch (IOException e) {
            fail("Unable to read YAML file " + filename);
        }

        YamlEmitter emitter = new YamlEmitter().setOptions(options);

        YamlMap firstAst = (YamlMap) parser.parse(fileContent);
        TestUtils.dumpTokens(parser.getTokens());

        String firstYaml = emitter.emit(firstAst);

        System.out.println("---INPUT");
        System.out.println(fileContent);
        System.out.println("---EMITTED");
        System.out.println(firstYaml);

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(firstYaml);
        TestUtils.dumpTokens(tokens);

        YamlMap secondAst = (YamlMap) parser.parse(firstYaml);

        String secondYaml = emitter.emit(secondAst);

        System.out.println("File content: " + StringUtils.debugString(fileContent));
        System.out.println("First YAML  : " + StringUtils.debugString(firstYaml));
        System.out.println("Second YAML : " + StringUtils.debugString(secondYaml));

        if (!firstAst.equals(secondAst)) {
            System.err.println("validate ast mismatch ["+filename+"]");
            validateError(filename, fileContent, firstAst, secondAst, firstYaml, secondYaml);
            fail("AST Mismatch for " + filename);
        }

        if (!firstYaml.equals(secondYaml)) {
            System.err.println("validate content mismatch ["+filename+"]");
            validateError(filename, fileContent, firstAst, secondAst, firstYaml, secondYaml);
            fail("Content Mismatch for " + filename);
        }
    }

    private void traceParser(String content, String title) {
        System.out.println("\n=== Parser Trace for " + title + " ===");
        reporter = new StandardReporter().setLevel(Level.TRACE);
        YamlAstParser parser = new YamlAstParser().setOptions(options).setReporter(reporter);
        parser.parse(content);
        TestUtils.dumpTokens(parser.getTokens());
    }

    private void validateError(String filename, String fileContent, YamlNode firstAst, YamlNode secondAst, String firstYaml, String secondYaml) {
        System.out.println("\nFile content:");
        System.out.println(StringUtils.debugString(fileContent));

        traceParser(fileContent, "File Content");
        traceParser(firstYaml, "Emitted Content");

        System.out.println("\n=== First AST ===");
        TestUtils.dumpYamlAst(firstAst, "");
        System.out.println("\n=== Second AST ===");
        TestUtils.dumpYamlAst(secondAst, "");

        // System.err.println("\n" + generateDiffMessage(filename, firstYaml, secondYaml) + "\n");

        System.out.println("\n=== FIRST EMIT ===");
        System.out.println(StringUtils.debugString(firstYaml));
        System.out.println("\n=== SECOND EMIT ===");
        System.out.println(StringUtils.debugString(secondYaml));
    }
}
