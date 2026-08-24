package io.github.qishr.cascara.lang.yaml.processor;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalar;

public class AstNodePropertiesMapOfMapsTests extends AstNodePropertiesTestBase {

    //
    // Tag
    //

    @Test
    public void test_TagOnMap() {
        String yaml = """
            x: !!str
              y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar value = map.getScalar("y");

        // Confirm tag is on correct node
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals(STR_TAG, map.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_TAG, key.getTag());
        TestUtils.assertEquals(NO_TAG, value.getTag());
    }

    @Test
    public void test_TagOnKey() {
        String yaml = """
            x:
              !!str y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar value = map.getScalar("y");

        // Confirm tag is on correct node
        TestUtils.assertEquals("!!str", key.getTag());
        TestUtils.assertEquals(STR_TAG, key.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_TAG, map.getTag());
        TestUtils.assertEquals(NO_TAG, value.getTag());
    }

    @Test
    public void test_TagOnValue() {
        String yaml = """
            x:
              y: !!str v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar value = map.getScalar("y");

        // Confirm tag is on correct node
        TestUtils.assertEquals("!!str", value.getTag());
        TestUtils.assertEquals(STR_TAG, value.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_TAG, map.getTag());
        TestUtils.assertEquals(NO_TAG, key.getTag());
    }

    //
    // Anchor
    //

    @Test
    public void test_AnchorOnMap() {
        String yaml = """
            x: &a
              y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor is on correct node
        TestUtils.assertEquals("a", map.getAnchor());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
        TestUtils.assertEquals(NO_ANC, val.getAnchor());
    }

    @Test
    public void test_AnchorOnKey() {
        String yaml = """
            x:
              &a y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor is on correct node
        TestUtils.assertEquals("a", key.getAnchor());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
        TestUtils.assertEquals(NO_ANC, val.getAnchor());
    }

    @Test
    public void test_AnchorOnValue() {
        String yaml = """
            x:
              y: &a v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor is on correct node
        TestUtils.assertEquals("a", val.getAnchor());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
    }

    //
    // Anchor then tag
    //

