package io.github.qishr.cascara.lang.yaml.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.util.StringUtils;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class SerializerRoundTripTests extends SerializerTestBase {


    @BeforeEach
    protected void setup() {
        super.setup();

        YamlOptions options = new YamlOptions()
            .setOutputExpandedStyle(true)
            .setStrict(true);

        parser.setOptions(options);
        emitter.setOptions(options);
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

    // See also: test9MMWb
    @Test
    void valid_16_keys_explicit() {
        validate("16-keys-explicit.yaml");
    }

    @Test
    void valid_02_flow_styles() {
        validate("02-flow-styles.yaml");
    }





    @Test
    void testRoundTripPreservesStructure() throws Exception {
        String original = "records:\n  -\n    id: \"1\"\n    tags:\n      -\n        a";

        if (DEBUG) {
            TestUtils.dumpTokens(parser.getTokenizer().tokenize(original));
        }

        // 1. Parse
        YamlMap originalMap = (YamlMap)parser.parse(original);

        // TestUtils.dumpTokens(parser.getTokens());

        // 2. Emit
        // String emitted = new YamlEmitter().setOptions(options).emit(originalMap);
        String emitted = serializer.toString(originalMap);

        if (DEBUG) {
            System.out.println("--- EMITTED START ---");
            System.out.println(emitted);
            System.out.println("--- EMITTED END ---");

            System.out.println("Original: " + StringUtils.debugString(original));
            System.out.println("Emitted : " + StringUtils.debugString(emitted));
        }

        if (DEBUG) {
            YamlTokenizer tokenizer = new YamlTokenizer()
                .setReporter(new StandardReporter().setLevel(Level.INFO));
            List<YamlToken> tokens = tokenizer.tokenize(emitted);
            TestUtils.dumpTokens(tokens);
        }


        // 3. Re-Parse
        YamlMap reParsedMap = (YamlMap)parser.parse(emitted);

        // 4. Verify logical equality
        assertEquals(originalMap.getEntries().size(), reParsedMap.getEntries().size());

        // Compare the first record's ID:
        // root (map) -> records (seq) -> [0] (map) -> id (scalar)
        YamlSequence seq = (YamlSequence) reParsedMap.get("records");
        YamlMap record = (YamlMap) seq.get(0);

        // Use getString helper from MapAstNode/YamlMap
        assertEquals("1", record.getString("id"));
    }

}
