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

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.lang.yaml.ast.YamlAnchor;
import io.github.qishr.cascara.lang.yaml.ast.YamlMapEntry;
import io.github.qishr.cascara.lang.yaml.ast.YamlMap;
import io.github.qishr.cascara.lang.yaml.ast.YamlNode;
import io.github.qishr.cascara.lang.yaml.ast.YamlSequence;

public class YamlNormalizer {
    // private Reporter reporter = new NoOpReporter();

    public YamlNormalizer() {

    }

    public YamlNormalizer setReporter(Reporter reporter) {
        // this.reporter = reporter;
        return this;
    }

    public YamlNode normalize(YamlNode node) {
        if (node == null) return null;

        // Unwrap structural anchor nodes
        if (node instanceof YamlAnchor anchor) {
            return normalize(anchor.getInnerNode());
        }

        // Normalize maps recursively
        if (node instanceof YamlMap map) {
            YamlMap newMap = new YamlMap(map.getToken(), map.getOptions());
            newMap.setAnchor(map.getAnchor());
            newMap.setTag(map.getTag());
            newMap.setResolvedTag(map.getResolvedTag());
            for (YamlMapEntry entry : map.getEntries()) {
                YamlNode key = normalize(entry.getKey());
                YamlNode value = normalize(entry.getValue());
                newMap.put(new YamlMapEntry(key, value));
            }
            return newMap;
        }

        // Normalize sequences recursively
        if (node instanceof YamlSequence seq) {
            YamlSequence newSeq = new YamlSequence(seq.getToken());
            newSeq.setAnchor(seq.getAnchor());
            newSeq.setTag(seq.getTag());
            newSeq.setResolvedTag(seq.getResolvedTag());
            for (YamlNode child : seq.getChildren()) {
                newSeq.add(normalize(child));
            }
            return newSeq;
        }

        // Scalars are already in normalized form
        return node;
    }
}