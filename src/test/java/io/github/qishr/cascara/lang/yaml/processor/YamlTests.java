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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.diagnostic.Diagnostic.Level;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.token.YamlToken;

class YamlTests {

  private static final Level LEVEL = Level.DEBUG;

    @Test
    void test_rootLevel_arrayAfterEmptyArray() {
        String yamlString = "name: \"nameval\"\n" +
                            "emptyarray: \n" +
                            "array:\n" +
                            "  - value1\n" +
                            "  - value2\n" +
                            "";

        // TODO: diagnostic level in one place for all tests?
        // YamlAstParser parser = new YamlAstParser().setReporter(new StandardReporter().setLevel(LEVEL));
        YamlAstParser parser = new YamlAstParser();
        parser.setReporter(new StandardReporter().setLevel(LEVEL));

        YamlMap yaml = (YamlMap)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        List<YamlNode> array = yaml.getSequence("array").getChildren();
        assertEquals(2, array.size());
    }

    @Test
    void test_subLevel_arrayAfterEmptyArray() {
        String yamlString = "object:\n" + //
                            "  emptyarray:\n" + //
                            "  array:\n" + //
                            "    - value1\n" + //
                            "    - value2\n" + //
                            "";
        YamlAstParser parser = new YamlAstParser();
        parser.setReporter(new StandardReporter().setLevel(LEVEL));

        YamlMap yaml = (YamlMap)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        YamlMap object = yaml.getMap("object");
        List<YamlMapEntry> entries = object.getEntries();

        YamlMapEntry entry1 = entries.getFirst();
        YamlNode key1 = entry1.getKey();
        if (key1 instanceof YamlScalar scalar) {
            assertEquals("emptyarray", scalar.asString());
        }

        YamlMapEntry entry2 = entries.getLast();
        YamlNode key2 = entry2.getKey();
        if (key2 instanceof YamlScalar scalar) {
            assertEquals("array", scalar.asString());
        }
    }

    @Test
    void test_stringContaining_quotes() {
        String yamlString = "name: \"one \\\"two\\\" three\"";

        YamlAstParser parser = new YamlAstParser();
        parser.setReporter(new StandardReporter().setLevel(LEVEL));

        YamlMap yaml = (YamlMap)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        String name = yaml.getString("name");
        assertEquals("one \"two\" three", name);
    }

    @Test
    void test_stringContaining_newline() {
        String yamlString = "name: \"One\nTwo\"";

        YamlAstParser parser = new YamlAstParser();
        parser.setReporter(new StandardReporter().setLevel(LEVEL));

        YamlMap yaml = (YamlMap)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        String name = yaml.getString("name");
        // assertEquals("One\nTwo", name);
        assertEquals("One Two", name);
    }

    @Test
    void test_startsWithComment() {
        String yamlString = "#comment\nkey: value\n";

        YamlAstParser parser = new YamlAstParser();
        parser.setReporter(new StandardReporter().setLevel(LEVEL));

        YamlMap yaml = (YamlMap)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        String value = yaml.getString("key");
        assertEquals("value", value);
    }

    @Test
    void test_startsWithBracket() {
        String yamlString = """
            [
              {
                "key": value
              }
            ]
            """;
        YamlAstParser parser = new YamlAstParser()
                .setReporter(new StandardReporter().setLevel(LEVEL));

        YamlSequence seq = (YamlSequence)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        YamlNode first = seq.get(0);
        assertInstanceOf(YamlMap.class, first);

        YamlMap map = (YamlMap) first;

        String value = map.getString("key");
        assertEquals("value", value);
    }

    @Test
    void test_startsWithBracket2() {
        String yamlString = """
            [
                {
                    "jmhVersion" : "1.37",
                    "benchmark" : "net.vansencool.benchmark.runner.HotBenchmark.cascaraYaml_complex",
                    "mode" : "thrpt",
                    "threads" : 1,
                    "forks" : 1,
                    "jvm" : "/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home/bin/java",
                    "jvmArgs" : [
                    ],
                    "jdkVersion" : "25",
                    "vmName" : "OpenJDK 64-Bit Server VM",
                    "vmVersion" : "25+36-LTS",
                    "warmupIterations" : 3,
                    "warmupTime" : "1 s",
                    "warmupBatchSize" : 1,
                    "measurementIterations" : 3,
                    "measurementTime" : "2 s",
                    "measurementBatchSize" : 1,
                    "primaryMetric" : {
                        "score" : 0.06774655987453963,
                        "scoreError" : 0.06074857274996886,
                        "scoreConfidence" : [
                            0.0069979871245707745,
                            0.12849513262450848
                        ],
                        "scorePercentiles" : {
                            "0.0" : 0.06419552824878388,
                            "50.0" : 0.06824519394305273,
                            "90.0" : 0.07079895743178226,
                            "95.0" : 0.07079895743178226,
                            "99.0" : 0.07079895743178226,
                            "99.9" : 0.07079895743178226,
                            "99.99" : 0.07079895743178226,
                            "99.999" : 0.07079895743178226,
                            "99.9999" : 0.07079895743178226,
                            "100.0" : 0.07079895743178226
                        },
                        "scoreUnit" : "ops/ms",
                        "rawData" : [
                            [
                                0.06419552824878388,
                                0.07079895743178226,
                                0.06824519394305273
                            ]
                        ]
                    },
                    "secondaryMetrics" : {
                    }
                }
            ]
            """;
        YamlAstParser parser = new YamlAstParser()
                .setReporter(new StandardReporter().setLevel(LEVEL));

        YamlSequence seq = (YamlSequence)parser.parse(yamlString);
        TestUtils.dumpTokens(parser.getTokens());

        YamlNode first = seq.get(0);
        assertInstanceOf(YamlMap.class, first);
        assertNotNull(seq);
    }

    // Search for test_ahh in YamlAstParser for the fix to this
    @Test
    void test_ahh() {
        String yamlString = """
          a:
            - b: w
              c:
                - x
            - d: y
          """;

        YamlTokenizer tokenizer = new YamlTokenizer();
        List<YamlToken> tokens = tokenizer.tokenize(yamlString);
        TestUtils.dumpTokens(tokens);

        YamlAstParser parser = new YamlAstParser()
                .setReporter(new StandardReporter().setLevel(LEVEL));


        assertDoesNotThrow(() -> parser.parse(yamlString));
    }
}

