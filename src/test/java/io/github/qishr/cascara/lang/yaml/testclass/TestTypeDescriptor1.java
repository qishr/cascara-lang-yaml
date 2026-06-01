package io.github.qishr.cascara.lang.yaml.testclass;

import java.time.LocalDateTime;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class TestTypeDescriptor1 {
    private LocalDateTime dateTime;

    public TestTypeDescriptor1(LocalDateTime dt) {
        dateTime = dt;
    }
}
