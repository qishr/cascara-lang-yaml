package io.github.qishr.cascara.lang.yaml.testclass;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class Stringy {
    @DataField(key="string")
    private String string = new String("string");
    public String getString() { return string; }
    public Stringy setString(String string) { this.string = string; return this; }
    public Stringy(String string) { this.string = string; }
    public Stringy() {}
}
