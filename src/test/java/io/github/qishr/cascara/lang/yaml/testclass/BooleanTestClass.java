package io.github.qishr.cascara.lang.yaml.testclass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class BooleanTestClass {

    @DataField
    public final Map<String, Object> allSettings = new ConcurrentHashMap<>();

    public BooleanTestClass() {
        // Initialize Core Defaults directly into the single map
        this.allSettings.put("dumpCss", false);
    }
}
