package io.github.qishr.cascara.lang.yaml.testclass;

import java.util.HashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.annotation.AnyGetter;
import io.github.qishr.cascara.common.lang.annotation.AnySetter;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class SettingsTestClass {

    private Map<String, Object> otherSettings = new HashMap<>();

    @AnySetter
    public void addSetting(String key, Object value) {
        System.out.println("DEBUG: Setting " + key + " to " + value + " (" + value.getClass().getSimpleName() + ")");
        this.otherSettings.put(key, value);
    }

    @AnyGetter
    public Map<String, Object> getOtherSettings() {
        return otherSettings;
    }
}
