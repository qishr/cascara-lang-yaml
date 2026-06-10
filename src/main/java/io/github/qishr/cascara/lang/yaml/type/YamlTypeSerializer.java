package io.github.qishr.cascara.lang.yaml.type;

import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.exception.SerializerException;
import io.github.qishr.cascara.common.type.AbstractTypeDescriptor;
import io.github.qishr.cascara.common.type.TypeSerializer;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlSerializerException;

public abstract class YamlTypeSerializer<T> extends AbstractTypeDescriptor implements TypeSerializer<T> {
    protected YamlTypeSerializer(Class<T> javaType) {
        super(javaType);
    }

    /// Transforms a concrete Java object into its structural AST representation.
    ///
    /// @param value    The live runtime object instance to serialize.
    /// @return         The matching structural AstNode graph.
    public YamlNode serialize(T value) throws SerializerException {
        return null;
    }

    /// Deserializes an AST node into a strongly-typed Java object.
    ///
    /// @param node        The structural AST node being parsed (e.g., YamlScalarNode, YamlMapNode).
    /// @return            The fully constructed Java object instance.
    /// @throws SerializerException If the node structure violates the type constraints.
    public T deserialize(YamlNode node) throws SerializerException {
        return null;
    }

    /// This explicitly overrides the top-level multi-format interface contract
    @Override
    public final T deserialize(AstNode node) throws SerializerException {
        if (node instanceof YamlNode yamlNode) {
            return this.deserialize(yamlNode); // Safely forwards to your abstract method
        }
        throw new YamlSerializerException(
            GenericDiagnosticCode.ERROR,
            "Expected a YamlNode branch, but received: " + node.getClass().getSimpleName()
        );
    }
}
