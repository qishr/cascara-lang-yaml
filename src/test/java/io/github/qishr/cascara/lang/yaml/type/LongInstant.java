package io.github.qishr.cascara.lang.yaml.type;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class LongInstant {
    private Long value;

    public LongInstant() {

    }

    public LongInstant(Long dt) {
        value = dt;
    }

    public Long getValue() { return value; }
}
