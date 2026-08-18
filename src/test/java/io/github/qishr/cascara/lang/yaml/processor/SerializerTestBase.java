package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.diagnostic.YamlParserException;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class SerializerTestBase extends AstParserTestBase {
    private static String VALID_PATH = "src/test/resources/yaml-suite/valid";

    protected Reporter serializerReporter;
    protected YamlSerializer serializer;

    @BeforeEach
    protected void setup() {
        super.setup();

        serializerReporter = new StandardReporter()
            .setLevel(SERIALIZER_LEVEL)
            .setAnsiColoringEnabled(true)
            .setFlushEnabled(true)
            .setStackTraceEnabled(true);

        serializer = new YamlSerializer()
            .setReporter(serializerReporter)
            .setOptions(YamlOptions.CANONICAL);
    }

    //
    // Utils
    //

    protected void validate(String filename) {
        Path path = Paths.get(VALID_PATH, filename);
        String fileContent = null;
        try {
            fileContent = Files.readString(path);
        } catch (IOException e) {
            fail("Unable to read YAML file " + filename);
        }

        if (DEBUG){
            YamlTokenizer tz = new YamlTokenizer()
                .setReporter(new StandardReporter()
                    .setLevel(Level.DEBUG)
                    .setAnsiColoringEnabled(true)
            );
            TestUtils.dumpTokens(tz.tokenize(fileContent));
        }

        YamlMap firstAst = null;

        try {
            firstAst = (YamlMap) parser.parse(fileContent);
        } catch (YamlParserException e) {

            // TODO: DEBUG

            assertNotNull(null);
        }
        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        String firstYaml = serializer.toString(firstAst);

        if (DEBUG) {
            System.out.println("---INPUT");
            System.out.println(fileContent);
            System.out.println("---EMITTED");
            System.out.println(firstYaml);
            YamlTokenizer tokenizer = new YamlTokenizer();
            List<YamlToken> tokens = tokenizer.tokenize(firstYaml);
            TestUtils.dumpTokens(tokens);
        }


        YamlMap secondAst = null;

        try {
            secondAst = (YamlMap) parser.parse(firstYaml);
        } catch (YamlParserException e) {
            System.out.println("---INPUT");
            System.out.println(fileContent);
            System.out.println("---EMITTED");
            System.out.println(firstYaml);


            assertNotNull(null);
        }

        String secondYaml = serializer.toString(secondAst);

        if (DEBUG) {
            System.out.println("File content: " + StringUtils.debugString(fileContent));
            System.out.println("First YAML  : " + StringUtils.debugString(firstYaml));
            System.out.println("Second YAML : " + StringUtils.debugString(secondYaml));
        }

        if (!firstAst.equals(secondAst)) {
            if (DEBUG) {
                System.err.println("validate ast mismatch ["+filename+"]");
                validationError(filename, fileContent, firstAst, secondAst, firstYaml, secondYaml);
            }
            fail("AST Mismatch for " + filename);
        }

        if (!firstYaml.equals(secondYaml)) {
            // if (DEBUG) {
                System.err.println("validate content mismatch ["+filename+"]");
                validationError(filename, fileContent, firstAst, secondAst, firstYaml, secondYaml);
            // }
            fail("Content Mismatch for " + filename);
        }
    }

    private void traceParser(String content, String title) {
        System.out.println("\n=== Parser Trace for " + title + " ===");
        parserReporter = new StandardReporter().setLevel(Level.TRACE);
        YamlAstParser parser = new YamlAstParser()
            .setReporter(parserReporter);
        parser.parse(content);
        TestUtils.dumpTokens(parser.getTokens());
    }

    private void validationError(String filename, String fileContent, YamlNode firstAst, YamlNode secondAst, String firstYaml, String secondYaml) {
        System.out.println("\nFile content:");
        System.out.println(StringUtils.debugString(fileContent));

        traceParser(fileContent, "File Content");
        traceParser(firstYaml, "Emitted Content");

        System.out.println("\n=== First AST ===");
        TestUtils.dumpYamlAst(firstAst, "");
        System.out.println("\n=== Second AST ===");
        TestUtils.dumpYamlAst(secondAst, "");

        // System.err.println("\n" + generateDiffMessage(filename, firstYaml, secondYaml) + "\n");

        System.out.println("\n=== FILE CONTENT ===");
        System.out.println(StringUtils.debugString(fileContent));
        System.out.println("\n=== FIRST EMIT ===");
        System.out.println(StringUtils.debugString(firstYaml));
        System.out.println("\n=== SECOND EMIT ===");
        System.out.println(StringUtils.debugString(secondYaml));
    }

    protected void testIntegrity(String yaml) {
        YamlNode root = parser.parse(yaml);
        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokens());
        }

        // YamlNode resolvedRoot = CascaraYaml.resolve(root);

        YamlOptions options = new YamlOptions()
            .setOutputResolvedAliases(false);
        serializer.setOptions(options);

        String emitted = serializer.toString(root);

        // TODO: Parser needs to ensure trailing whitespace is correct

        if (yaml.endsWith("\n") && !emitted.endsWith("\n")) {
            emitted += "\n";
        }
        if (!yaml.endsWith("\n") && emitted.endsWith("\n")) {
            yaml += "\n";
        }

        if (!yaml.equals(emitted)) {
            reportMismatch(yaml, emitted);
        }

        TestUtils.assertEquals(yaml, emitted);
    }

    protected void reportMismatch(String expected, String actual) {
        try {
            reporter.debug("Expected YAML:");
            reporter.getWriter(Level.DEBUG).write(2, StringUtils.debugString(expected));
            reporter.debug("Actual YAML:");
            reporter.getWriter(Level.DEBUG).write(2, StringUtils.debugString(actual));

            reporter.debug("Expected YAML:");
            reporter.getWriter(Level.DEBUG).write(2, expected);
            reporter.debug("Actual YAML:");
            reporter.getWriter(Level.DEBUG).write(2, actual);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
