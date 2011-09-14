/*
 *  Copyright 2010 Hippo.
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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FacetedSearchFreeTextTest extends TestCase {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    private static String[] content = new String[] {
        "/test",           "nt:unstructured",
        "jcr:mixinTypes",  "mix:referenceable",
        "/test/docs",      "nt:unstructured",
        "jcr:mixinTypes",  "mix:referenceable",
        "/test/docs/a",    "hippo:handle",
        "jcr:mixinTypes",  "hippo:hardhandle",
        "/test/docs/a/a",  "hippo:testdocument",
        "jcr:mixinTypes",  "hippo:harddocument",
        "x",               "a",
        "y",               "z",
        "text",            "aap",
        "/test/docs/b",    "hippo:handle",
        "jcr:mixinTypes",  "hippo:hardhandle",
        "/test/docs/b/b",  "hippo:testdocument",
        "jcr:mixinTypes",  "hippo:harddocument",
        "x",               "b",
        "y",               "z",
        "text",            "noot",
        "/test/nav1",      "hippo:facetsearch",
        "hippo:facets",    "x",
        "hippo:docbase",   "/test/docs",
        "hippo:queryname", "test",
        "/test/nav2",      "hippo:facetsearch",
        "hippo:facets",    "y",
        "hippo:facets",    "x",
        "hippo:docbase",   "/test/docs",
        "hippo:queryname", "test"
    };

    private static String[] searchPatterns = new String[] {"[aap]", "{aap}", "'aap'" };

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        if (session.getRootNode().hasNode("test")) {
            session.getRootNode().getNode("test").remove();
        }
        build(session, content);
        session.save();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (session.getRootNode().hasNode("test")) {
            session.getRootNode().getNode("test").remove();
        }
        session.save();
        super.tearDown();
    }

    @Test
    public void testFirstLevel() throws RepositoryException {
        Node testRoot = session.getRootNode();
        for (String search : searchPatterns) {
            Node node = testRoot.getNode("test/nav1[" + search + "]");
            assertTrue(node.hasNode("a"));
            assertFalse(node.hasNode("b"));
            assertTrue(node.hasNode("hippo:resultset/a"));
            assertFalse(node.hasNode("hippo:resultset/b"));
            session.refresh(false);
        }
    }

    @Test
    public void testSecondLevel() throws RepositoryException {
        Node testRoot = session.getRootNode();
        for (String search : searchPatterns) {
            Node node = testRoot.getNode("test/nav2[" + search + "]");
            assertTrue(node.hasNode("z/a"));
            assertFalse(node.hasNode("z/b"));
            assertTrue(node.hasNode("hippo:resultset/a"));
            assertFalse(node.hasNode("hippo:resultset/b"));
            assertTrue(node.hasNode("z/a/hippo:resultset/a"));
            assertFalse(node.hasNode("z/a/hippo:resultset/b"));
            session.refresh(false);
        }
    }

    @Test
    public void testParentOfSearch() throws RepositoryException {
        Node testRoot = session.getRootNode();
        {
            Node search1 = testRoot.getNode("test/nav1");
            assertTrue(search1.hasNode("a"));
            assertTrue(search1.hasNode("b"));
            assertEquals("/test/nav1", search1.getPath());
            assertEquals("/test", search1.getParent().getPath());
            session.refresh(false);
        }
        {
            Node search2 = testRoot.getNode("test/nav1[[aap]]");
            assertTrue(search2.hasNode("a"));
            assertFalse(search2.hasNode("b"));
            assertEquals("/test/nav1", search2.getPath());
            assertEquals("/test", search2.getParent().getPath());
            session.refresh(false);
        }
        {
            Node search3 = testRoot.getNode("test/nav1[[noot]]");
            assertFalse(search3.hasNode("a"));
            assertTrue(search3.hasNode("b"));
            assertEquals("/test/nav1", search3.getPath());
            assertEquals("/test", search3.getParent().getPath());
            session.refresh(false);
        }
    }

    @Test
    public void testMultipleSearches() throws RepositoryException {
        Node testRoot = session.getRootNode();
        {
            Node node = testRoot.getNode("test/nav1");
            assertTrue(node.hasNode("a"));
            assertTrue(node.hasNode("b"));
            session.refresh(false);
        }
        {
            Node search1 = testRoot.getNode("test/nav1[[aap]]");
            assertTrue(search1.hasNode("a"));
            assertFalse(search1.hasNode("b"));
            assertTrue(search1.hasNode("hippo:resultset/a"));
            assertFalse(search1.hasNode("hippo:resultset/b"));
            session.refresh(false);
        }
        {
            Node search2 = testRoot.getNode("test/nav1[[noot]]");
            assertFalse(search2.hasNode("a"));
            assertTrue(search2.hasNode("b"));
            assertFalse(search2.hasNode("hippo:resultset/a"));
            assertTrue(search2.hasNode("hippo:resultset/b"));
            session.refresh(false);
        }
        {
            Node search3 = testRoot.getNode("test/nav1");
            assertTrue(search3.hasNode("a"));
            assertTrue(search3.hasNode("b"));
        }
    }

    @Ignore
    public void testMultipleIntermixedSearches() throws RepositoryException {
        /* FIXME: it is not yet possible to have multiple free text searches */
        Node testRoot = session.getRootNode();
        Node node = testRoot.getNode("test/nav1");
        Node search1 = testRoot.getNode("test/nav1[[aap]]");
        Node search2 = testRoot.getNode("test/nav1[[noot]]");
        Node search3 = testRoot.getNode("test/nav1");

        org.hippoecm.repository.util.Utilities.dump(System.err, search1);
        assertTrue(node.hasNode("a"));
        assertTrue(node.hasNode("b"));
        assertTrue(search1.hasNode("a"));
        assertFalse(search1.hasNode("b"));
        assertTrue(search1.hasNode("hippo:resultset/a"));
        assertFalse(search1.hasNode("hippo:resultset/b"));
        assertFalse(search2.hasNode("a"));
        assertTrue(search2.hasNode("b"));
        assertFalse(search2.hasNode("hippo:resultset/a"));
        assertTrue(search2.hasNode("hippo:resultset/b"));
        assertTrue(search3.hasNode("a"));
        assertTrue(search3.hasNode("b"));
    }

    @Test
    public void testThresholdExceeded() throws RepositoryException {
        if (external != null) {
            return; // not a valid test for remote repositories
        }
        Node testRoot = session.getRootNode();
        {
            Node search = testRoot.getNode("test/nav1");
            for(NodeIterator nodeIter = search.getNodes(); nodeIter.hasNext(); ) {
                Node child = nodeIter.nextNode();
            }
            assertFalse(server.stateThresholdExceeded(session, null));
        }
        session.refresh(false);
        assertFalse(server.stateThresholdExceeded(session, null));
        {
            Node search = testRoot.getNode("test/nav1[[aap]]");
            for(NodeIterator nodeIter = search.getNodes(); nodeIter.hasNext(); ) {
                Node child = nodeIter.nextNode();
            }
            assertTrue(server.stateThresholdExceeded(session, null));
        }
        session.refresh(false);
        assertFalse(server.stateThresholdExceeded(session, null));
    }
}
