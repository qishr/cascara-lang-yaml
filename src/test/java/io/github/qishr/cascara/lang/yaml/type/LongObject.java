package io.github.qishr.cascara.lang.yaml.type;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class LongObject {
    private Long value;

    public Long getValue() {
        return value;
    }

    public void setRaw(Long value) {
        this.value = value;
    }
}
