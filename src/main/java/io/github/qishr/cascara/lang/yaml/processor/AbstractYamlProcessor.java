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


package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.NoOpReporter;
import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.processor.Processor;
import io.github.qishr.cascara.common.lang.util.LanguageOptions;
import io.github.qishr.cascara.common.semver.SemVer;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.util.JarManifest;
import io.github.qishr.cascara.common.util.Properties;
import io.github.qishr.cascara.lang.yaml.util.YamlOptions;

public abstract class AbstractYamlProcessor<P extends Processor> implements Processor {
    static final String YAML_CONTENT_TYPE_STRING = "application/yaml";

    static final ContentType YAML_CONTENT_TYPE =
        new ContentType("YAML")
            .withType("text/yaml")
            .withType("application/yaml")
            .withSuffix(".yaml")
            .withSuffix(".yml");

    protected YamlOptions options = new YamlOptions();
    protected Reporter reporter = new NoOpReporter();
    private Properties properties;

    protected abstract P self();

    @Override
    public Properties getServiceProperties() {
        if (properties == null) {
            properties = new Properties();
            properties.set("contentType", YAML_CONTENT_TYPE_STRING);
        }
        return properties;
    }

    @Override
    public ContentType getContentType() {
        return YAML_CONTENT_TYPE;
    }

    /// {@inheritDoc}
    @Override
    public P setReporter(Reporter reporter) {
        this.reporter = reporter == null ? new NoOpReporter() : reporter;
        return self();
    }

    /// {@inheritDoc}
    @Override
    public P setOptions(LanguageOptions<?> options) {
        this.options = options == null ? new YamlOptions() : (YamlOptions) options;
        return self();
    }

    public YamlOptions getOptions() {
        return options;
    }

    public Reporter getReporter() {
        return reporter;
    }

    public SemVer getVersion() {
        return JarManifest.of(getClass()).getVersion();
    }
}
