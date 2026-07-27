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

import io.github.qishr.cascara.common.lang.ast.AstNodeFactory;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.lang.util.QuoteStyle;
import io.github.qishr.cascara.lang.yaml.ast.ScalarStyle;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlScalarNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public class YamlNodeFactory implements AstNodeFactory<YamlNode,YamlScalarNode,YamlSequenceNode,YamlMapNode,YamlMapEntryNode,YamlNode> {

    @Override
    public YamlScalarNode createScalarNode(Object jvmValue) {
        return new YamlScalarNode(jvmValue);
    }

    @Override
    public YamlScalarNode createScalarNode(Object key, QuoteStyle quoteStyle) {
        return new YamlScalarNode(key, scalarStyle(quoteStyle));
    }

	@Override
	public YamlScalarNode createScalarNode(Object jvmValue, QuoteStyle quoteStyle, LanguageOptions<?> options) {
        return new YamlScalarNode(jvmValue, scalarStyle(quoteStyle), (YamlOptions)options);
	}

    @Override
    public YamlScalarNode createKey(Object key) {
        return new YamlScalarNode(key);
    }

    @Override
    public YamlSequenceNode createSequenceNode() {
        return new YamlSequenceNode();
    }

    @Override
    public YamlMapNode createMapNode() {
        return new YamlMapNode();
    }

    private ScalarStyle scalarStyle(QuoteStyle quoteStyle) {
        return switch (quoteStyle) {
            case DOUBLE -> ScalarStyle.DOUBLE_QUOTED;
            case SINGLE -> ScalarStyle.SINGLE_QUOTED;
            case PLAIN -> ScalarStyle.PLAIN;
            case UNDETERMINED -> ScalarStyle.UNDETERMINED;
        };
    }
}
