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
package org.hippoecm.repository.impl;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.standardworkflow.WorkflowEventWorkflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is not part of a public accessible API or extensible interface */
class WorkflowPostActionsBoundMethod implements WorkflowPostActions {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id: ";

    final static Logger log = LoggerFactory.getLogger(WorkflowPostActionsBoundMethod.class);

    WorkflowManagerImpl workflowManager;
    String sourceIdentity;
    boolean isDocumentResult;
    Node wfSubject;
    Node wfNode;
    Set<String> preconditionSet;
    String workflowCategory;
    String workflowMethod;

    WorkflowPostActionsBoundMethod(WorkflowManagerImpl workflowManager, Node wfSubject, boolean isDocumentResult, Node wfNode, String workflowCategory, String workflowMethod) throws RepositoryException {
        this.workflowManager = workflowManager;
        this.sourceIdentity = wfSubject.getIdentifier();
        this.wfSubject = wfSubject;
        this.isDocumentResult = isDocumentResult;
        this.wfNode = wfNode;
        this.workflowCategory = workflowCategory;
        this.workflowMethod = workflowMethod;
        if (wfNode.hasNode("hipposys:eventdocument")) {
            // TODO
        } else if (wfNode.hasProperty("hipposys:eventdocument")) {
            this.wfSubject = wfNode.getProperty("hipposys:eventdocument").getNode();
        }
    }

    public void execute(Object returnObject) {
        try {
            if (wfNode.hasProperty("hipposys:eventconditioncategory")) {
                if (!wfNode.getProperty("hipposys:eventconditioncategory").getString().equals(workflowCategory)) {
                    return;
                }
            }
            if (wfNode.hasProperty("hipposys:eventconditionmethod")) {
                if (!wfNode.getProperty("hipposys:eventconditionmethod").getString().equals(workflowMethod)) {
                    return;
                }
            }
            Workflow workflow = workflowManager.getWorkflowInternal(wfNode, wfSubject);
            if (workflow instanceof WorkflowEventWorkflow) {
                WorkflowEventWorkflow event = (WorkflowEventWorkflow)workflow;
                try {
                    if (isDocumentResult) {
                        event.fire((Document)returnObject);
                    } else {
                        event.fire();
                    }
                } catch (WorkflowException ex) {
                    log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
                } catch (MappingException ex) {
                    log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
                } catch (RemoteException ex) {
                    log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
                }
            }
        } catch (RepositoryException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }
    }

    public void dispose() {
    }
}
