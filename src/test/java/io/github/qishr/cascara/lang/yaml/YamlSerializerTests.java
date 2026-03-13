package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlSerializerException;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlSerializer;
import io.github.qishr.cascara.lang.yaml.testclass.ColorDefinition;
import io.github.qishr.cascara.lang.yaml.testclass.LongObject;
import io.github.qishr.cascara.lang.yaml.testclass.SettingsTestClass;
import io.github.qishr.cascara.lang.yaml.testclass.Stringy;
import io.github.qishr.cascara.lang.yaml.testclass.TestState;
import io.github.qishr.cascara.lang.yaml.testclass.UriTestClass;


class YamlSerializerTests {

    @Test
    void test_stringy() throws YamlSerializerException {
        Stringy stringy = new Stringy("test");
        YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = yamlSerializer.toAst(stringy);
        String string = new YamlEmitter().emit(yaml);
        assertEquals("string: \"test\"\n", string);
    }

    @Test
    void test_colordef() throws YamlSerializerException {
        ColorDefinition colordef = new ColorDefinition();
        colordef.setId("id");
        colordef.setName("name");
        colordef.setHexColor("#DDDDDD");
        colordef.setBaseColorId("id");
        colordef.setPaletteColorId("id");
        colordef.setTransformId("id");
        colordef.setTransformDefinition("transform");
        colordef.setLeftHexColor("left");
        colordef.setRightHexColor("right");
        colordef.setLerp("lerp");

        Stringy stringy = new Stringy("test");
        YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = yamlSerializer.toAst(colordef);
        String string = new YamlEmitter().emit(yaml);
        System.err.println(string);
        assertNotNull(yaml);
    }


    @Test
    void test_stringy_quotes() throws YamlSerializerException {
        Stringy stringy = new Stringy("one \"two\" three");
        YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = yamlSerializer.toAst(stringy);
        String string = new YamlEmitter().emit(yaml);
        assertEquals("string: \"one \\\"two\\\" three\"\n", string);
    }

    @Test
    void test_quotedSequenceItem() throws YamlSerializerException {
        String yamlString = "disabledModules: \n" + //
                        "  - \"cascara.module.toolbar\"\n";
        YamlSerializer yamlSerializer = new YamlSerializer();
        TestState t = yamlSerializer.fromText(yamlString, TestState.class);
        assertEquals(1, t.disabledModules.size());
        assertEquals("cascara.module.toolbar", t.disabledModules.getFirst());
    }

    @Test
    void test_stringWithLongValue() throws YamlSerializerException {
        Stringy stringy = new Stringy("00000555");
        YamlSerializer yamlSerializer = new YamlSerializer();
        String yaml = yamlSerializer.toText(stringy);
        Stringy answer = yamlSerializer.fromText(yaml, Stringy.class);
        assertEquals("00000555", answer.getString());
    }

    @Test
    void test_uri() throws YamlSerializerException {
        UriTestClass uri = new UriTestClass();

        uri.uri = URI.create("http://io.com");
        YamlSerializer yamlSerializer = new YamlSerializer();
        String yaml = yamlSerializer.toText(uri);
        UriTestClass answer = yamlSerializer.fromText(yaml, UriTestClass.class);
        assertEquals("http://io.com", answer.uri.toString());
    }

    @Test
    void test_map_boolean() throws YamlSerializerException {
        String yamlString = "dumpCss: true\n";
        YamlSerializer yamlSerializer = new YamlSerializer();
        SettingsTestClass t = yamlSerializer.fromText(yamlString, SettingsTestClass.class);
        assertEquals(true, t.getOtherSettings().get("dumpCss"));
    }

    @Test
    void test_long_object() throws YamlSerializerException {
        String yamlString = "value: 1\n";
        YamlSerializer yamlSerializer = new YamlSerializer();
        LongObject t = yamlSerializer.fromText(yamlString, LongObject.class);
        assertEquals(1, t.getValue());
    }

}

