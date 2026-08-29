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


package io.github.qishr.cascara.lang.yaml.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import io.github.qishr.cascara.common.lang.type.TypeReference;
import io.github.qishr.cascara.common.semver.SemVer;
import io.github.qishr.cascara.common.util.JarManifest;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlStream;
import io.github.qishr.cascara.lang.yaml.processor.YamlNormalizer;
import io.github.qishr.cascara.lang.yaml.processor.YamlSerializer;

public final class CascaraYaml {

    private static final YamlNormalizer normalizer = new YamlNormalizer();
    private static final YamlAliasResolver resolver = new YamlAliasResolver();

    /// Utility class — not instantiable.
    private CascaraYaml() {}

    /// Return the version of Cascara YAML.
    public static SemVer getVersion() {
        return JarManifest.of(CascaraYaml.class).getVersion();
    }

    //
    // Instantiation
    //

    /// Create a new YAML serializer instance.
    public static YamlSerializer newSerializer() {
        return new YamlSerializer();
    }

    /// Create a new YAML normalizer instance.
    public static YamlNormalizer newNormalizer() {
        return new YamlNormalizer();
    }

    /// Create a new YAML alias resolver instance.
    public static YamlAliasResolver newResolver() {
        return new YamlAliasResolver();
    }

    //
    // READ: String
    //

    /// Read YAML text into a JVM object of the given type.
    public static <T> T read(String text, Class<T> type) {
        return newSerializer().fromString(text, type);
    }

    /// Read YAML text using a generic type reference.
    public static <T> T read(String text, TypeReference<T> type) {
        return newSerializer().fromText(text, type);
    }

    //
    // READ: Reader
    //

    /// Read YAML from a Reader into a JVM object.
    public static <T> T read(Reader reader, Class<T> type) {
        return newSerializer().fromReader(reader, type);
    }

    /// Read JSON from a Reader using a generic type reference.
    public static <T> T read(Reader reader, TypeReference<T> type) {
        return newSerializer().fromReader(reader, type);
    }

    //
    // READ: InputStream
    //

    /// Read YAML from an InputStream into a JVM object.
    public static <T> T read(InputStream is, Class<T> type) {
        return newSerializer().fromStream(is, type);
    }

    /// Read YAML from an InputStream using a generic type reference.
    public static <T> T read(InputStream is, TypeReference<T> type) {
        return newSerializer().fromStream(is, type);
    }

    //
    // WRITE
    //

    /// Write a JVM object to YAML text.
    public static String write(Object value) {
        return newSerializer().toString(value);
    }

    /// Write a JVM object to a Writer as YAML.
    public static void write(Object value, Writer writer) throws IOException {
        writer.write(newSerializer().toString(value));
    }

    //
    // AST-level access
    //

    /// Convert a JVM object into a YAML AST node.
    public static YamlNode toAst(Object value) {
        return newSerializer().toAst(value);
    }

    /// Convert a YAML AST node into a JVM object.
    public static <T> T fromAst(YamlNode ast, Class<T> type) {
        return newSerializer().fromAst(ast, type);
    }

    /// Convert a YAML AST node using a generic type reference.
    public static <T> T fromAst(YamlNode ast, TypeReference<T> type) {
        return newSerializer().fromAst(ast, type);
    }

    //
    // Normalization and Resolution
    //

    public static YamlNode normalize(YamlNode node) {
        return normalizer.normalize(node);
    }

    public static YamlNode resolve(YamlNode node) {
        return resolver.resolve(node);
    }

    public static YamlStream resolve(YamlStream node) {
        return resolver.resolve(node);
    }
}