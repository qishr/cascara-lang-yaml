package io.github.qishr.cascara.lang.yaml.exception;

import io.github.qishr.cascara.common.lang.exception.SerializerException;

public class YamlSerializerException extends SerializerException {
    public YamlSerializerException(String m) {
        super(m);
    }

    public YamlSerializerException(String m, Throwable t) {
        super(m, t);
    }
}
