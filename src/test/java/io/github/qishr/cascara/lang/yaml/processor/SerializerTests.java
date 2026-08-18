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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.lang.type.UriTypeDescriptor;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.util.ColorDefinition;
import io.github.qishr.cascara.lang.yaml.util.LongObject;
import io.github.qishr.cascara.lang.yaml.util.ObjectWithEnumField;
import io.github.qishr.cascara.lang.yaml.util.SettingsTestClass;
import io.github.qishr.cascara.lang.yaml.util.Stringy;
import io.github.qishr.cascara.lang.yaml.util.TestState;
import io.github.qishr.cascara.lang.yaml.util.UriTestClass;
import io.github.qishr.cascara.lang.yaml.util.ObjectWithEnumField.TestEnum;


class SerializerTests extends SerializerTestBase {

    @Test
    void test_stringy() throws SerializerException {
        Stringy stringy = new Stringy("test");
        // YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = serializer.toAst(stringy);
        String string = serializer.toString(yaml);
        assertEquals("string: test", string);
    }

    @Test
    void test_colordef() throws SerializerException {
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

        // YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = serializer.toAst(colordef);
        String string = serializer.toString(yaml);
        assertNotNull(string);
        assertNotNull(yaml);
    }


    @Test
    void test_stringy_quotes() throws SerializerException {
        Stringy stringy = new Stringy("one \"two\" three");
        // YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = serializer.toAst(stringy);
        String string = serializer.toString(yaml);
        assertEquals("string: one \"two\" three", string);
    }

    @Test
    void test_quotedSequenceItem() throws SerializerException {
        String yamlString = "disabledModules: \n" + //
                        "  - \"cascara.module.toolbar\"\n";
        // YamlSerializer yamlSerializer = new YamlSerializer();
        TestState t = serializer.fromString(yamlString, TestState.class);
        assertEquals(1, t.disabledModules.size());
        assertEquals("cascara.module.toolbar", t.disabledModules.getFirst());
    }

    @Test
    void test_stringWithLongValue() throws SerializerException {
        Stringy stringy = new Stringy("00000555");
        // YamlSerializer yamlSerializer = new YamlSerializer();
        String yaml = serializer.toString(stringy);
        Stringy answer = serializer.fromString(yaml, Stringy.class);
        assertEquals("00000555", answer.getString());
    }

    @Test
    void test_uri() throws SerializerException {
        UriTestClass uri = new UriTestClass();

        // System.out.println("Is named module: " + UriTestClass.class.getModule().isNamed());

        uri.uri = URI.create("http://io.com");
        // YamlSerializer yamlSerializer = new YamlSerializer();

        UriTypeDescriptor uriTypeDescriptor = new UriTypeDescriptor();
        serializer.registerTypeDescriptor(uriTypeDescriptor);

        String yaml = serializer.toString(uri);
        UriTestClass answer = serializer.fromString(yaml, UriTestClass.class);
        assertEquals("http://io.com", answer.uri.toString());
    }

    @Test
    void test_map_boolean() throws SerializerException {
        String yamlString = "dumpCss: true\n";
        // YamlSerializer yamlSerializer = new YamlSerializer();
        SettingsTestClass t = serializer.fromString(yamlString, SettingsTestClass.class);
        assertEquals(true, t.getOtherSettings().get("dumpCss"));
    }

    @Test
    void test_long_object() throws SerializerException {
        String yamlString = "value: 1\n";
        // YamlSerializer yamlSerializer = new YamlSerializer();
        LongObject t = serializer.fromString(yamlString, LongObject.class);
        assertEquals(1, t.getValue());
    }

    @Test
    void test_nullMappingToObject() throws SerializerException {
        // 'security:' is present, but has no value (null scalar)
        String yamlString = "disabledModules: []\n" +
                            "security: \n";

        // YamlSerializer yamlSerializer = new YamlSerializer();

        // This is where it currently fails:
        // The parser returns a null scalar, but the mapper
        // expects to see tokens for a NestedConfig object.
        TestState t = serializer.fromString(yamlString, TestState.class);

        assertNotNull(t);
        assertNull(t.security, "The security object should be null in the Java state");
    }

    @Test
    void test_objectWithEnumField() throws SerializerException {

        ObjectWithEnumField owef = new ObjectWithEnumField();
        owef.testEnum = ObjectWithEnumField.TestEnum.TWO;

        // YamlSerializer yamlSerializer = new YamlSerializer();

        String yaml = serializer.toString(owef);


        // This is where it currently fails:
        // The parser returns a null scalar, but the mapper
        // expects to see tokens for a NestedConfig object.
        ObjectWithEnumField deserialized = serializer.fromString(yaml, ObjectWithEnumField.class);

        assertNotNull(deserialized);
        assertEquals(TestEnum.TWO, deserialized.testEnum);
    }

}

