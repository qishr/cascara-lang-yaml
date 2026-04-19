package io.github.qishr.cascara.lang.yaml.testclass;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class TestState {
    @DataField
    public List<String> disabledModules = new ArrayList<>();

    @DataField
    public NestedConfig security; // This is the object that will be null in YAML

    public TestState() {}
}

