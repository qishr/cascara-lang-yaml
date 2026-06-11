package io.github.qishr.cascara.lang.yaml.testclass;

import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.type.YamlTypeSerializer;

public class PersonSerializer extends YamlTypeSerializer<Person> {
    public PersonSerializer() {
        super(Person.class);
    }

	@Override
	public YamlNode serialize(Person value) throws SerializerException {
        return new YamlMapNode()
            .put("firstName", value.getFirstName())
            .put("lastName", value.getLastName())
            .put("age", value.getAge());
    }
}

