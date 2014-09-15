/*
 * Copyright 2014 Hippo B.V. (http://www.onehippo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onehippo.repository.documentworkflow.integration;

import java.util.Date;

import javax.jcr.Node;

import org.junit.Test;
import org.onehippo.repository.documentworkflow.DocumentWorkflow;

import static org.hippoecm.repository.HippoStdNodeType.PUBLISHED;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestTest extends AbstractDocumentWorkflowIntegrationTest {

    @Test
    public void testRequestAndAcceptPublication() throws Exception {
        DocumentWorkflow workflow = getDocumentWorkflow(handle);

        // edit document
        Node draft = workflow.obtainEditableInstance().getNode(session);
        draft.setProperty("foo", "bar");
        session.save();
        workflow.commitEditableInstance();

        workflow.requestPublication();
        assertTrue("Publication request not found", handle.hasNode(HIPPO_REQUEST));

        Node request = handle.getNode(HIPPO_REQUEST);
        workflow.acceptRequest(request.getIdentifier());

        assertFalse("Request still on handle", handle.hasNode(HIPPO_REQUEST));
        assertTrue("Document is not live", isLive());
        assertEquals("Edit did not make it to live", "bar", getVariant(PUBLISHED).getProperty("foo").getString());
    }

    @Test
    public void testRequestAndAcceptDepublication() throws Exception {
        DocumentWorkflow workflow = getDocumentWorkflow(handle);
        workflow.publish();

        workflow.requestDepublication();
        assertTrue("Depublication request not found", handle.hasNode(HIPPO_REQUEST));

        Node request = handle.getNode(HIPPO_REQUEST);
        workflow.acceptRequest(request.getIdentifier());

        assertFalse("Request still on handle", handle.hasNode(HIPPO_REQUEST));
        assertFalse("Document is still live", isLive());
    }

    @Test
    public void testRequestAndAcceptScheduledPublication() throws Exception {
        DocumentWorkflow workflow = getDocumentWorkflow(handle);

        // edit document
        Node draft = workflow.obtainEditableInstance().getNode(session);
        draft.setProperty("foo", "bar");
        session.save();
        workflow.commitEditableInstance();

        workflow.requestPublication(new Date(System.currentTimeMillis()+1000));
        poll(new Executable() {
            @Override
            public void execute() throws Exception {
                assertTrue("Publication request not found", handle.hasNode(HIPPO_REQUEST));
            }
        }, 10);

        Node request = handle.getNode(HIPPO_REQUEST);
        workflow.acceptRequest(request.getIdentifier());

        poll(new Executable() {
            @Override
            public void execute() throws Exception {
                assertFalse("Request still on handle", handle.hasNode(HIPPO_REQUEST));
            }
        }, 10);

        assertTrue("Document is not live", isLive());
        assertEquals("Edit did not make it to live", "bar", getVariant(PUBLISHED).getProperty("foo").getString());

    }

    @Test
    public void testRequestAndAcceptScheduledDepublication() throws Exception {
        DocumentWorkflow workflow = getDocumentWorkflow(handle);
        workflow.publish();

        workflow.requestDepublication();
        poll(new Executable() {
            @Override
            public void execute() throws Exception {
                assertTrue("Depublication request not found", handle.hasNode(HIPPO_REQUEST));
            }
        }, 10);

        Node request = handle.getNode(HIPPO_REQUEST);
        workflow.acceptRequest(request.getIdentifier());

        poll(new Executable() {
            @Override
            public void execute() throws Exception {
                assertFalse("Request still on handle", handle.hasNode(HIPPO_REQUEST));
            }
        }, 10);

        assertFalse("Document is still live", isLive());
    }
}