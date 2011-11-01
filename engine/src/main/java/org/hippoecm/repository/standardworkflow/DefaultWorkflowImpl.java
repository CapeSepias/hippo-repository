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
package org.hippoecm.repository.standardworkflow;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Locale;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.Localized;
import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.RepositoryMap;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowContext;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.ext.InternalWorkflow;
import org.hippoecm.repository.impl.NodeDecorator;

public class DefaultWorkflowImpl implements DefaultWorkflow, EditableWorkflow, InternalWorkflow {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    private static final long serialVersionUID = 1L;

    WorkflowContext context;
    Document document;
    Session session;
    Session rootSession; // FIXME; having the need for a rootSession is PLAIN WRONG, however because the document manager cannot read its own confiugration because of authorization rules, editors and authors cannot access documents
    Node subject;
    
    public DefaultWorkflowImpl(WorkflowContext context, Session userSession, Session rootSession, Node subject) throws RepositoryException {
        this.context = context;
        document = new Document(subject.getUUID());
        this.session = rootSession; // FIXME SHOULD BE THE USERSESSION!
        this.rootSession = rootSession;
        this.subject = rootSession.getNodeByIdentifier(subject.getIdentifier());
    }

    private WorkflowContext getWorkflowContext() {
        return context;
    }

    public Map<String,Serializable> hints() {
        return null;
    }

    public Document obtainEditableInstance()
            throws WorkflowException, MappingException, RepositoryException, RemoteException {
        return document;
    }

    public Document commitEditableInstance()
            throws WorkflowException, MappingException, RepositoryException, RemoteException {
        return document;
    }

    public Document disposeEditableInstance()
            throws WorkflowException, MappingException, RepositoryException, RemoteException {
        throw new WorkflowException("Document type does not allow for reverting changes");
    }

    public void delete() throws WorkflowException, MappingException, RepositoryException, RemoteException {
        Document folder = getWorkflowContext().getDocument("embedded", document.getIdentity());
        Workflow workflow = getWorkflowContext().getWorkflow(getFolderWorkflowCategory(), folder);
        if(workflow instanceof FolderWorkflow)
            ((FolderWorkflow)workflow).delete(document);
        else
            throw new WorkflowException("cannot delete document which is not contained in a folder");
    }

    private String getFolderWorkflowCategory() {
        String folderWorkflowCategory = "internal";
        RepositoryMap config = getWorkflowContext().getWorkflowConfiguration();
        if (config != null && config.exists() && config.get("folder-workflow-category") instanceof String) {
            folderWorkflowCategory = (String) config.get("folder-workflow-category");
        }
        return folderWorkflowCategory;
    }

    public void archive() throws WorkflowException, MappingException, RepositoryException, RemoteException {
        Document folder = getWorkflowContext().getDocument("embedded", document.getIdentity());
        Workflow workflow = getWorkflowContext().getWorkflow(getFolderWorkflowCategory(), folder);
        if(workflow instanceof FolderWorkflow)
            ((FolderWorkflow)workflow).archive(document);
        else
            throw new WorkflowException("cannot delete document which is not contained in a folder");
    }

    public void rename(String newName) throws WorkflowException, MappingException, RepositoryException, RemoteException {
        Document folder = getWorkflowContext().getDocument("embedded", document.getIdentity());
        Workflow workflow = getWorkflowContext().getWorkflow(getFolderWorkflowCategory(), folder);
        if(workflow instanceof FolderWorkflow)
            ((FolderWorkflow)workflow).rename(document, newName);
        else
            throw new WorkflowException("cannot delete document which is not contained in a folder");
    }

    public void localizeName(Localized localized, String newName) throws WorkflowException, MappingException, RepositoryException, RemoteException {
        Node node;
        if (subject.isNodeType(HippoNodeType.NT_HANDLE)) {
            node = subject;
        } else {
            node = subject.getParent();
            if (!node.isNodeType(HippoNodeType.NT_HANDLE)) {
                node = subject;
            }
        }
        localizeName(node, localized, newName);
    }

