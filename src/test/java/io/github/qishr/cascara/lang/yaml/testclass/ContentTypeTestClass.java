package io.github.qishr.cascara.lang.yaml.testclass;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class ContentTypeTestClass {

    @DataField
    public String canonicalId = "";

    @DataField
    public String canonicalName = "";

    @DataField
    public List<String> mimeTypes = new ArrayList<>();

    @DataField
    public List<String> suffixes = new ArrayList<>();

    @DataField
    public String moduleId = "";

    public ContentTypeTestClass() {}

    public ContentTypeTestClass withType(String mimeType) {
        mimeTypes.add(mimeType);
        return this;
    }

    public ContentTypeTestClass withFilenameExt(String suffix) {
        suffixes.add(suffix);
        return this;
    }

    public String getCanonicalId() {
        return canonicalId;
    }

    public void setCanonicalId(String canonicalId) {
        this.canonicalId = canonicalId;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public void setMimeTypes(List<String> mimeTypes) {
        this.mimeTypes = mimeTypes;
    }

    public List<String> getSuffixes() {
        return suffixes;
    }

    public void setSuffixes(List<String> suffixes) {
        this.suffixes = suffixes;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }
}
