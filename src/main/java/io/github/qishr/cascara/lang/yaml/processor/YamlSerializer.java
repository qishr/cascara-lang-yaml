package io.github.qishr.cascara.lang.yaml.processor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.common.lang.LanguageOptions;
import io.github.qishr.cascara.common.lang.annotation.AnyGetter;
import io.github.qishr.cascara.common.lang.annotation.AnySetter;
import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.annotation.Serializable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.processor.Serializer;
import io.github.qishr.cascara.common.util.ReflectionUtils;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.exception.YamlSerializerException;
import io.github.qishr.cascara.lang.yaml.YamlDocument;
import io.github.qishr.cascara.lang.yaml.YamlOptions;

/// Standard implementation for YAML serialization.
public class YamlSerializer implements Serializer<YamlNode> {
    private final YamlParser parser = new YamlParser();
    private YamlOptions options = new YamlOptions();

    @Override
    public YamlSerializer setReporter(Reporter reporter) {
        this.parser.setReporter(reporter);
        return this;
    }

    @Override
    public YamlSerializer setOptions(LanguageOptions<?> options) {
        if (options instanceof YamlOptions yamlOptions) {
            this.options = yamlOptions;
            this.parser.setOptions(yamlOptions);
        }
        return this;
    }

    //
    // Serializer Implementation
    //

    @Override
    public String toText(Object object) throws YamlSerializerException {
        // Step 1: Object -> AST
        YamlNode ast = toAst(object);
        // Step 2: AST -> String
        return new YamlEmitter().setOptions(options).emit(ast);
    }

    @Override
    public <C> C fromText(String text, Class<C> clazz) throws YamlSerializerException {
        // Step 1: String -> AST
        YamlNode ast = parser.parse(text);
        // Step 2: AST -> Object
        return fromAst(ast, clazz);
    }

    @Override
    public YamlNode toAst(Object object) {
        try {
            // checkIfSerializable(object);
            // initializeObject(object);
            // return getYamlRootMap(object);
            return createValueNode(object);
        } catch (Exception e) {
            String message = String.format(
                "Failed to map object to YAML AST: %s",
                e.getMessage()
            );
            throw new YamlSerializerException(message, e);
        }
    }

    @Override
    public <C> C fromAst(YamlNode astNode, Class<C> clazz) {
        try {
            // UNWRAP: If it's a document, get the actual content
            YamlNode actualNode = astNode;
            if (astNode instanceof YamlDocument doc) {
                actualNode = doc.getRoot();
            }

            return (C) performMapping(actualNode, clazz);
        } catch (Exception e) {
            String message = String.format(
                "Failed to map YAML AST to %s: %s",
                clazz.getSimpleName(),
                e.getMessage()
            );
            throw new YamlSerializerException(message, e);
        }
    }

    //
    // Internal Reflection Logic
    //

    /// Converts a Yaml AST structure back into a Java object of the specified type.
    @SuppressWarnings("unchecked")
    public <T> T performMapping(YamlNode yaml, Class<T> clazz) throws YamlSerializerException {
        // If the YAML node is null, or it's a scalar representing a null value,
        // we return null immediately. This allows 'security: ' to map to a null Object.
        if (yaml == null || (yaml instanceof YamlScalarNode scalar && scalar.getValue() == null)) {
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
                throw new YamlSerializerException("Class " + clazz.getSimpleName() + " is not @Serializable");
            }

            T instance = clazz.getConstructor().newInstance();

            // 2. We now check against the generic MapAstNode interface
            if (!(yaml instanceof YamlMapNode mapNode)) {
                throw new YamlSerializerException("Expected a map structure for class " + clazz.getSimpleName());
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
        } catch (YamlSerializerException e) {
            throw e;
        } catch (NoSuchMethodException e) {
            throw new YamlSerializerException("Failed to deserialize " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new YamlSerializerException("Failed to deserialize " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private YamlMapNode getYamlRootMap(Object object) throws Exception {
        Class<?> clazz = object.getClass();
        // New: YamlDocument now requires a root node (usually a Map)
        YamlMapNode rootMap = new YamlMapNode();

        for (Field field : getAllFields(clazz)) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(DataIgnore.class)) continue;

            if (field.isAnnotationPresent(AnySetter.class)) {
                Map<?, ?> map = (Map<?, ?>) field.get(object);
                if (map != null) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        YamlNode keyNode = createValueNode(entry.getKey());
                        YamlNode valueNode = createValueNode(entry.getValue());
                        rootMap.put(keyNode, valueNode);
                    }
                }
                continue;
            }

            Object value = field.get(object);
            if (value != null) {
                String keyName = field.isAnnotationPresent(DataField.class)
                    ? field.getAnnotation(DataField.class).key() : field.getName();
                if (keyName == null || keyName.isEmpty()) keyName = field.getName();

                YamlScalarNode keyNode = createValueScalar(keyName, QuoteStyle.PLAIN);
                YamlNode valueNode = createValueNode(value);
                rootMap.put(keyNode, valueNode);
            }
        }

        // 2. Process dynamic settings (@YamlAnyGetter)
        for (Method method : getAllMethods(clazz)) { //.getDeclaredMethods()) {
            if (method.isAnnotationPresent(AnyGetter.class)) {
                method.setAccessible(true);

                // Invoke the method to get the Map
                Object result = method.invoke(object);

                if (result instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        YamlNode keyNode = createValueNode(entry.getKey());
                        YamlNode valueNode = createValueNode(entry.getValue());
                        rootMap.put(keyNode, valueNode);
                    }
                }
            }
        }

