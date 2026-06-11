package io.github.qishr.cascara.lang.yaml.processor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.annotation.AnyGetter;
import io.github.qishr.cascara.common.lang.annotation.AnySetter;
import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.annotation.Serializable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.processor.Serializer;
import io.github.qishr.cascara.common.service.ServiceProviderLayer;
import io.github.qishr.cascara.common.service.ServiceMetadata;
import io.github.qishr.cascara.common.type.Primitive;
import io.github.qishr.cascara.common.type.ScalarDescriptor;
import io.github.qishr.cascara.common.type.TypeDescriptor;
import io.github.qishr.cascara.common.util.ReflectionUtils;
import io.github.qishr.cascara.lang.yaml.YamlPrimitiveDelegate;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlDiagnosticCode;
import io.github.qishr.cascara.lang.yaml.exception.YamlSerializerException;
import io.github.qishr.cascara.lang.yaml.type.YamlTypeSerializer;

/// Standard implementation for YAML serialization.
public class YamlSerializer extends AbstractYamlProcessor<YamlSerializer> implements Serializer<YamlNode> {
    private static YamlPrimitiveDelegate YAML_PRIMITIVE_DELEGATE = new YamlPrimitiveDelegate();
    private final YamlParser parser = new YamlParser(); // TODO: Not thread safe
    private Map<Class<?>,TypeDescriptor<?>> typeDescriptors = new HashMap<>();

    public YamlSerializer() {
    }

    @Override protected YamlSerializer self() { return this; }

    /// {@inheritDoc}
    @Override
    public YamlSerializer setReporter(Reporter reporter) {
        this.reporter = reporter;
        parser.setReporter(reporter);
        return self();
    }

    //
    // Serializer Implementation
    //

    @Override
    public void registerTypeDescriptor(TypeDescriptor<?> typeDescriptor) {
        typeDescriptors.put(typeDescriptor.getJvmType(), typeDescriptor);
    }

    @Override
    public String toText(Object object) {
        // Step 1: Object -> AST
        YamlNode ast = toAst(object);
        // Step 2: AST -> String
        return new YamlEmitter().setOptions(options).emit(ast);
    }

