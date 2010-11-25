/*
 *  Copyright 2008 Hippo.
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
package org.hippoecm.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.junit.Before;
import org.junit.Test;

public class HREPTWO1493Test extends TestCase {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private final String[] content1 = {
        "/test",              "nt:unstructured",
        "/test/docs",         "nt:unstructured",
        "jcr:mixinTypes",     "mix:referenceable",
        "/test/docs/doc",     "hippo:handle",
        "jcr:mixinTypes",     "hippo:hardhandle",
        "/test/docs/doc",     "hippo:handle",
        "jcr:mixinTypes",     "hippo:hardhandle",
        "/test/docs/doc/doc", "hippo:testdocument",
        "jcr:mixinTypes",     "hippo:harddocument",
        "hippo:x",            "test"
    };

    @Test
    public void testParentOfProperty() throws RepositoryException {
        Property property;
        Node node;
        build(session, content1);
        session.save();

        node = session.getRootNode().getNode("test").addNode("virtual", "hippo:facetselect");
        node.setProperty("hippo:docbase", session.getRootNode().getNode("test/docs").getUUID());
        node.setProperty("hippo:modes", new Value[0]);
        node.setProperty("hippo:facets", new Value[0]);
        node.setProperty("hippo:values", new Value[0]);
        session.save();

        node = traverse(session, "/test/virtual/doc/doc");
        property = node.getProperty("hippo:x");
        assertEquals("/test/virtual/doc/doc/hippo:x", property.getPath());
        assertEquals("/test/virtual/doc/doc", property.getParent().getPath());
    }

    @Test
    public void testModifyVirtualProperty() throws RepositoryException {
        Property property;
        Node node;
        build(session, content1);
        session.save();

        node = session.getRootNode().getNode("test").addNode("virtual", "hippo:facetselect");
        node.setProperty("hippo:docbase", session.getRootNode().getNode("test/docs").getUUID());
        node.setProperty("hippo:modes", new Value[0]);
        node.setProperty("hippo:facets", new Value[0]);
        node.setProperty("hippo:values", new Value[0]);
        session.save();

        node = traverse(session, "/test/virtual/doc/doc");
        node.setProperty("hippo:x", "modified");
        session.save();

        node = traverse(session, "/test/docs/doc/doc");
        assertTrue(node.hasProperty("hippo:x"));
        assertEquals("test", node.getProperty("hippo:x").getString());

        node = traverse(session, "/test/virtual/doc/doc");
        node.setProperty("hippo:x", "modified");
        session.save();

        node = traverse(session, "/test/docs/doc/doc");
        assertTrue(node.hasProperty("hippo:x"));
        assertEquals("test", node.getProperty("hippo:x").getString());
    }

    @Test
    public void testModifyPropertyAfterBrowsingVirtual() throws RepositoryException {
        Node node;
        build(session, content1);
        session.save();
        session.refresh(false);

        node = session.getRootNode().getNode("test").addNode("virtual", "hippo:facetselect");
        node.setProperty("hippo:docbase", session.getRootNode().getNode("test/docs").getUUID());
        node.setProperty("hippo:modes", new Value[0]);
        node.setProperty("hippo:facets", new Value[0]);
        node.setProperty("hippo:values", new Value[0]);
        session.save();

        session.logout();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);

        node = traverse(session, "/test/docs/doc/doc");
        node.setProperty("hippo:x", "changed");
        session.save();

        session.logout();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);

        node = traverse(session, "/test/docs/doc/doc");
        assertEquals("changed", node.getProperty("hippo:x").getString());

        restart();

        /*
        node = traverse(session, "/test/virtual/doc/doc");
        assertNotNull(node);
        assertEquals("changed", node.getProperty("hippo:x").getString());
        node.setProperty("hippo:x", "invalid");
        node.save();

        session.logout();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);

        node = traverse(session, "/test/docs/doc/doc");
        assertEquals("changed", node.getProperty("hippo:x").getString());

        session.logout();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);
        */

        node = traverse(session, "/test/virtual/doc/doc");
        assertNotNull(node);
        assertEquals("changed", node.getProperty("hippo:x").getString());

        node = traverse(session, "/test/docs/doc/doc");
        node.setProperty("hippo:x", "reset");
        session.save();

        session.logout();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);

        node = traverse(session, "/test/docs/doc/doc");
        assertEquals("reset", node.getProperty("hippo:x").getString());
    }

    private void restart() throws RepositoryException {
        session.refresh(false);
        session.logout();
        server.close();
        try {
            Thread.sleep(1000);
        } catch(InterruptedException ex) {
        }
        server = HippoRepositoryFactory.getHippoRepository();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);
    }
}
