package io.github.qishr.cascara.lang.yaml.type;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class NestedConfig {
    @DataField
    public boolean enabled = true;

    public NestedConfig() {}
}