        return rootMap;
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

    /// Creates the appropriate YamlNode (Scalar, Sequence, or Map) based on the Java value type.
    private YamlNode createValueNode(Object value) throws Exception {
        if (value instanceof List<?> list) {
            return serializeList(list);
        }

        if (value instanceof java.util.Map<?, ?> map) {
            return serializeMap(map);
        }

        if (value instanceof Path path) {
            // Path is serialized as a double quoted String of its absolute path
            return new YamlScalarNode(path.toAbsolutePath().toString(), QuoteStyle.DOUBLE);
        }

        if (value instanceof URI uri) {
            return new YamlScalarNode(uri.toString(), QuoteStyle.DOUBLE);
        }

        // heck if this is a nested serializable object
        if (value.getClass().isAnnotationPresent(Serializable.class)) {
            // Turn this object into a nested YAML Mapping
            return getYamlRootMap(value);
        }

        // Default to the existing scalar creation logic for primitives/strings
        return createValueScalar(value);
    }

    /// Creates a YamlScalar node with a specific, forced style.
    private YamlScalarNode createValueScalar(Object value, QuoteStyle quoteStyle) {
        if (value == null) return new YamlScalarNode("", QuoteStyle.PLAIN);
        return new YamlScalarNode(value.toString(), quoteStyle);
    }

    /// Creates a YamlScalar node with an inferred style (the original logic).
    private YamlScalarNode createValueScalar(Object value) {
        if (value == null) return new YamlScalarNode("", QuoteStyle.PLAIN);

        // Default to DOUBLE for Strings/Paths to be safe, PLAIN for numbers/booleans
        QuoteStyle style = (value instanceof String || value instanceof Path || value instanceof URI)
            ? QuoteStyle.DOUBLE
            : QuoteStyle.PLAIN;

        return createValueScalar(value, style);
    }

    /// Serializes a List into a YamlSequence.
    private YamlSequenceNode serializeList(List<?> list) throws Exception {
        YamlSequenceNode sequence = new YamlSequenceNode();
        for (Object item : list) {
            if (item == null) continue;

            // Check if the item is itself serializable (a nested object)
            if (item.getClass().isAnnotationPresent(Serializable.class)) {
                // Recursive call for nested objects (e.g., JsonSchemaAssociation)
                sequence.add(getYamlRootMap(item));
            } else {
                // Assume it's a primitive/string (e.g., List<String>)
                sequence.add(createValueNode(item));
            }
        }
        return sequence;
    }

