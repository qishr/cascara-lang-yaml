package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.type.LocalDateTimeTypeDescriptor;
import io.github.qishr.cascara.common.type.ByteArrayDescriptor;
import io.github.qishr.cascara.common.type.TypeDescriptor;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlSerializer;
import io.github.qishr.cascara.lang.yaml.testclass.LongInstant;
import io.github.qishr.cascara.lang.yaml.testclass.Person;
import io.github.qishr.cascara.lang.yaml.testclass.PersonSerializer;
import io.github.qishr.cascara.lang.yaml.testclass.TypeDescriptorTestClass;

public class ScalarDescriptorTest {
    @Test
    void testDateScalarDescriptor() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        LocalDateTimeTypeDescriptor dateScalarDescriptor = new LocalDateTimeTypeDescriptor();
        yamlSerializer.registerTypeDescriptor(dateScalarDescriptor);

        LocalDateTime dt = LocalDateTime.now();
        TypeDescriptorTestClass test = new TypeDescriptorTestClass(dt);

        YamlNode yaml = yamlSerializer.toAst(test);
        String string = new YamlEmitter().emit(yaml);

        String dateTimeString = dt.toString();

        assertEquals("dateTime: \"" + dateTimeString + "\"\n", string);
    }

    @Test
    void testLongInstant0() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        // TODO: This is essentially the same as testLongInstant1 and should be remvoed.
        Long dt = 1L;
        LongInstant test = new LongInstant(dt);

        YamlNode yaml = yamlSerializer.toAst(test);
        String string = new YamlEmitter().emit(yaml);

        String dateTimeString = dt.toString();

        assertEquals("value: " + dateTimeString + "\n", string);
    }

    @Test
    void testLongInstant1() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        Long dt = 1L;
        String text = "value: 1\n";

        LongInstant test = yamlSerializer.fromText(text, LongInstant.class);


        assertEquals(dt, test.getValue());

    }

    @Test
    void testCustomSerializer() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        TypeDescriptor<?> personSerializer = new PersonSerializer();

        yamlSerializer.registerTypeDescriptor(personSerializer);

        Person person = new Person("Dave", "Smith", "31");

        String yaml = yamlSerializer.toText(person);

        String expected = """
                firstName: "Dave"
                lastName: "Smith"
                age: "31"
                """;
        assertEquals(expected, yaml);
    }

    @Test
    void testByteArray() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        // THis is called here because tests runnig from Gradle don't have SPI
        yamlSerializer.registerTypeDescriptor(new ByteArrayDescriptor());

        Person person = new Person("Dave", "Smith", "31");
        person.setBytes(new byte[]{1,2,3});

        String yaml = yamlSerializer.toText(person);

        String expected = """
                firstName: "Dave"
                lastName: "Smith"
                personAge: "31"
                bytes: "AQID"
                """;
        assertEquals(expected, yaml);

        Person copy = yamlSerializer.fromText(yaml, Person.class);

        assertNotNull(copy);
        assertEquals(person.getFirstName(), copy.getFirstName());
        assertEquals(person.getLastName(), copy.getLastName());
        assertEquals(person.getAge(), copy.getAge());
        assertEquals(person.getBytes()[0], copy.getBytes()[0]);
        assertEquals(person.getBytes()[1], copy.getBytes()[1]);
        assertEquals(person.getBytes()[2], copy.getBytes()[2]);
    }
}
