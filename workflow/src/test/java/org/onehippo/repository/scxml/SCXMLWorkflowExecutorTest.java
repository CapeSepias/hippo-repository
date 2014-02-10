/*
 * Copyright 2013 Hippo B.V. (http://www.onehippo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onehippo.repository.scxml;

import org.hippoecm.repository.api.WorkflowException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.repository.mock.MockNode;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * SCXMLWorkflowExecutorTest
 * */
public class SCXMLWorkflowExecutorTest {

    private static final String SCXML_TEST =
            "<scxml xmlns=\"http://www.w3.org/2005/07/scxml\" xmlns:hippo=\"http://www.onehippo.org/cms7/repository/scxml\" version=\"1.0\" initial=\"hello\">\n" +
                    "  <state id=\"hello\">\n" +
                    "    <onentry>\n" +
                    "      <hippo:action action=\"hello\" enabledExpr=\"true\"/>\n" +
                    "      <hippo:result value=\"message\"/>\n" +
                    "    </onentry>\n" +
                    "  </state>\n" +
                    "</scxml>";

    private static final String SCXML_FAILING_TEST =
            "<scxml xmlns=\"http://www.w3.org/2005/07/scxml\" xmlns:hippo=\"http://www.onehippo.org/cms7/repository/scxml\" version=\"1.0\" initial=\"hello\">\n" +
                    "  <state id=\"hello\">\n" +
                    "    <onentry>\n" +
                    "      <hippo:action action=\"hello\" enabledExpr=\"unknown\"/>\n" +
                    "    </onentry>\n" +
                    "  </state>\n" +
                    "</scxml>";

    private static final String SCXML_TERMINATE_TEST =
            "<scxml xmlns=\"http://www.w3.org/2005/07/scxml\" xmlns:hippo=\"http://www.onehippo.org/cms7/repository/scxml\" version=\"1.0\" initial=\"hello\">\n" +
                    "  <state id=\"hello\">\n" +
                    "    <onentry>\n" +
                    "      <hippo:action action=\"hello\" enabledExpr=\"true\"/>\n" +
                    "      <hippo:action action=\"terminate\" enabledExpr=\"true\"/>\n" +
                    "    </onentry>\n" +
                    "    <transition event=\"hello\">\n" +
                    "      <hippo:result value=\"_eventdata\"/>\n" +
                    "    </transition>\n" +
                    "    <transition event=\"terminate\" target=\"terminated\"/>\n" +
                    "  </state>\n" +
                    "  <final id=\"terminated\"/>\n" +
                    "</scxml>";

    private MockRepositorySCXMLRegistry registry;
    private SCXMLExecutorFactory factory;

    @Before
    public void before() throws Exception {
        registry = new MockRepositorySCXMLRegistry();
        factory = new RepositorySCXMLExecutorFactory();
        HippoServiceRegistry.registerService(registry, SCXMLRegistry.class);
        HippoServiceRegistry.registerService(factory, SCXMLExecutorFactory.class);
    }

    @After
    public void after() throws Exception {
        HippoServiceRegistry.unregisterService(factory, SCXMLExecutorFactory.class);
        HippoServiceRegistry.unregisterService(registry, SCXMLRegistry.class);
    }

    @Test
    public void testTest() throws Exception {
        MockNode scxmlConfigNode = registry.createConfigNode();
        MockNode scxmlDefNode = registry.addScxmlNode(scxmlConfigNode, "scxml", SCXML_TEST);
        registry.addCustomAction(scxmlDefNode, "http://www.onehippo.org/cms7/repository/scxml", "action", ActionAction.class.getName());
        registry.addCustomAction(scxmlDefNode, "http://www.onehippo.org/cms7/repository/scxml", "result", ResultAction.class.getName());
        registry.setUp(scxmlConfigNode);

        SCXMLWorkflowExecutor workflowExecutor = new SCXMLWorkflowExecutor(new MockSCXMLDataModel("scxml"));
        workflowExecutor.getSCXMLExecutor().getRootContext().set("message", "Hello world!");
        Object message = workflowExecutor.start();

        assertTrue(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());
        assertTrue(workflowExecutor.getDataModel().getActions().get("hello"));
        assertEquals("Hello world!", message);

        try {
            workflowExecutor.triggerAction("foo");
            fail("triggerAction foo should have failed");
        }
        catch (WorkflowException e) {
            assertEquals("Cannot invoke workflow scxml action foo: action not allowed or undefined", e.getMessage());
        }
        assertTrue(workflowExecutor.getDataModel().getActions().get("hello"));
        assertNull(workflowExecutor.triggerAction("hello"));
        assertTrue(workflowExecutor.isStarted());

        workflowExecutor.setDataModel(new MockSCXMLDataModel("scxml"));
        assertFalse(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());

        try {
            workflowExecutor.triggerAction("foo");
            fail("triggerAction foo should have failed");
        }
        catch (WorkflowException e) {
            assertEquals("Workflow scxml not started", e.getMessage());
        }
        workflowExecutor.start();
        assertTrue(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());
    }

