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


package io.github.qishr.cascara.lang.yaml.token;

import io.github.qishr.cascara.common.lang.token.TokenCategory;
import io.github.qishr.cascara.common.lang.token.TokenType;

public enum YamlTokenType implements TokenType {
    // Structural layout
    INDENT(TokenCategory.INDENTATION),
    DEDENT(TokenCategory.INDENTATION),
    BLOCK_END(TokenCategory.INDENTATION),

    // Structural punctuation
    KEY_INDICATOR(TokenCategory.PUNCTUATION),
    VALUE_INDICATOR(TokenCategory.PUNCTUATION),
    COMMA(TokenCategory.PUNCTUATION),
    SEQUENCE_ENTRY_INDICATOR(TokenCategory.PUNCTUATION),
    MAP_START(TokenCategory.PUNCTUATION),
    MAP_END(TokenCategory.PUNCTUATION),
    SEQUENCE_START(TokenCategory.PUNCTUATION),
    SEQUENCE_END(TokenCategory.PUNCTUATION),

    // Metadata
    DIRECTIVE(TokenCategory.META),
    TAG(TokenCategory.META),

    // Identifiers
    ANCHOR(TokenCategory.IDENTIFIER),
    ALIAS(TokenCategory.IDENTIFIER),

    // Values
    SCALAR(TokenCategory.STRING),

    // Whitespace & comments
    NEWLINE(TokenCategory.NEWLINE),
    COMMENT(TokenCategory.COMMENT),

    // Parser‑only tokens
    STREAM_START(TokenCategory.INTERNAL),
    STREAM_END(TokenCategory.INTERNAL),
    DOCUMENT_START(TokenCategory.INTERNAL),
    DOCUMENT_END(TokenCategory.INTERNAL),
    EOF(TokenCategory.INTERNAL),

    // Error
    ERROR(TokenCategory.ERROR);


    private final TokenCategory category;

    YamlTokenType(TokenCategory category) {
        this.category = category;
    }

    @Override
    public String getId() {
        return name();
    }

    @Override
    public TokenCategory getCategory() {
        return category;
    }
}
