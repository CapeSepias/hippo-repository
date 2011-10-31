/*
 *  Copyright 2011 Hippo.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.hippoecm.repository.jackrabbit.xml;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.xml.DocumentViewExporter;
import org.hippoecm.repository.api.HippoNode;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Document view exporter that does not export virtual nodes.
 */
public class HippoDocumentViewExporter extends DocumentViewExporter {

    public HippoDocumentViewExporter(Session session, ContentHandler handler, boolean recurse, boolean binary) {
        super(session, handler, recurse, binary);
    }

    @Override
    protected void exportNodes(Node node) throws RepositoryException, SAXException {
        if (!isVirtual(node)) {
            super.exportNodes(node);
        }
    }

    private boolean isVirtual(Node node) throws RepositoryException {
        if (node == null || !(node instanceof HippoNode)) {
            return false;
        }
        try {
            HippoNode hippoNode = (HippoNode) node;
            Node canonical = hippoNode.getCanonicalNode();
            if (canonical == null) {
                return true;
            }
            return !canonical.isSame(hippoNode);
        } catch (ItemNotFoundException e) {
            return true;
        }

    }
}
