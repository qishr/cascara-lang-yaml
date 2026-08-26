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


package io.github.qishr.cascara.lang.yaml.ast;

import java.util.ArrayList;
import java.util.List;

import io.github.qishr.cascara.lang.yaml.token.YamlToken;
import io.github.qishr.cascara.lang.yaml.util.YamlVisitor;

public class YamlDocument extends YamlNode {
    private final List<YamlDirective> directives = new ArrayList<>();
    private final List<YamlTagDirective> tagsDirectives = new ArrayList<>();
    private YamlNode body;
    private boolean hasStartMarker;
    private boolean hasEndMarker;
    private YamlDirective yamlDirective;

    public YamlDocument(YamlToken token) {
        super(token);
    }

    public YamlDocument(YamlToken token, YamlNode body, List<YamlDirective> directives) {
        super(token);
        this.body = body;
        this.directives.addAll(directives);
    }

    public void addDirective(YamlDirective directive) {
        if (directive != null) {
            this.directives.add(directive);
            if (directive instanceof YamlTagDirective tagDirective) {
                tagsDirectives.add(tagDirective);
            } else {
                yamlDirective = directive;
            }
        }
    }

    public List<YamlDirective> getDirectives() {
        return directives;
    }

    public List<YamlTagDirective> getTagsDirectives() {
        return tagsDirectives;
    }

    public YamlDirective getYamlDirective() {
        return yamlDirective;
    }

    public YamlNode getBody() {
        return body;
    }

    public void setBody(YamlNode body) {
        this.body = body;
    }

    public boolean hasEndMarker() {
        return hasEndMarker;
    }

    public void setHasEndMarker(boolean b) {
        hasEndMarker = b;
    }

    public boolean hasStartMarker() {
        return hasStartMarker;
    }

    public void setHasStartMarker(boolean b) {
        hasStartMarker = b;
    }

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }

	@Override
	public List<YamlNode> getChildren() {
        List<YamlNode> children = new ArrayList<>();
        for (YamlNode node : directives) {
            children.add(node);
        }
        children.add(body);
        return children;
	}
}