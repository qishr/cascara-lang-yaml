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

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.type.ByteArrayDescriptor;
import io.github.qishr.cascara.common.lang.type.DateTimeTypeDescriptor;
import io.github.qishr.cascara.common.lang.type.TypeDescriptor;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.util.LongInstant;
import io.github.qishr.cascara.lang.yaml.util.Person;
import io.github.qishr.cascara.lang.yaml.util.PersonSerializer;
import io.github.qishr.cascara.lang.yaml.util.TypeDescriptorTestClass;

public class SerializerScalarDescriptorTests extends SerializerTestBase {
    @Test
    void testDateScalarDescriptor() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        DateTimeTypeDescriptor dateScalarDescriptor = new DateTimeTypeDescriptor();
        yamlSerializer.registerTypeDescriptor(dateScalarDescriptor);

        ZonedDateTime dt = ZonedDateTime.now();
        TypeDescriptorTestClass test = new TypeDescriptorTestClass(dt);

        YamlNode yaml = yamlSerializer.toAst(test);
        String string = serializer.toString(yaml);

        String dateTimeString = dt.toString();

        assertEquals("dateTime: " + dateTimeString, string);
    }

    @Test
    void testLongInstant0() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        // TODO: This is essentially the same as testLongInstant1 and should be remvoed.
        Long dt = 1L;
        LongInstant test = new LongInstant(dt);

        YamlNode yaml = yamlSerializer.toAst(test);
        String string = serializer.toString(yaml);

        String dateTimeString = dt.toString();

        assertEquals("value: " + dateTimeString, string);
    }

    @Test
    void testLongInstant1() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        Long dt = 1L;
        String text = "value: 1\n";

        LongInstant test = yamlSerializer.fromString(text, LongInstant.class);


        assertEquals(dt, test.getValue());

    }

    @Test
    void testCustomSerializer() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        TypeDescriptor<?> personSerializer = new PersonSerializer();

        yamlSerializer.registerTypeDescriptor(personSerializer);

        Person person = new Person("Dave", "Smith", "31");

        String yaml = yamlSerializer.toString(person);

        String expected = """
                firstName: Dave
                lastName: Smith
                age: "31"
                """;
        assertEquals(expected, yaml);
    }

    @Test
    void testByteArray() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        // TODO: Remove this...
        // yamlSerializer.setReporter(new StandardReporter().setLevel(Level.DEBUG));

        // THis is called here because tests runnig from Gradle don't have SPI
        yamlSerializer.registerTypeDescriptor(new ByteArrayDescriptor());

        Person person = new Person("Dave", "Smith", "31");
        person.setBytes(new byte[]{1,2,3});

        String yaml = yamlSerializer.toString(person);

        String expected = """
                firstName: Dave
                lastName: Smith
                personAge: "31"
                bytes: AQID
                """;
        assertEquals(expected, yaml);

        Person copy = yamlSerializer.fromString(yaml, Person.class);

        assertNotNull(copy);
        assertEquals(person.getFirstName(), copy.getFirstName());
        assertEquals(person.getLastName(), copy.getLastName());
        assertEquals(person.getAge(), copy.getAge());
        assertEquals(person.getBytes()[0], copy.getBytes()[0]);
        assertEquals(person.getBytes()[1], copy.getBytes()[1]);
        assertEquals(person.getBytes()[2], copy.getBytes()[2]);
    }
}
