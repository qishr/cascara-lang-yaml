// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.yaml.util;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.common.annotation.DataField;

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