    private YamlMapNode serializeMap(java.util.Map<?, ?> map) throws Exception {
        YamlMapNode yamlMap = new YamlMapNode();
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;

            YamlScalarNode keyNode = new YamlScalarNode(entry.getKey().toString(), QuoteStyle.PLAIN);
            YamlNode valueNode = (entry.getValue() == null)
                ? new YamlScalarNode("", QuoteStyle.PLAIN)
                : createValueNode(entry.getValue());

            yamlMap.put(keyNode, valueNode);
        }
        return yamlMap;
    }

    //
    // Deserialization Helpers
    //

    /// Dispatches a node to the correct deserialization logic.
    /// @param node The AST node to convert.
    /// @param field The field being populated (can be null for nested elements).
    /// @param targetType The class type to convert to.
    /// Dispatches a node to the correct deserialization logic based on target type.
    private Object deserializeNode(YamlNode node, Field field, Class<?> targetType) throws Exception {
        if (node == null) return null;

        // 1. Path Handling (Preserved)
        if (targetType == Path.class) {
            return deserializePath(node);
        }
        if (targetType == URI.class) {
            return deserializeUri(node);
        }

        // 2. Nested @Serializable objects
        if (targetType.isAnnotationPresent(Serializable.class)) {
            if (!(node instanceof YamlNode)) {
                 throw new YamlSerializerException("Expected YamlNode for serializable type: " + targetType.getSimpleName());
            }
            return performMapping((YamlNode)node, targetType);
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
            return deserializeScalar(scalar.getPrimitiveValue(), targetType);
        }

        if (targetType == Object.class) {
            if (node instanceof YamlMapNode mapNode) {
                return convertYamlMapToStandardMap(mapNode);
            }
            if (node instanceof YamlSequenceNode seqNode) {
                return convertYamlSequenceToStandardList(seqNode);
            }
            if (node instanceof ScalarAstNode scalar) {
                return scalar.getPrimitiveValue();
            }
            return node;
        }

        // Likely cause of arriving here is that the target type either:
        //   - Doesn't have the @Serializable annotation
        //   - Is in a package that's not opened to cascara.lang.yaml
        //
        // 5. Strictness: If we got here, the AST structure doesn't match the Java model
        throw new YamlSerializerException(
            String.format("Incompatible types: Cannot map %s to Java type %s",
                node.getClass().getSimpleName(), targetType.getSimpleName())
        );
    }

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

    /// Converts a primitive value (already inferred by the AST) or a raw string into the target Java type.
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object deserializeScalar(Object primitive, Class<?> targetType) throws YamlSerializerException {
        if (primitive == null) return null;

        // 1. Exact Match / Wrapper Match
        if (targetType.isInstance(primitive) ||
           (targetType.isPrimitive() && getWrapperClass(targetType).isInstance(primitive))) {
            return primitive;
        }

        // 2. Numeric Narrowing (If AST already inferred a Number but target is different)
        if (primitive instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class || targetType == Float.class) return num.floatValue();
            if (targetType == byte.class || targetType == Byte.class) return num.byteValue();
            if (targetType == short.class || targetType == Short.class) return num.shortValue();
        }

        // 3. String-Based Parsing (Fallback for quoted values or string-only types)
        String rawValue = primitive.toString().trim();

        if (targetType == String.class) return rawValue;
        if (targetType == Path.class) return Path.of(rawValue);
        if (targetType == URI.class) return URI.create(rawValue);
        if (targetType == UUID.class) return UUID.fromString(rawValue);

        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, rawValue);
        }

        try {
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(rawValue);
            }

            // Robust Number Parsing (handling scientific notation and float-to-long)
            if (targetType == Integer.class || targetType == int.class) {
                return (int) Double.parseDouble(rawValue); // Double parse handles "1.0" -> 1
            }

            if (targetType == Long.class || targetType == long.class) {
                if (rawValue.contains(".") || rawValue.toLowerCase().contains("e")) {
                    return (long) Double.parseDouble(rawValue);
                }
                return Long.parseLong(rawValue);
            }

            if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(rawValue);
            }

            if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(rawValue);
            }
        } catch (NumberFormatException e) {
            throw new YamlSerializerException(
                String.format("Value '%s' cannot be converted to %s", rawValue, targetType.getSimpleName())
            );
        }

        throw new YamlSerializerException("Unsupported field type: " + targetType.getSimpleName());
    }

    /// Specifically handles Path deserialization.
    private Path deserializePath(AstNode node) throws YamlSerializerException {
        if (node instanceof ScalarAstNode scalar) {
            Object val = scalar.getPrimitiveValue();
            return Path.of(val != null ? val.toString() : "");
        }
        throw new YamlSerializerException("Expected a scalar for Path field, but found " + node.getClass().getSimpleName());
    }

    private URI deserializeUri(AstNode node) throws YamlSerializerException {
        if (node instanceof ScalarAstNode scalar) {
            Object val = scalar.getPrimitiveValue();
            return URI.create(val != null ? val.toString() : "");
        }
        throw new YamlSerializerException("Expected a scalar for URI field, but found " + node.getClass().getSimpleName());
    }

    private List<?> deserializeList(YamlNode node, Field field) throws Exception {
        if (node == null) return new ArrayList<>();
        Class<?> itemType = ReflectionUtils.getGenericTypeOfListField(field);

        // Fallback for single values in YAML where a list was expected
        if (node instanceof YamlScalarNode scalar) {
            Object val = deserializeScalar(scalar.getPrimitiveValue(), itemType);
            // FIX: If the value is null (like an empty key), return an empty mutable list
            if (val == null) return new ArrayList<>();

            // Otherwise, return a mutable list with the single item
            ArrayList<Object> singleList = new ArrayList<>();
            singleList.add(val);
            return singleList;
        }

        if (!(node instanceof YamlSequenceNode sequence)) {
            throw new YamlSerializerException("Expected a sequence for field: " + field.getName());
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
            Object key = convertScalarToType(entry.getKey().toString(), keyType);
            Object val = deserializeNode(entry.getValue(), field, valType);
            if (key != null) result.put(key, val != null ? val : "");
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
                            value = inferValueType(scalar.getString());
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

    /// Converts a raw scalar string from the YAML AST into the target Java field type.
    /// Supports String, Boolean, and common Number types.
    private Object convertScalarToType(String rawValue, Class<?> targetType) throws YamlSerializerException {

        if (rawValue == null) {
            // If the target is a String or Object, return empty string or null
            if (targetType == String.class || targetType == Object.class) return "";
            // For primitives/Numbers, we can't return null, so we return a default or throw
            if (targetType.isPrimitive()) {
                if (targetType == boolean.class) return false;
                return 0;
            }
            return null;
        }

        // Trim input just in case of whitespace issues from the parser
        String processedValue = rawValue.trim();

        if (targetType == Object.class || targetType == String.class) {
            return inferValueType(processedValue);
        }

        // Specific Type: String
        if (targetType == String.class) {
            return processedValue;
        }

        try {
            // Handle Boolean
            if (targetType == Boolean.class || targetType == boolean.class) {
                // Allows "true", "True", "TRUE", etc.
                return Boolean.parseBoolean(rawValue);
            }

            // Handle Number Types
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(rawValue);
            } else if (targetType == Long.class || targetType == long.class) {

                // CRITICAL FIX FOR LARGE TIMESTAMPS/INTEGERS PARSED AS FLOATS

                // 1. Check if the value contains floating point notation ('.', 'e', or 'E')
                if (processedValue.contains(".") || processedValue.contains("e") || processedValue.contains("E")) {
                    try {
                        // Attempt to parse it as a Double first (which handles scientific notation)
                        Double d = Double.parseDouble(processedValue);

                        // 2. Safely convert the Double to a Long, truncating the fractional part.
                        //    This is safe for YAML-parsed large integers.
                        return d.longValue();
                    } catch (NumberFormatException e) {
                        // If parsing as Double fails, fall through and try as a standard Long later
                        // This handles cases where it's a non-numeric string, like a boolean disguised as a float.
                    }
                }

                // 3. If no float notation was found, or if the Double parsing failed,
                //    attempt standard Long parsing (e.g., "1765538348553" or "1.5E12" that failed to be a double).
                return Long.parseLong(processedValue);

            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(rawValue);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(rawValue);
            }
        } catch (NumberFormatException e) {
             throw new YamlSerializerException(
                 String.format("YAML value '%s' could not be converted to target numeric type %s.",
                               rawValue, targetType.getSimpleName()), e
             );
        }

        // Fallback for unsupported types
        throw new YamlSerializerException(
            String.format("Unsupported field type '%s' during deserialization.", targetType.getSimpleName())
        );
    }

    private Object inferValueType(String value) {
        if (value == null) return "";

        // Explicit Boolean Check
        if (value.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (value.equalsIgnoreCase("false")) return Boolean.FALSE;

        // Try Number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            // Fallback to String
            return value;
        }
    }
}