    @Test
    public void test_AnchorThenTagOnMap() {
        String yaml = """
            x: &a !!str
              y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor and tag are on correct node
        TestUtils.assertEquals("a", map.getAnchor());
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals(STR_TAG, map.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
        TestUtils.assertEquals(NO_TAG, key.getTag());
        TestUtils.assertEquals(NO_ANC, val.getAnchor());
        TestUtils.assertEquals(NO_TAG, val.getTag());
    }

    @Test
    public void test_AnchorThenTagOnKey() {
        String yaml = """
            x:
              &a !!str y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor and tag are on correct node
        TestUtils.assertEquals("a", key.getAnchor());
        TestUtils.assertEquals("!!str", key.getTag());
        TestUtils.assertEquals(STR_TAG, key.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
        TestUtils.assertEquals(NO_TAG, map.getTag());
        TestUtils.assertEquals(NO_ANC, val.getAnchor());
        TestUtils.assertEquals(NO_TAG, val.getTag());
    }

    @Test
    public void test_AnchorThenTagOnValue() {
        String yaml = """
            x:
              y: &a !!str v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor and tag are on correct node
        TestUtils.assertEquals("a", val.getAnchor());
        TestUtils.assertEquals("!!str", val.getTag());
        TestUtils.assertEquals(STR_TAG, val.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
        TestUtils.assertEquals(NO_TAG, map.getTag());
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
        TestUtils.assertEquals(NO_TAG, key.getTag());
    }

    //
    // Tag then anchor
    //

    @Test
    public void test_TagThenAnchorOnMap() {
        String yaml = """
            x: !!str &a
              y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor and tag are on correct node
        TestUtils.assertEquals("a", map.getAnchor());
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals(STR_TAG, map.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
        TestUtils.assertEquals(NO_TAG, key.getTag());
        TestUtils.assertEquals(NO_ANC, val.getAnchor());
        TestUtils.assertEquals(NO_TAG, val.getTag());
    }

    @Test
    public void test_TagThenAnchorOnKey() {
        String yaml = """
            x:
              !!str &a y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor and tag are on correct node
        TestUtils.assertEquals("a", key.getAnchor());
        TestUtils.assertEquals("!!str", key.getTag());
        TestUtils.assertEquals(STR_TAG, key.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
        TestUtils.assertEquals(NO_TAG, map.getTag());
        TestUtils.assertEquals(NO_ANC, val.getAnchor());
        TestUtils.assertEquals(NO_TAG, val.getTag());
    }

    @Test
    public void test_TagThenAnchorOnValue() {
        String yaml = """
            x:
              y: !!str &a v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();
        YamlScalar val = map.getScalar("y");

        // Confirm anchor and tag are on correct node
        TestUtils.assertEquals("a", val.getAnchor());
        TestUtils.assertEquals("!!str", val.getTag());
        TestUtils.assertEquals(STR_TAG, val.getResolvedTag());

        // Confirm tag is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
        TestUtils.assertEquals(NO_TAG, map.getTag());
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
        TestUtils.assertEquals(NO_TAG, key.getTag());
    }

    //
    // Tag on map with variations of tag and anchor on key
    //

    @Test
    public void test_TagOnMap_TagOnKey() {
        // String yaml = """
        //     x: !!str
        //       !!str y: v
        //     """;
        String yaml = """
            x: !!str
               !!str y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tags are on correct nodes
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals("!!str", key.getTag());
    }

    @Test
    public void test_TagOnMap_AnchorOnKey() {
        String yaml = """
            x: !!str
              &a y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tag and anchor are on correct nodes
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals("a", key.getAnchor());

        // Confirm tag is not on wrong node
        TestUtils.assertEquals(NO_TAG, key.getTag());
    }

    @Test
    public void test_TagOnMap_TagThenAnchorOnKey() {
        String yaml = """
            x: !!str
              !!str &a y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tag and anchor are on correct nodes
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals("!!str", key.getTag());
        TestUtils.assertEquals("a", key.getAnchor());

        // Confirm anchor is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
    }

    @Test
    public void test_TagOnMap_AnchorThenTagOnKey() {
        String yaml = """
            x: !!str
              &a !!str y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tag and anchor are on correct nodes
        TestUtils.assertEquals("!!str", map.getTag());
        TestUtils.assertEquals("!!str", key.getTag());
        TestUtils.assertEquals("a", key.getAnchor());

        // Confirm anchor is not on wrong nodes
        TestUtils.assertEquals(NO_ANC, map.getAnchor());
    }

    //
    // Anchor on map with variations of tag and anchor on key
    //

    @Test
    public void test_AnchorOnMap_TagOnKey() {
        String yaml = """
            x: &a
              !!str y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm anchor and tag are on correct nodes
        TestUtils.assertEquals("a", map.getAnchor());
        TestUtils.assertEquals("!!str", key.getTag());

        // Confirm anchor and tag are not on wrong nodes
        TestUtils.assertEquals(NO_ANC, key.getAnchor());
        TestUtils.assertEquals(NO_TAG, map.getTag());
    }

    @Test
    public void test_AnchorOnMap_AnchorOnKey() {
        String yaml = """
            x: &a
              &b y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tag and anchor are on correct nodes
        TestUtils.assertEquals("a", map.getAnchor());
        TestUtils.assertEquals("b", key.getAnchor());
    }

    @Test
    public void test_AnchorOnMap_TagThenAnchorOnKey() {
        String yaml = """
            x: &a
              !!str &b y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tag and anchor are on correct nodes
        TestUtils.assertEquals("a", map.getAnchor());
        TestUtils.assertEquals("!!str", key.getTag());
        TestUtils.assertEquals("b", key.getAnchor());

        // Confirm tag is not on wrong node
        TestUtils.assertEquals(NO_TAG, map.getTag());
    }

    @Test
    public void test_AnchorOnMap_AnchorThenTagOnKey() {
        String yaml = """
            x: &a
              &b !!str y: v
            """;

        tokenize(yaml);
        YamlMap root = (YamlMap) parser.parse(yaml);

        YamlMap map = root.getMap("x");
        YamlMapEntry entry = map.getEntry(0);
        YamlScalar key = (YamlScalar) entry.getKey();

        // Confirm tag and anchors are on correct nodes
        TestUtils.assertEquals("a", map.getAnchor());
        TestUtils.assertEquals("b", key.getAnchor());
        TestUtils.assertEquals("!!str", key.getTag());

        // Confirm tag is not on wrong node
        TestUtils.assertEquals(NO_TAG, map.getTag());
    }

    //
    // Tag then anchor on map with variations of tag and anchor on key
    //

    // TODO:
    // - TagThenAnchorOnMap with:
    //     - TagOnKey
    //     - AnchorOnKey
    //     - TagThenAnchorOnKey
    //     - AnchorThenTagOnKey

    //
    // Anchor then tag on map with variations of tag and anchor on key
    //

    // TODO:
    // - AnchorThenTagOnMap with:
    //     - TagOnKey
    //     - AnchorOnKey
    //     - TagThenAnchorOnKey
    //     - AnchorThenTagOnKey
    //

    // TODO: anchors nd tags on different lines for same node

    // TODO: Confirm that YamlParserException is thrown for more than
    // one tag or more than one anchor on a node.

    // TODO (in another test class)
    //
    // Explicit Block Key Indicator (?): Testing properties on keys defined
    // with explicit indicators (? &a !!str key\n: value).
    //
    // Value Tagged with Custom/Local Tags (!mytype): Verifying non-canonical
    // tag resolution on maps vs scalar keys.
    //
    // Flow Maps inside Block Maps: Property attachment when nesting flow
    // mappings (x: &a { &b y: v }).
}
