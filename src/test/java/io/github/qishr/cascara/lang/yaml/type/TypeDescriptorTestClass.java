package io.github.qishr.cascara.lang.yaml.type;

import java.time.LocalDateTime;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class TypeDescriptorTestClass {
    @SuppressWarnings("unused")
    private LocalDateTime dateTime;

    public TypeDescriptorTestClass(LocalDateTime dt) {
        dateTime = dt;
    }
}