    public void localizeName(Locale locale, String newName) throws WorkflowException, MappingException, RepositoryException, RemoteException {
        Node node, handle;
        if (subject.isNodeType(HippoNodeType.NT_HANDLE)) {
            handle = node = subject;
        } else {
            handle = subject.getParent();
            if (handle.isNodeType(HippoNodeType.NT_HANDLE)) {
                node = handle;
            } else {
                handle = null;
                node = subject;
            }
        }
        Localized localized;
        if (handle != null) {
            localized = ((NodeDecorator)subject).getLocalized(locale);
        } else
            localized = Localized.getInstance(locale);
        localizeName(node, localized, newName);
    }

    public void localizeName(String newName) throws WorkflowException, MappingException, RepositoryException, RemoteException {
        Node node, handle;
        if (subject.isNodeType(HippoNodeType.NT_HANDLE)) {
            handle = node = subject;
        } else {
            handle = subject.getParent();
            if (handle.isNodeType(HippoNodeType.NT_HANDLE)) {
                node = handle;
            } else {
                handle = null;
                node = subject;
            }
        }
        Localized localized;
        if (handle != null) {
            localized = ((NodeDecorator)org.hippoecm.repository.decorating.checked.NodeDecorator.unwrap(subject)).getLocalized(null);
            if (localized == null) {
                localized = Localized.getInstance();
            }
        } else
            localized = Localized.getInstance();
        localizeName(node, localized, newName);
    }

    private void localizeName(Node node, Localized localized, String newName) throws RepositoryException {
        // find the existing localName
        Node translationNode = null;
        if (node.isNodeType(HippoNodeType.NT_TRANSLATED)) {
            for (NodeIterator iter = node.getNodes(HippoNodeType.HIPPO_TRANSLATION); iter.hasNext(); ) {
                translationNode = iter.nextNode();
                Localized translationLocalized = Localized.getInstance(translationNode);
                if (localized.equals(translationLocalized)) {
                    break;
                } else {
                    translationNode = null;
                }
            }
        } else {
            checkout(node);
            node.addMixin(HippoNodeType.NT_TRANSLATED);
        }
        if (translationNode == null) {
            checkout(node);
            translationNode = node.addNode(HippoNodeType.HIPPO_TRANSLATION, HippoNodeType.NT_TRANSLATION);
            localized.setTranslation(translationNode);
        } else {
            checkout(translationNode);
        }
        translationNode.setProperty(HippoNodeType.HIPPO_MESSAGE, newName);
        node.save();
    }

    private static void checkout(Node node) throws RepositoryException {
        while (node.getDepth() > 0) {
            if (node.isNodeType("mix:versionable")) {
                if (!node.isCheckedOut()) {
                    node.checkout();
                }
                return;
            }
            node = node.getParent();
        }
    }

    public void copy(Document destination, String newName) throws MappingException, RemoteException, WorkflowException, RepositoryException {
        Document folder = getWorkflowContext().getDocument("embedded", document.getIdentity());
        Workflow workflow = getWorkflowContext().getWorkflow(getFolderWorkflowCategory(), destination);
        if(workflow instanceof EmbedWorkflow)
            ((EmbedWorkflow)workflow).copyTo(folder, document, newName, null);
        else
            throw new WorkflowException("cannot copy document which is not contained in a folder");
    }

    public void move(Document destination, String newName) throws MappingException, RemoteException, WorkflowException, RepositoryException {
        Document folder = getWorkflowContext().getDocument("embedded", document.getIdentity());
        Workflow workflow = getWorkflowContext().getWorkflow(getFolderWorkflowCategory(), folder);
        if(workflow instanceof FolderWorkflow)
            ((FolderWorkflow)workflow).move(document, destination, newName);
        else
            throw new WorkflowException("cannot move document which is not contained in a folder");
    }
}
