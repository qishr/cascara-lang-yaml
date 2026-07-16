package io.github.qishr.cascara.lang.yaml.type;

import java.time.ZonedDateTime;

import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class TypeDescriptorTestClass {
    @SuppressWarnings("unused")
    private ZonedDateTime dateTime;

    public TypeDescriptorTestClass(ZonedDateTime dt) {
        dateTime = dt;
    }
}
