package io.github.qishr.cascara.lang.yaml.testclass;

import java.time.LocalDateTime;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class TestTypeDescriptor2 {
    private Long dateTime;

    public TestTypeDescriptor2() {

    }

    public TestTypeDescriptor2(Long dt) {
        dateTime = dt;
    }

    public Long getValue() { return dateTime; }
}
