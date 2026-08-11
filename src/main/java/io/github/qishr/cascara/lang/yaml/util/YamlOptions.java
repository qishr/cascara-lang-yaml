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

import io.github.qishr.cascara.common.diagnostic.LocalizableRuntimeException;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.util.Duplicable;

public class YamlOptions extends LanguageOptions<YamlOptions> implements Duplicable<YamlOptions> {
    public static final YamlOptions DEFAULT = new YamlOptions();

    public static final YamlOptions CANONICAL = new ImmutableYamlOptions(
        new YamlOptions()
            .setStripComments(true)
            .setStripTags(true)
            .setStripAnchors(false) // YTS expects anchors to remain
            .setSortKeys(false) // YTS expects maps to be in their original order
            .setNormalizeIndent(true)
            .setNormalizeScalarFormatting(true)
            .setIndentSize(2)
            .setExplicitStart(false) // YTS expects no --- if there doesn't need to be one
            .setForceExplicitNull(true)
            .setForceBlockCollections(true)
    );

    private int depthLimit = 500;

    private boolean allowUnicode = true;
    private boolean explicitStart = false; // Writes '---' if true
    private boolean expandedStyle = false;
    private boolean strict = false;
    private boolean includeComments = false;
    private boolean multiDocument = false;

    private boolean sortKeys = false;
    private boolean forceQuotes = false; // TODO: Not used
    private boolean stripComments = false;
    private boolean stripTags = false;
    private boolean stripAnchors = false;
    private boolean normalizeIndent = false;
    private boolean normalizeScalarFormatting = false;

    private boolean forceExplicitNull = false;
    private boolean forceBlockCollections = false;

    public YamlOptions() {}

    public YamlOptions(YamlOptions original) {
        depthLimit = original.depthLimit;

        allowUnicode = original.allowUnicode;
        explicitStart = original.explicitStart; // Writes '---' if true
        expandedStyle = original.expandedStyle;
        strict = original.strict;
        includeComments = original.includeComments;
        multiDocument = original.multiDocument;

        sortKeys = original.sortKeys;
        forceQuotes = original.forceQuotes; // TODO: Not used
        stripComments = original.stripComments;
        stripTags = original.stripTags;
        stripAnchors = original.stripAnchors;

        normalizeIndent = original.normalizeIndent;
        normalizeScalarFormatting = original.normalizeScalarFormatting;

        forceExplicitNull = original.forceExplicitNull;
        forceBlockCollections = original.forceBlockCollections;
    }

    public int getDepthLimit() {return depthLimit; }
    public boolean isAllowUnicode() { return allowUnicode; }
    public boolean isExplicitStart() { return explicitStart; }
    public boolean isExpandedStyle() { return expandedStyle; }
    public boolean isStrict() { return strict; }
    public boolean isIncludeComments() { return includeComments; }
    public boolean isMultiDocument() { return multiDocument; }

    public boolean stripComments() { return stripComments; }
    public boolean stripTags() { return stripTags; }
    public boolean stripAnchors() { return stripAnchors; }
    public boolean sortKeys() { return sortKeys; }
    public boolean normalizeIndent() { return normalizeIndent; }
    public boolean normalizeScalarFormatting() { return normalizeScalarFormatting; }

    public boolean forceExplicitNull() { return forceExplicitNull; }
    public boolean forceBlockCollections() { return forceBlockCollections; }

    public YamlOptions setDepthLimit(int val) {
        this.depthLimit = val;
        return this;
    }

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

    public YamlOptions setStripComments(boolean val) {
        this.stripComments = val;
        return this;
    }

    public YamlOptions setStripTags(boolean val) {
        this.stripTags = val;
        return this;
    }

    public YamlOptions setStripAnchors(boolean val) {
        this.stripAnchors = val;
        return this;
    }

    public YamlOptions setSortKeys(boolean val) {
        this.sortKeys = val;
        return this;
    }

    public YamlOptions setNormalizeIndent(boolean val) {
        // TODO: Not implemented
        return this;
    }

    public YamlOptions setNormalizeScalarFormatting(boolean val) {
        // TODO: Not implemented
        return this;
    }

    public YamlOptions setForceExplicitNull(boolean b) {
        forceExplicitNull = b;
        return this;
    }

    public YamlOptions setForceBlockCollections(boolean b) {
        forceBlockCollections = b;
        return this;
    }

    @Override
    public YamlOptions duplicate() {
        return new YamlOptions(this);
    }


    public static class ImmutableYamlOptions extends YamlOptions {
        public ImmutableYamlOptions(YamlOptions options) {
            super(options);
        }

        public YamlOptions setAllowComments(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowComments");
        }

        public YamlOptions setAllowUnicode(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setAllowUnicode");
        }

        public YamlOptions setExplicitStart(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setExplicitStart");
        }

        public YamlOptions setExpandedStyle(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setExpandedStyle");
        }

        public YamlOptions setStrict(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setStrict");
        }

        public YamlOptions setIncludeComments(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setIncludeComments");
        }

        public YamlOptions setMultiDocument(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setMultiDocument");
        }

        public YamlOptions setStripComments(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setStripComments");
        }

        public YamlOptions setStripTags(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setStripTags");
        }

        public YamlOptions setStripAnchors(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setStripAnchors");
        }

        public YamlOptions setSortKeys(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setSortKeys");
        }

        public YamlOptions setNormalizeIndent(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setNormalizeIndent");
        }

        public YamlOptions setNormalizeScalarFormatting(boolean val) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setNormalizeScalarFormatting");
        }

        public YamlOptions setForceExplicitNull(boolean b) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setForceExplicitNull");
        }

        public YamlOptions setForceBlockCollections(boolean b) {
            throw new LocalizableRuntimeException(GenericDiagnosticCode.UNSUPPORTED_OPERATION, "setForceBlockCollections");
        }
    }
}