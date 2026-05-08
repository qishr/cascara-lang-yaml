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
