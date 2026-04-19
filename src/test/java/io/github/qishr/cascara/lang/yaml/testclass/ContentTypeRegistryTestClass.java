package io.github.qishr.cascara.lang.yaml.testclass;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class ContentTypeRegistryTestClass {

    @DataField
    public List<ContentTypeTestClass> records = new ArrayList<>();

    public ContentTypeRegistryTestClass() {}

    public List<ContentTypeTestClass> getRecords() {
        return records;
    }

    public void setRecords(List<ContentTypeTestClass> records) {
        this.records = records;
    }
}