    @Test
    public void testFailingTest() throws Exception {
        MockNode scxmlConfigNode = registry.createConfigNode();
        MockNode scxmlDefNode = registry.addScxmlNode(scxmlConfigNode, "scxml", SCXML_FAILING_TEST);
        registry.addCustomAction(scxmlDefNode, "http://www.onehippo.org/cms7/repository/scxml", "action", ActionAction.class.getName());
        registry.setUp(scxmlConfigNode);

        SCXMLWorkflowExecutor workflowExecutor = new SCXMLWorkflowExecutor(new MockSCXMLDataModel("scxml"));
        try {
            workflowExecutor.start();
            fail("Workflow should have failed starting");
        }
        catch (WorkflowException e) {
            assertEquals("Workflow scxml execution failed", e.getMessage());
        }
        assertFalse(workflowExecutor.isStarted());
        try {
            workflowExecutor.triggerAction("foo");
            fail("triggerAction foo should have failed");
        }
        catch (WorkflowException e) {
            assertEquals("Workflow scxml not started", e.getMessage());
        }
    }

    @Test
    public void testTerminateTest() throws Exception {
        MockNode scxmlConfigNode = registry.createConfigNode();
        MockNode scxmlDefNode = registry.addScxmlNode(scxmlConfigNode, "scxml", SCXML_TERMINATE_TEST);
        registry.addCustomAction(scxmlDefNode, "http://www.onehippo.org/cms7/repository/scxml", "action", ActionAction.class.getName());
        registry.addCustomAction(scxmlDefNode, "http://www.onehippo.org/cms7/repository/scxml", "result", ResultAction.class.getName());
        registry.setUp(scxmlConfigNode);

        SCXMLWorkflowExecutor workflowExecutor = new SCXMLWorkflowExecutor(new MockSCXMLDataModel("scxml"));
        workflowExecutor.start();

        assertTrue(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());
        assertTrue(workflowExecutor.getDataModel().getActions().get("hello"));
        assertTrue(workflowExecutor.getDataModel().getActions().get("terminate"));

        Object message = workflowExecutor.triggerAction("hello", "Hello world!");
        assertEquals("Hello world!", message);
        assertTrue(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());
        message = workflowExecutor.triggerAction("hello", "Hello world!");
        assertEquals("Hello world!", message);
        assertTrue(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());
        workflowExecutor.triggerAction("terminate");
        assertTrue(workflowExecutor.isTerminated());
        try {
            workflowExecutor.triggerAction("hello", "Hello world!");
            fail("triggerAction on a terminated workflow should have failed");
        }
        catch (WorkflowException e) {
            assertEquals("Workflow scxml already terminated", e.getMessage());
        }
        // simple reset
        workflowExecutor.reset();
        assertFalse(workflowExecutor.isStarted());
        assertFalse(workflowExecutor.isTerminated());
        workflowExecutor.start();
        workflowExecutor.triggerAction("terminate");
        assertTrue(workflowExecutor.isStarted());
        assertTrue(workflowExecutor.isTerminated());
        // force reset
        workflowExecutor.setDataModel(new MockSCXMLDataModel("scxml"));
        workflowExecutor.start();
        message = workflowExecutor.triggerAction("hello", "Hello world!");
        assertEquals("Hello world!", message);
    }
}