    @Override
    public <C> C fromText(String text, Class<C> clazz) {
        // Step 1: String -> AST
        YamlNode ast = parser.parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, clazz);
    }

    @Override
    public YamlNode toAst(Object object) {
        return serialize(object);
    }

    @Override
    public <C> C fromAst(YamlNode astNode, Class<C> clazz) {
        return (C) deserialize(astNode, clazz);
    }

    //
    // Serialization Methods
    //

    /// Creates the appropriate YamlNode (Scalar, Sequence, or Map) based on the Java value type.
	@SuppressWarnings("unchecked")
    private YamlNode serialize(Object value) {
        if (isPrimitive(value)) {
            return new YamlScalarNode(value);
        }

        TypeDescriptor<?> typeDescriptor = getTypeDescriptor(value.getClass());

        if (typeDescriptor != null) {
            if (typeDescriptor instanceof YamlTypeSerializer typeSerializer) {
				return typeSerializer.serialize(value);
            }

            if (typeDescriptor instanceof ScalarDescriptor descriptor) {
                Primitive primitive;
                try {
                    // If you're wondering why this is not a String,
                    // an Instant is not a String - it's a Long
                    primitive = descriptor.toPrimitive(value).setDelegate(YAML_PRIMITIVE_DELEGATE);
                } catch (Exception e) {
                    throw new YamlSerializerException(e, YamlDiagnosticCode.FAILED_TO_MAP_AST, value.getClass().getSimpleName(), e.getMessage());
                }
                return YamlScalarNode.fromPrimitive(primitive);
            }
        }

        if (value instanceof List<?> list) {
            return serializeList(list);
        }

        if (value instanceof Map<?, ?> map) {
            return serializeMap(map);
        }

        // TODO: ALL OBJECTS should be handled this way. @Serializable should not be neccesary
        if (value.getClass().isAnnotationPresent(Serializable.class)) {
            return serializeObject(value);
        }

        throw new YamlSerializerException(YamlDiagnosticCode.FAILED_SERIALIZE, value.getClass());
    }

    private YamlMapNode serializeObject(Object object) {
        Class<?> clazz = object.getClass();
        YamlMapNode rootMap = new YamlMapNode();

        for (Field field : getAllFields(clazz)) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(DataIgnore.class)) continue;

            if (field.isAnnotationPresent(AnySetter.class)) {
                Map<?, ?> map;
                try {
                    map = (Map<?, ?>) field.get(object);
                } catch (IllegalAccessException e) {
                    throw new YamlSerializerException(e, YamlDiagnosticCode.FIELD_NOT_ACCESSIBLE, field.getName());
                }
                if (map != null) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        YamlNode keyNode = serialize(entry.getKey());
                        YamlNode valueNode = serialize(entry.getValue());
                        rootMap.put(keyNode, valueNode);
                    }
                }
                continue;
            }

            Object value;
			try {
				value = field.get(object);
			} catch (IllegalAccessException e) {
                throw new YamlSerializerException(e, YamlDiagnosticCode.FIELD_NOT_ACCESSIBLE, field.getName());
			}
            if (value != null) {
                String keyName = field.isAnnotationPresent(DataField.class)
                    ? field.getAnnotation(DataField.class).key() : field.getName();
                if (keyName == null || keyName.isEmpty()) keyName = field.getName();

                YamlScalarNode keyNode = new YamlScalarNode(keyName, QuoteStyle.PLAIN);

                YamlNode valueNode = serialize(value);
                rootMap.put(keyNode, valueNode);
            }
        }

        // 2. Process dynamic settings (@YamlAnyGetter)
        for (Method method : getAllMethods(clazz)) { //.getDeclaredMethods()) {
            if (method.isAnnotationPresent(AnyGetter.class)) {
                method.setAccessible(true);

                // Invoke the method to get the Map
                Object result;

				try {
					result = method.invoke(object);

                    // TODO: Handle these gracefully

                    // IllegalAccessException - if this Method object is enforcing Java language access control and the underlying method is inaccessible.
                    // IllegalArgumentException - if the method is an instance method and the specified object argument is not an instance of the class or interface declaring the underlying method (or of a subclass or implementor thereof); if the number of actual and formal parameters differ; if an unwrapping conversion for primitive arguments fails; or if, after possible unwrapping, a parameter value cannot be converted to the corresponding formal parameter type by a method invocation conversion.
                    // InvocationTargetException - if the underlying method throws an exception.
                    // NullPointerException - if the specified object is null and the method is an instance method.
                    // ExceptionInInitializerError - if the initialization provoked by this method fails.

                } catch (IllegalAccessException e) {
                    throw new YamlSerializerException(e, YamlDiagnosticCode.FIELD_NOT_ACCESSIBLE, method.getName());
				} catch (InvocationTargetException e) {
                    throw new YamlSerializerException(e, YamlDiagnosticCode.INVOCATION_TARGET_EXCEPTION, method.getName());
				}

                if (result instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        YamlNode keyNode = serialize(entry.getKey());
                        YamlNode valueNode = serialize(entry.getValue());
                        rootMap.put(keyNode, valueNode);
                    }
                }
            }
        }

        return rootMap;
    }


    /// Serializes a List into a YamlSequence.
    private YamlSequenceNode serializeList(List<?> list) {
        YamlSequenceNode sequence = new YamlSequenceNode();
        for (Object item : list) {
            if (item == null) continue;

            // Check if the item is itself serializable (a nested object)
            if (item.getClass().isAnnotationPresent(Serializable.class)) {
                // Recursive call for nested objects (e.g., JsonSchemaAssociation)
                sequence.add(serializeObject(item));
            } else {
                // Assume it's a primitive/string (e.g., List<String>)
                sequence.add(serialize(item));
            }
        }
        return sequence;
    }

    private YamlMapNode serializeMap(Map<?, ?> map) {
        YamlMapNode yamlMap = new YamlMapNode();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;

            YamlScalarNode keyNode = new YamlScalarNode(entry.getKey(), QuoteStyle.PLAIN);
            YamlNode valueNode = (entry.getValue() == null)
                ? new YamlScalarNode("")
                : serialize(entry.getValue());

            yamlMap.put(keyNode, valueNode);
        }
        return yamlMap;
    }

    //
    // Serialization Helpers
    //

    /// Retrieves all declared fields for a class and all its superclasses (excluding Object).
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        // Start with the current class and move up the hierarchy
        Class<?> currentClass = clazz;

        // Stop when we reach Object.class, as it has no serializable fields we care about
        while (currentClass != null && currentClass != Object.class) {
            // Add all fields declared in the current class (but not its superclasses)
            for (Field field : currentClass.getDeclaredFields()) {
                fields.add(field);
            }
            // Move up to the superclass for the next iteration
            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }

    private List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                methods.add(m);
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    //
    // Deserialization Methods
    //

    /// Converts a Yaml AST structure back into a Java object of the specified type.
    @SuppressWarnings("unchecked")
    private <T> T deserialize(YamlNode yaml, Class<T> clazz) throws YamlSerializerException {
        // If the YAML node is null, or it's a scalar representing a null value,
        // we return null immediately. This allows 'security: ' to map to a null Object.
        if (yaml == null || (yaml instanceof YamlScalarNode scalar && scalar.getPrimitive() == null)) {
            return null;
        }

        Set<String> claimedKeys = new HashSet<>();
        try {

            // 1. SHORTCUT: If the target is a standard Collection, bypass POJO logic
            if (Map.class.isAssignableFrom(clazz)) {
                if (yaml instanceof YamlMapNode mapNode) {
                    return (T) convertYamlMapToStandardMap(mapNode);
                }
                return (T) new LinkedHashMap<>();
            }

            if (List.class.isAssignableFrom(clazz)) {
                if (yaml instanceof YamlSequenceNode seqNode) {
                    return (T) convertYamlSequenceToStandardList(seqNode);
                }
                return (T) new ArrayList<>();
            }

            // 1. Validation
            if (!clazz.isAnnotationPresent(Serializable.class)) {
                throw new YamlSerializerException(yaml, YamlDiagnosticCode.CLASS_NOT_SERIALIZABLE, clazz.getSimpleName());
            }

            T instance = clazz.getConstructor().newInstance();

            // 2. We now check against the generic MapAstNode interface
            if (!(yaml instanceof YamlMapNode mapNode)) {
                throw new YamlSerializerException(yaml, YamlDiagnosticCode.EXPECTED_MAP_STRUCTURE, clazz.getSimpleName());
            }

            // 3. Process Declared Fields
            for (Field field : getAllFields(clazz)) {
                field.setAccessible(true);
                if (field.isAnnotationPresent(DataIgnore.class)) continue;

                // Determine the YAML key for this field
                String key = field.getName();
                if (field.isAnnotationPresent(DataField.class)) {
                    String annotatedKey = field.getAnnotation(DataField.class).key();
                    if (annotatedKey != null && !annotatedKey.isEmpty()) {
                        key = annotatedKey;
                    }
                }

                claimedKeys.add(key);

                // Use the new generic 'get' method
                YamlNode valueNode = mapNode.get(key);

                if (valueNode != null) {
                    // We pass field.getType() so it knows this is a List, a String, etc.
                    Object convertedValue = deserializeNode(valueNode, field, field.getType());
                    if (convertedValue != null) {
                        field.set(instance, convertedValue);
                    }
                }
            }

            // 4. Handle dynamic properties via @YamlAnySetter
            processAnySetter(instance, (YamlMapNode)yaml, claimedKeys, clazz);

            return instance;
        } catch (NoSuchMethodException e) {
            throw new YamlSerializerException(yaml, e, YamlDiagnosticCode.NO_SUCH_METHOD, clazz.getSimpleName());
        } catch (Exception e) {
            throw new YamlSerializerException(yaml, e, YamlDiagnosticCode.FAILED_DESERIALIZE, clazz.getSimpleName(), e.getMessage());
        }
    }

    /// Dispatches a node to the correct deserialization logic.
    /// @param node The AST node to convert.
    /// @param field The field being populated (can be null for nested elements).
    /// @param targetType The class type to convert to.
    /// Dispatches a node to the correct deserialization logic based on target type.
    private Object deserializeNode(YamlNode node, Field field, Class<?> targetType) throws Exception {
        if (node == null) return null;

        // 1. High Priority Symmetrical Check: Intercept custom YAML type serializers
        TypeDescriptor<?> typeDescriptor = getTypeDescriptor(targetType);
        if (typeDescriptor instanceof YamlTypeSerializer<?> typeSerializer) {
            return typeSerializer.deserialize(node);
        }

        // 2. Nested @Serializable objects
        if (targetType.isAnnotationPresent(Serializable.class)) {
            if (!(node instanceof YamlNode)) {
                 throw new YamlSerializerException(node, YamlDiagnosticCode.EXPECTED_YAML_NODE, targetType.getSimpleName());
            }
            return deserialize((YamlNode)node, targetType);
        }

        // 3. Collections
        if (List.class.isAssignableFrom(targetType)) {
            return deserializeList(node, field);
        }
        if (Map.class.isAssignableFrom(targetType)) {
            return deserializeMap(node, field);
        }

        // 4. Scalars (Primitives, Strings, Enums)
        if (node instanceof ScalarAstNode scalar) {

            // ScalarDescriptor
            if (typeDescriptor instanceof ScalarDescriptor descriptor) {
                Object val = scalar.getPrimitive();
                String stringValue = val != null ? val.toString() : "";
                try {
                    Object object = descriptor.toJvmType(stringValue);
                    return object;
                } catch (Exception e) {
                    throw new YamlSerializerException(node, e, YamlDiagnosticCode.FAILED_TO_MAP_TYPE, targetType.getSimpleName(), e.getMessage());
                }
            }

            return deserializeScalar(scalar, targetType);
        }

        if (targetType == Object.class) {
            if (node instanceof YamlMapNode mapNode) {
                return convertYamlMapToStandardMap(mapNode);
            }
            if (node instanceof YamlSequenceNode seqNode) {
                return convertYamlSequenceToStandardList(seqNode);
            }
            if (node instanceof ScalarAstNode scalar) {
                return scalar.getPrimitive();
            }
            return node;
        }

        // Likely cause of arriving here is that the target type either:
        //   - Doesn't have the @Serializable annotation
        //   - Is in a package that's not opened to cascara.lang.yaml
        //
        // Strictness: If we got here, the AST structure doesn't match the Java model
        throw new YamlSerializerException(node, YamlDiagnosticCode.INCOMPATIBLE_TYPES,
            node.getClass().getSimpleName(), targetType.getSimpleName()
        );
    }

    private List<?> deserializeList(YamlNode node, Field field) throws Exception {
        if (node == null) return new ArrayList<>();
        Class<?> itemType = ReflectionUtils.getGenericTypeOfListField(field);

        // Fallback for single values in YAML where a list was expected
        if (node instanceof YamlScalarNode scalar) {
            Object val = deserializeScalar(scalar, itemType);
            // FIf the value is null (like an empty key), return an empty mutable list
            if (val == null) return new ArrayList<>();

            // Otherwise, return a mutable list with the single item
            ArrayList<Object> singleList = new ArrayList<>();
            singleList.add(val);
            return singleList;
        }

        if (!(node instanceof YamlSequenceNode sequence)) {
            throw new YamlSerializerException(node, YamlDiagnosticCode.EXPECTED_SEQUENCE, field.getName());
        }

        List<Object> result = new ArrayList<>();
        for (YamlNode item : sequence.getChildren()) {
            Object val = deserializeNode(item, null, itemType);
            // YAML sequences can have null entries (- ), we should decide if we allow them.
            // Usually, for a list of strings/objects, we skip nulls or add them.
            result.add(val);
        }
        return result;
    }

    private Map<?, ?> deserializeMap(AstNode node, Field field) throws Exception {
        if (!(node instanceof YamlMapNode mapNode)) return new LinkedHashMap<>();

        Class<?> keyType = ReflectionUtils.getGenericTypeOfMapKey(field);
        Class<?> valType = ReflectionUtils.getGenericTypeOfMapValue(field);
        Map<Object, Object> result = new LinkedHashMap<>();

        for (YamlMapEntryNode entry : mapNode.getEntries()) {
            // Object primitiveKey = (entry.getKey() instanceof ScalarAstNode scalar)
            //         ? scalar.getPrimitive()
            //         : entry.getKey().toString();
            // Object key = deserializeScalar(primitiveKey, keyType);


            Object key;
            if (entry.getKey() instanceof YamlScalarNode scalarKey) {
                key = deserializeScalar(scalarKey, keyType);
            } else {
                throw new YamlSerializerException(node, GenericDiagnosticCode.ERROR, "Non-scalar key not implemented");
            }

            Object val = deserializeNode(entry.getValue(), field, valType);
            if (key != null) result.put(key, val != null ? val : ""); // TODO: Is "" okay here?
        }

        return result;
    }

    /// Converts a primitive value (already inferred by the AST) or a raw string into the target Java type.
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object deserializeScalar(ScalarAstNode scalar, Class<?> targetType) throws YamlSerializerException {
        Object primitive = scalar.getPrimitive();
        if (primitive == null) return null;

        // 1. Exact Match / Wrapper Match
        if (targetType.isInstance(primitive) ||
           (targetType.isPrimitive() && getWrapperClass(targetType).isInstance(primitive))) {
            return primitive;
        }

        // TODO: Might want to do this after type descriptor check

        // 2. Numeric Narrowing (If AST already inferred a Number but target is different)
        if (primitive instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class || targetType == Float.class) return num.floatValue();
            if (targetType == byte.class || targetType == Byte.class) return num.byteValue();
            if (targetType == short.class || targetType == Short.class) return num.shortValue();
        }

        // 3. ScalarDescriptor
        String text = primitive.toString().trim();
        TypeDescriptor typeDescriptor = getTypeDescriptor(targetType);
        if (typeDescriptor instanceof ScalarDescriptor descriptor) {
            try {
                return descriptor.toJvmType(text);
            } catch (Exception e) {
                throw new YamlSerializerException(scalar, e, YamlDiagnosticCode.FAILED_DESERIALIZE_SCALAR, primitive.getClass(), e.getMessage());
            }
        }

        // 4. String-Based Parsing (Fallback for quoted values or string-only types)
        if (targetType == String.class) return text;

        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, text);
        }

        Primitive yamlPrimitive = Primitive.of(primitive);
        yamlPrimitive.setDelegate(YAML_PRIMITIVE_DELEGATE);
        if (targetType == boolean.class || targetType == Boolean.class) {
            return yamlPrimitive.asBoolean(false);
        } else if (targetType == int.class || targetType == Integer.class) {
            return yamlPrimitive.asInteger(0);
        } else if (targetType == long.class || targetType == Long.class) {
            return (long) yamlPrimitive.asInteger(0); // TODO: add asLong to YamlPrimitive
        } else if (targetType == float.class || targetType == Float.class) {
            return (float) yamlPrimitive.asDouble(0); // TODO: add asFloat to YamlPrimitive
        } else if (targetType == double.class || targetType == Double.class) {
            return yamlPrimitive.asDouble(0);
        }

        // TODO:
        // We reach here through non-JPSM tests run through Gradle.
        // Proper solution is black box testing, make the tests their own module.
        // Quick fix might be to let the caller tell the serializer what type descriptors to use.

        throw new YamlSerializerException(scalar, YamlDiagnosticCode.UNSUPPORTED_TYPE, targetType.getSimpleName());
    }

    //
    // Deserialization Helpers
    //

    private Map<String, Object> convertYamlMapToStandardMap(YamlMapNode mapNode) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (YamlMapEntryNode entry : mapNode.getEntries()) {
            // Convert key (usually a scalar) to String
            String key = entry.getKey().toString();
            // Recursively convert the value
            Object value = deserializeNode(entry.getValue(), null, Object.class);
            result.put(key, value);
        }
        return result;
    }

    private List<Object> convertYamlSequenceToStandardList(YamlSequenceNode seqNode) throws Exception {
        List<Object> result = new ArrayList<>();
        for (YamlNode child : seqNode.getChildren()) {
            // Recursively convert each item in the list
            result.add(deserializeNode(child, null, Object.class));
        }
        return result;
    }

    private Class<?> getWrapperClass(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        return primitive;
    }

    private void processAnySetter(Object instance, YamlMapNode rootMap, Set<String> claimedKeys, Class<?> clazz) throws Exception {
        for (Method method : getAllMethods(clazz)) {
            if (method.isAnnotationPresent(AnySetter.class)) {
                method.setAccessible(true);
                for (YamlMapEntryNode entry : rootMap.getEntries()) {
                    // entry.getKey() returns a YamlNode.
                    // We use toString() because our YamlScalarNode override returns stringValue.
                    String key = entry.getKey().toString();

                    if (!claimedKeys.contains(key)) {
                        Object value;
                        YamlNode valueNode = entry.getValue();
                        if (valueNode instanceof YamlScalarNode scalar) {
                            value = scalar.getPrimitive();
                        } else {
                            // If it's a complex object (Map/List), for now we pass the AST node
                            // or we'd need a recursive "astToMap" helper.
                            value = valueNode;
                        }
                        method.invoke(instance, key, value);
                    }
                }
            }
        }
    }

    //
    // Shared Helpers
    //

    private boolean isPrimitive(Object thing) {
        return (thing instanceof String ||
            thing instanceof Number ||
            thing instanceof Byte ||
            thing instanceof Character ||
            thing instanceof Boolean);
    }

    private TypeDescriptor<?> getTypeDescriptor(Class<?> clazz) {
        // 1. First check if one has been registered locally
        TypeDescriptor<?> descriptor = typeDescriptors.get(clazz);
        if (descriptor != null) {
            return descriptor;
        }

        // 2. Use service provider layer to get one
        ServiceProviderLayer rootLayer = ServiceProviderLayer.getRootLayer();
        List<ServiceMetadata> typeDescriptors = rootLayer.findAllProviders(
            TypeDescriptor.class,
            capabilities -> {
                String registeredTypeName = capabilities.getString("javaType");
                if (registeredTypeName == null) return false;
                try {
                    // Check if the runtime object's class can be assigned to the descriptor's target type
                    Class<?> registeredType = Class.forName(registeredTypeName);
                    return registeredType.isAssignableFrom(clazz);
                } catch (ClassNotFoundException e) {
                    return false;
                }
            }
        );
        if (!typeDescriptors.isEmpty()) {
            ServiceMetadata metadata = typeDescriptors.getFirst();
            return ServiceProviderLayer.loadProvider(ScalarDescriptor.class, metadata);
        }
        return null;
    }
}