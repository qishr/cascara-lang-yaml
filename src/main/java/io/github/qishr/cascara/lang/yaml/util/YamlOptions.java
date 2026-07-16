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

import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.util.Duplicable;

public class YamlOptions extends LanguageOptions<YamlOptions> implements Duplicable<YamlOptions> {
    public static final YamlOptions DEFAULT = new YamlOptions();

    private boolean allowUnicode = true;
    private boolean explicitStart = false; // Writes '---' if true
    private boolean expandedStyle = false;
    private boolean strict = false;
    private boolean includeComments = false;
    private boolean multiDocument = false;

    /// Sets whether unicode characters are allowed in scalars.
    public YamlOptions setAllowUnicode(boolean val) {
        this.allowUnicode = val;
        return this;
    }

    /// Sets whether to always output the '---' document start marker.
    public YamlOptions setExplicitStart(boolean val) {
        this.explicitStart = val;
        return this;
    }

    public YamlOptions setExpandedStyle(boolean val) {
        this.expandedStyle = val;
        return this;
    }

    public YamlOptions setStrict(boolean val) {
        this.strict = val;
        return this;
    }

    public YamlOptions setIncludeComments(boolean val) {
        this.includeComments = val;
        return this;
    }

    public YamlOptions setMultiDocument(boolean val) {
        this.multiDocument = val;
        return this;
    }

    public boolean isAllowUnicode() { return allowUnicode; }
    public boolean isExplicitStart() { return explicitStart; }
    public boolean isExpandedStyle() { return expandedStyle; }
    public boolean isStrict() { return strict; }
    public boolean isIncludeComments() { return includeComments; }
    public boolean isMultiDocument() { return multiDocument; }

    @Override
    public YamlOptions duplicate() {
        return new YamlOptions()
            .setAllowUnicode(allowUnicode)
            .setExpandedStyle(expandedStyle)
            .setExplicitStart(explicitStart)
            .setIncludeComments(includeComments)
            .setIndentSize(indentSize)
            .setMultiDocument(multiDocument)
            .setStrict(strict);
    }
}