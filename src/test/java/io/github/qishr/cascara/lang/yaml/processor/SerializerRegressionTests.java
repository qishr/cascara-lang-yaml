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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;
import io.github.qishr.cascara.lang.yaml.util.ContentTypeRegistryTestClass;
import io.github.qishr.cascara.lang.yaml.util.ContentTypeTestClass;

public class SerializerRegressionTests {
    @Test
    void test_contentTypes() throws SerializerException {

        ContentTypeRegistryTestClass registry = new ContentTypeRegistryTestClass();
        ContentTypeTestClass type1 = new ContentTypeTestClass();
        type1.getMimeTypes().add("text/plain");
        type1.setCanonicalId("text/plain");
        type1.setCanonicalName("Plain Text");
        type1.getSuffixes().add(".text");
        type1.getSuffixes().add(".txt");
        registry.getRecords().add(type1);


        YamlSerializer yamlSerializer = new YamlSerializer();
        YamlNode yaml = yamlSerializer.toAst(registry);
        assertInstanceOf(YamlMap.class, yaml);

        YamlMap registryNode = (YamlMap) yaml;

        YamlNode recordsNode = registryNode.get("records");
        assertInstanceOf(YamlSequence.class, recordsNode);

        YamlSequence recordsSequence = (YamlSequence) recordsNode;

        YamlNode element1 = recordsSequence.get(0);
        assertInstanceOf(YamlMap.class, element1);

        YamlMap item1MapNode = (YamlMap) element1;
        assertEquals("Plain Text", item1MapNode.getString("canonicalName"));
     }
}
