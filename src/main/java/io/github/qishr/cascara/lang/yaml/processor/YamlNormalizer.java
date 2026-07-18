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

import io.github.qishr.cascara.lang.yaml.ast.YamlAliasNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchorNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntryNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequenceNode;

public class YamlNormalizer {
    public static YamlNode normalize(YamlNode node) {

        // unwrap anchors
        if (node instanceof YamlAnchorNode anchor) {
            return normalize(anchor.getInnerNode());
        }

        // resolve aliases
        if (node instanceof YamlAliasNode alias) {
            return normalize(alias.getResolvedNode());
        }

        // normalize maps
        if (node instanceof YamlMapNode map) {
            YamlMapNode newMap = new YamlMapNode(map.getStartLine(), map.getStartColumn());
            for (YamlMapEntryNode entry : map.getEntries()) {
                YamlNode key = normalize(entry.getKey());
                YamlNode value = normalize(entry.getValue());
                newMap.put(new YamlMapEntryNode(
                    key.getStartLine(),
                    key.getStartColumn(),
                    key,
                    value
                ));
            }
            return newMap;
        }

        // normalize sequences
        if (node instanceof YamlSequenceNode seq) {
            YamlSequenceNode newSeq = new YamlSequenceNode(seq.getStartLine(), seq.getStartColumn());
            for (YamlNode child : seq.getChildren()) {
                newSeq.add(normalize(child));
            }
            return newSeq;
        }

        // scalars are already normalized
        return node;
    }
}
