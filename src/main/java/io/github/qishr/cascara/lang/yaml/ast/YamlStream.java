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

public class YamlStream extends YamlNode {
    private final List<YamlDocument> documents = new ArrayList<>();
    // private final List<YamlComment> comments = new ArrayList<>();

    public YamlStream() {
        super();
    }

    public YamlStream(YamlToken token) {
        super(token);
    }

    public YamlStream(YamlToken token, List<YamlDocument> documents) {
        super(token);
        this.documents.addAll(documents);
    }

    public YamlStream(YamlToken token, List<YamlDocument> documents, List<YamlComment> comments) {
        super(token);
        this.documents.addAll(documents);
        this.getComments().addAll(comments);
    }

    public void addDocument(YamlDocument document) {
        if (document != null) {
            this.documents.add(document);
        }
    }

    public List<YamlDocument> getDocuments() {
        return documents;
    }

    // TODO: Iterator of documents

    public YamlDocument getDocument(int i) {
        return documents.get(i);
    }

    // public List<YamlComment> getComments() {
    //     return comments;
    // }

    public boolean isEmpty() {
        return documents.isEmpty();
    }

    @Override
    public void accept(YamlVisitor visitor) {
        visitor.visit(this);
    }

	@Override
	public List<? extends YamlNode> getChildren() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getChildren'");
	}
}