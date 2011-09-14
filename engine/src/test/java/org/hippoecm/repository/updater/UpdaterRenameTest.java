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
package org.hippoecm.repository.updater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.StringReader;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.hippoecm.repository.Modules;
import org.hippoecm.repository.TestCase;
import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.ext.UpdaterContext;
import org.hippoecm.repository.ext.UpdaterItemVisitor;
import org.hippoecm.repository.ext.UpdaterModule;
import org.hippoecm.repository.standardworkflow.RepositoryWorkflow;
import org.hippoecm.repository.util.Utilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class UpdaterRenameTest extends TestCase {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    private final String[] content = {
        "/test", "nt:unstructured",
        "/test/d", "testsubns:document",
        "jcr:mixinTypes", "hippo:harddocument",
        "testsuperns:x", "1",
        "testsubns:x", "2",
        "testsuperns:y", "11",
        "testsubns:y", "12"
    };

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp(true);
        NamespaceRegistry namespaceRegistry = session.getWorkspace().getNamespaceRegistry();
        build(session, content);
        session.save();
    }

    @Test
    public void testCommonNamespace() throws RepositoryException {
        UpdaterModule module = new UpdaterModule() {
            public void register(UpdaterContext context) {
                context.registerVisitor(new UpdaterItemVisitor.NodeTypeVisitor("testsubns:document") {
                        protected void leaving(Node node, int level) throws RepositoryException {
                            if(node.hasProperty("testsuperns:y")) {
                                node.setProperty("testsuperns:z", node.getProperty("testsuperns:y").getString());
                            }
                            if(node.hasProperty("testsubns:y")) {
                                node.setProperty("testsubns:z", node.getProperty("testsubns:y").getString());
                            }
                        }
                    });
                context.registerVisitor(new UpdaterItemVisitor.NamespaceVisitor(context, "testsuperns", "-",  new InputStreamReader(getClass().getClassLoader().getResourceAsStream("repository-testsuperns2.cnd"))));
                context.registerVisitor(new UpdaterItemVisitor.NamespaceVisitor(context, "testsubns", "-",  new InputStreamReader(getClass().getClassLoader().getResourceAsStream("repository-testsubns2.cnd"))));
            }
        };
        List list = new LinkedList();
        list.add(module);
        Modules modules = new Modules(list);
        UpdaterEngine.migrate(session, modules);
        session.logout();
        session = server.login(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD);
        Node node = session.getRootNode().getNode("test").getNode("d");
        assertTrue(node.hasProperty("testsuperns:x"));
        assertTrue(node.hasProperty("testsubns:x"));
        assertFalse(node.hasProperty("testsuperns:y"));
        assertFalse(node.hasProperty("testsubns:y"));
        assertTrue(node.hasProperty("testsuperns:z"));
        assertTrue(node.hasProperty("testsubns:z"));
    }
}
