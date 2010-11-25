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

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.RepositoryException;
import javax.jcr.InvalidItemStateException;

import org.junit.*;
import static org.junit.Assert.*;

public class HREPTWO548Test extends TestCase {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private String[] content1 = {
        "/test", "nt:unstructured",
        "/test/docs", "nt:unstructured",
        "jcr:mixinTypes", "mix:referenceable",
        "/test/docs/red", "hippo:document",
        "jcr:mixinTypes", "hippo:harddocument"
    };
    private String[] content2 = {
        "/test/nav", "hippo:facetselect",
        "hippo:docbase", "/test/docs",
        "hippo:facets", "lang",
        "hippo:values", "en",
        "hippo:modes", "select"
    };
    private String[] content3 = {
        "/test/docs/blue", "hippo:document",
        "jcr:mixinTypes", "hippo:harddocument"
    };

    @Before
    public void setUp() throws Exception {
        super.setUp();
        while (session.getRootNode().hasNode("test")) {
            session.getRootNode().getNode("test").remove();
            session.save();
        }
    }

    @After
    public void tearDown() throws Exception {
        session.save(); //session.refresh(false);
        if (session.getRootNode().hasNode("test")) {
            session.getRootNode().getNode("test").remove();
            session.save();
        }
    }

    @Test
    public void testIssue() throws RepositoryException {
        Node result;
        build(session, content1);
        session.save();
        build(session, content2);
        session.save();
        session.refresh(false);
        //Utilities.dump(session.getRootNode());

        result = traverse(session, "/test/docs/red");
        assertNotNull(result);

        Node browse = traverse(session, "/test/nav");
        assertNotNull(result);
        assertTrue(browse.hasNode("red"));
        assertFalse(browse.hasNode("blue"));

        session.refresh(false);
        try {
            assertFalse(browse.hasNode("yellow"));
        } catch(InvalidItemStateException ex) {
            // allowed result
        }

        browse = traverse(session, "/test/nav");

        { // intermezzo: other session adds node
            Session session2 = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);
            session2.getRootNode().getNode("test/docs");
            build(session2, content3);
            session2.save();
            session2.logout();
        }

        assertTrue(browse.hasNode("red"));
        assertFalse(browse.hasNode("blue"));

        // with refresh(true) you DON'T get your virtual tree updated
        session.refresh(true);

        browse = traverse(session, "/test/nav");
        assertNotNull(result);
        assertTrue(browse.hasNode("red"));
        assertFalse(browse.hasNode("blue"));

        // with refresh(false) you DO get your virtual tree updated
        session.refresh(false);

        browse = traverse(session, "/test/nav");
        assertNotNull(result);
        assertTrue(browse.hasNode("red"));
        assertTrue(browse.hasNode("blue"));
    }
}
