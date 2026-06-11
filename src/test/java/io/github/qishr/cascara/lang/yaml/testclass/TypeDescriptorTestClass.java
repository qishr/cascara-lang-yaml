package io.github.qishr.cascara.lang.yaml.testclass;

import java.time.LocalDateTime;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class TypeDescriptorTestClass {
    private LocalDateTime dateTime;

    public TypeDescriptorTestClass(LocalDateTime dt) {
        dateTime = dt;
    }
}
