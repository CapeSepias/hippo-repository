/**
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Collections;

import javax.jcr.RepositoryException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.commons.scxml2.Context;
import org.apache.commons.scxml2.ErrorReporter;
import org.apache.commons.scxml2.Evaluator;
import org.apache.commons.scxml2.EventDispatcher;
import org.apache.commons.scxml2.SCInstance;
import org.apache.commons.scxml2.SCXMLExpressionException;
import org.apache.commons.scxml2.TriggerEvent;
import org.apache.commons.scxml2.env.SimpleDispatcher;
import org.apache.commons.scxml2.env.SimpleErrorReporter;
import org.apache.commons.scxml2.env.jexl.JexlContext;
import org.apache.commons.scxml2.env.jexl.JexlEvaluator;
import org.apache.commons.scxml2.model.ModelException;
import org.apache.commons.scxml2.model.OnEntry;
import org.apache.commons.scxml2.model.State;
import org.easymock.EasyMock;
import org.hippoecm.repository.api.WorkflowException;
import org.junit.Before;
import org.junit.Test;

/**
 * AbstractActionTest
 */
public class AbstractActionTest {

    private EventDispatcher evtDispatcher;
    private ErrorReporter errRep;
    private SCInstance scInstance;
    private Log appLog;
    private Collection<TriggerEvent> derivedEvents;

    private State state;
    private OnEntry onEntry;
    private Context context;

    private AbstractAction action;

    @Before
    public void before() throws Exception {
        evtDispatcher = new SimpleDispatcher();
        errRep = new SimpleErrorReporter();
        appLog = new SimpleLog(getClass().getName());
        derivedEvents = Collections.emptyList();

        state = new State();
        onEntry = new OnEntry();
        state.setOnEntry(onEntry);

        context = new JexlContext();
        Evaluator evaluator = new JexlEvaluator();
        scInstance = EasyMock.createNiceMock(SCInstance.class);
        EasyMock.expect(scInstance.getContext(state)).andReturn(context).anyTimes();
        EasyMock.expect(scInstance.getEvaluator()).andReturn(evaluator).anyTimes();
        EasyMock.replay(scInstance);
    }

    @Test
    public void testActionSetup() throws Exception {
        action = new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            protected void doExecute(EventDispatcher evtDispatcher, ErrorReporter errRep, Log appLog,
                    Collection<TriggerEvent> derivedEvents) throws ModelException, SCXMLExpressionException,
                    WorkflowException, RepositoryException {
                
            }
        };
        action.setParent(onEntry);

        assertSame(onEntry, action.getParent());
        assertSame(state, action.getParentTransitionTarget());
    }

    @Test
    public void testAbstractActionParametersAndEvaluation() throws Exception {
        action = new ParametersTestingAction();
        action.setParent(onEntry);

        assertNull(((ParametersTestingAction) action).getOneProperty());
        ((ParametersTestingAction) action).setOneProperty("Just a value");
        assertEquals("Just a value", ((ParametersTestingAction) action).getOneProperty());

        assertNull(((ParametersTestingAction) action).getOneExpression());
        ((ParametersTestingAction) action).setOneExpression("1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10");
        assertEquals("1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10", ((ParametersTestingAction) action).getOneExpression());

        action.execute(evtDispatcher, errRep, scInstance, appLog, derivedEvents);
    }

    private class ParametersTestingAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public String getOneProperty() {
            return getParameter("oneProperty");
        }

        public void setOneProperty(String oneProperty) {
            setParameter("oneProperty", oneProperty);
        }

        public String getOneExpression() {
            return getParameter("oneExpression");
        }

        public void setOneExpression(String oneExpression) {
            setParameter("oneExpression", oneExpression);
        }

        @Override
        protected void goImmutable() {
            setParameter("stringparam", "astring");
            setParameter("booleanparam", "true");
        }

        @Override
        protected void doExecute(EventDispatcher evtDispatcher, ErrorReporter errRep, Log appLog,
                Collection<TriggerEvent> derivedEvents) throws ModelException, SCXMLExpressionException,
                WorkflowException, RepositoryException {

            assertSame(context, getContext());
            assertNull(getParameter("unknownparam"));

            assertEquals(4, getParameters().size());

            assertEquals("astring", getParameter("stringparam"));
            assertEquals("true", getParameter("booleanparam"));

            try {
                setParameter("wrongattempt", "goodtry");
                fail("Shouldn't be allowed to set parameters in #doExecute() operation.");
            } catch (UnsupportedOperationException e) {
                // as expected; in #doExecute() method, the parameters are already immutable.
                // you have to set parameters in either the property setter methods invoked by the SCXML executor
                // or #goImmutable() method.
            }

            assertEquals(55, eval(getParameter("oneExpression")));
        }
    }
}
