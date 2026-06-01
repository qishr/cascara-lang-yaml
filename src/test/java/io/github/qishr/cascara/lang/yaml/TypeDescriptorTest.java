package io.github.qishr.cascara.lang.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.type.LocalDateTimeDescriptor;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
import io.github.qishr.cascara.lang.yaml.processor.YamlSerializer;
import io.github.qishr.cascara.lang.yaml.testclass.TestTypeDescriptor1;
import io.github.qishr.cascara.lang.yaml.testclass.TestTypeDescriptor2;

public class TypeDescriptorTest {
    @Test
    void t1() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        LocalDateTime dt = LocalDateTime.now();
        TestTypeDescriptor1 test = new TestTypeDescriptor1(dt);

        YamlNode yaml = yamlSerializer.toAst(test);
        String string = new YamlEmitter().emit(yaml);

        String dateTimeString = dt.toString();

        assertEquals("dateTime: \"" + dateTimeString + "\"\n", string);

    }

    @Test
    void t2() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        Long dt = 1L;
        TestTypeDescriptor2 test = new TestTypeDescriptor2(dt);

        YamlNode yaml = yamlSerializer.toAst(test);
        String string = new YamlEmitter().emit(yaml);

        String dateTimeString = dt.toString();

        assertEquals("dateTime: " + dateTimeString + "\n", string);
        // assertEquals("dateTime: \"" + dateTimeString + "\"\n", string);

    }

    @Test
    void t22() {
        YamlSerializer yamlSerializer = new YamlSerializer();

        Long dt = 1L;
        // String dateTimeString = dt.toString();
        String text = "dateTime: 1\n";

        TestTypeDescriptor2 test = yamlSerializer.fromText(text, TestTypeDescriptor2.class);


        assertEquals(dt, test.getValue());

    }
}
