/*
 *  Copyright 2008-2013 Hippo B.V. (http://www.onehippo.com)
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

import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javax.jcr.Node;

import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.WorkflowManager;
import org.hippoecm.repository.standardworkflow.VersionWorkflow;
import org.junit.Test;
import org.onehippo.repository.testutils.RepositoryTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class VersioningWorkflowTest extends RepositoryTestCase {

    @Test
    public void testSimpleVersioning() throws Exception {
        Node node, root = session.getRootNode();
        node = root.addNode("test", "nt:unstructured");
        node = node.addNode("testdocument", "hippo:handle");
        node.addMixin("hippo:hardhandle");
        node = node.addNode("testdocument", "hippo:testdocument");
        node.addMixin("hippo:harddocument");
        node.addMixin("hippostd:publishable");
        node.setProperty("hippostd:state", "published");
        session.save();

        WorkflowManager wflMgr = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
        VersionWorkflow versionwf = (VersionWorkflow) wflMgr.getWorkflow("versioning", node);
        assertNotNull(versionwf);

        versionwf.version();

        SortedMap<Calendar,Set<String>> list = versionwf.list();
        assertEquals(1, list.size());

        node.checkout();
        node.setProperty("aap", "noot");

        versionwf.version();

        list = versionwf.list();
        assertEquals(2, list.size());

        Iterator<Map.Entry<Calendar, Set<String>>> iter = list.entrySet().iterator();
        iter.next();
        Document restored = versionwf.restore(iter.next().getKey());
        session.refresh(false);
        assertFalse(session.getNodeByIdentifier(restored.getIdentity()).hasProperty("aap"));
    }

    @Test
    public void testRestoreToTypeWithAutocreatedChild() throws Exception {
        Node node, root = session.getRootNode();
        node = root.addNode("test", "nt:unstructured");
        node = node.addNode("testdocument", "hippo:handle");
        node.addMixin("hippo:hardhandle");
        node = node.addNode("testdocument", "hippo:autocreatedchild");
        node.addMixin("hippo:harddocument");
        node.addMixin("hippostd:publishable");
        node.setProperty("hippostd:state", "published");
        session.save();


        WorkflowManager wflMgr = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
        VersionWorkflow versionwf = (VersionWorkflow) wflMgr.getWorkflow("versioning", node);
        assertNotNull(versionwf);

        Document initial = versionwf.version();
        Node initialVersion = session.getNodeByIdentifier(initial.getIdentity());

        SortedMap<Calendar,Set<String>> list = versionwf.list();
        assertEquals(1, list.size());

        node.checkout();
        node.setProperty("aap", "noot");
        session.save();

        versionwf = (VersionWorkflow) wflMgr.getWorkflow("versioning", initialVersion.getNode("jcr:frozenNode"));
        versionwf.restoreTo(new Document(node));
        session.refresh(false);

        assertFalse(node.hasProperty("aap"));
    }

}
