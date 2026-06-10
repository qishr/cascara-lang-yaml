package io.github.qishr.cascara.lang.yaml.type;

import java.util.Base64;

import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlSerializerException;

public class ByteArraySerializer extends YamlTypeSerializer<byte[]> {
    public ByteArraySerializer() {
        super(byte[].class);
    }

	@Override
	public YamlNode serialize(byte[] value) throws SerializerException {
        String base64 = Base64.getEncoder().encodeToString(value);
        return new YamlScalarNode(base64);
    }

    @Override
	public byte[] deserialize(YamlNode node) throws SerializerException {
        if (node instanceof YamlScalarNode scalar) {
            try {
                return Base64.getDecoder().decode(scalar.asString());
            } catch (IllegalArgumentException e) {
                throw new YamlSerializerException(e, GenericDiagnosticCode.ERROR, "Malformed Base64 payload.");
            }
        }
        return new byte[]{};
    }
}

