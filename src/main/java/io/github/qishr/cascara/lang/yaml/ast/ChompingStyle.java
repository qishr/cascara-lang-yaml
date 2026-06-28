package io.github.qishr.cascara.lang.yaml.ast;

public enum ChompingStyle {
    CLIP,  // Default (one trailing newline)
    STRIP, // - (no trailing newlines)
    KEEP   // + (all trailing newlines preserved)
}