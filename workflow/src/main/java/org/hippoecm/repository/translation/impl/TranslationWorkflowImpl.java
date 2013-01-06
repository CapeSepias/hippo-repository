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
package org.hippoecm.repository.translation.impl;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.hippoecm.repository.HippoStdNodeType;
import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowContext;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.ext.InternalWorkflow;
import org.hippoecm.repository.standardworkflow.CopyWorkflow;
import org.hippoecm.repository.standardworkflow.FolderWorkflow;
import org.hippoecm.repository.translation.HippoTranslatedNode;
import org.hippoecm.repository.translation.HippoTranslationNodeType;
import org.hippoecm.repository.translation.TranslationWorkflow;
import org.hippoecm.repository.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslationWorkflowImpl implements TranslationWorkflow, InternalWorkflow {

    private static final long serialVersionUID = 1L;

    private final Session userSession;
    private final Session rootSession;
    private final WorkflowContext workflowContext;
    private final Node rootSubject;
    private final Node userSubject;

    public TranslationWorkflowImpl(WorkflowContext context, Session userSession, Session rootSession, Node subject)
            throws RemoteException, RepositoryException {
        this.workflowContext = context;
        this.userSession = userSession;
        this.rootSession = rootSession;
        this.userSubject = userSession.getNodeByIdentifier(subject.getIdentifier());
        this.rootSubject = rootSession.getNodeByIdentifier(subject.getIdentifier());

        if (!userSubject.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
            throw new RepositoryException("Node is not of type " + HippoTranslationNodeType.NT_TRANSLATED);
        }
    }

    public Document addTranslation(String language, String name) throws WorkflowException, MappingException,
            RepositoryException, RemoteException {
        HippoTranslatedNode translatedNode = new HippoTranslatedNode(rootSubject);

        Node lclContainingFolder = translatedNode.getContainingFolder();
        if (!lclContainingFolder.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
            throw new WorkflowException("No translated ancestor found");
        }

        HippoTranslatedNode translatedFolder = new HippoTranslatedNode(lclContainingFolder);
        Node folderTranslation = translatedFolder.getTranslation(language);
        Document targetFolder = new Document(folderTranslation.getIdentifier());
        Node copiedDoc = null;
        if (userSubject.getParent().isNodeType(HippoNodeType.NT_HANDLE)) {
            Workflow defaultWorkflow = workflowContext.getWorkflowContext(null).
                    getWorkflow("translation-copy", new Document(rootSubject.getIdentifier()));
            if (defaultWorkflow instanceof CopyWorkflow) {
                ((CopyWorkflow) defaultWorkflow).copy(targetFolder, name);
            } else {
                throw new WorkflowException("Unknown default workflow; cannot copy document");
            }
            NodeIterator siblings = folderTranslation.getNodes(name);
            while (siblings.hasNext()) {
                Node sibling = siblings.nextNode();
                if (sibling.isNodeType(HippoNodeType.NT_HANDLE)) {
                    copiedDoc = sibling;
                }
            }
            if (copiedDoc == null) {
                throw new WorkflowException("Could not locate handle for document after copying");
            }
            copiedDoc = copiedDoc.getNode(copiedDoc.getName());
            JcrUtils.ensureIsCheckedOut(copiedDoc, false);
        } else {
            Workflow internalWorkflow = workflowContext.getWorkflowContext(null).getWorkflow("internal", targetFolder);
            if (!(internalWorkflow instanceof FolderWorkflow)) {
                throw new WorkflowException(
                        "Target folder does not have a folder workflow in the category 'internal'.");
            }
            Map<String, Set<String>> prototypes = (Map<String, Set<String>>) internalWorkflow.hints().get("prototypes");
            if (prototypes == null) {
                throw new WorkflowException("No prototype hints available in workflow of target folder.");
            }

            // find best matching category and type from prototypes
            String primaryType = userSubject.getPrimaryNodeType().getName();
            String category = null;
            String type = null;
            for (Map.Entry<String, Set<String>> candidate : prototypes.entrySet()) {
                String categoryName = candidate.getKey();
                Set<String> types = candidate.getValue();
                if (types.contains(primaryType)) {
                    category = categoryName;
                    type = primaryType;
                    break;
                }
                if (category == null) {
                    category = categoryName;
                }
                if (type == null && types.size() > 0) {
                    type = types.iterator().next();
                }
            }

            if (category != null && type != null) {
                String path = ((FolderWorkflow) internalWorkflow).add(category, type, name);
                copiedDoc = rootSession.getNode(path);
            } else {
                throw new WorkflowException("No category found to use for adding translation to target folder");
            }
            JcrUtils.ensureIsCheckedOut(copiedDoc, false);
            if (!copiedDoc.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
                copiedDoc.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
            }
            copiedDoc.setProperty(HippoTranslationNodeType.ID,
                                  userSubject.getProperty(HippoTranslationNodeType.ID).getString());
        }

        copiedDoc.setProperty(HippoTranslationNodeType.LOCALE, language);
        Document copy = new Document(copiedDoc.getIdentifier());

        rootSession.save();
        rootSession.refresh(false);
        return copy;
    }

    public void addTranslation(String language, Document document) throws WorkflowException, MappingException,
            RepositoryException, RemoteException {
        HippoTranslatedNode translatedNode = new HippoTranslatedNode(rootSubject);
        if (translatedNode.hasTranslation(language)) {
            throw new WorkflowException("Language already exists");
        }

        Node copiedDocNode = rootSession.getNodeByIdentifier(document.getIdentity());
        JcrUtils.ensureIsCheckedOut(copiedDocNode, false);
        if (!copiedDocNode.isNodeType(HippoTranslationNodeType.NT_TRANSLATED)) {
            copiedDocNode.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
        }
        copiedDocNode.setProperty(HippoTranslationNodeType.LOCALE, language);
        copiedDocNode.setProperty(HippoTranslationNodeType.ID, userSubject.getProperty(HippoTranslationNodeType.ID)
                .getString());

        rootSession.save();
        rootSession.refresh(false);
    }

    public Map<String, Serializable> hints() throws WorkflowException, RemoteException, RepositoryException {
        Map<String, Serializable> hints = new TreeMap<String, Serializable>();

        if (rootSubject.isNodeType(HippoStdNodeType.NT_PUBLISHABLE)) {
            String state = rootSubject.getProperty(HippoStdNodeType.HIPPOSTD_STATE).getString();
            if ("draft".equals(state)) {
                hints.put("addTranslation", Boolean.FALSE);
            } else {
                NodeIterator siblings = rootSubject.getParent().getNodes(rootSubject.getName());
                Node unpublished = null;
                Node published = null;
                while (siblings.hasNext()) {
                    Node sibling = siblings.nextNode();
                    if (sibling.isNodeType(HippoStdNodeType.NT_PUBLISHABLE)) {
                        String siblingState = sibling.getProperty(HippoStdNodeType.HIPPOSTD_STATE).getString();
                        if ("unpublished".equals(siblingState)) {
                            unpublished = sibling;
                        } else if ("published".equals(siblingState)) {
                            published = sibling;
                        }
                    }
                }
                if (unpublished != null && published != null) {
                    if ("published".equals(state)) {
                        hints.put("addTranslation", Boolean.FALSE);
                    }
                }
            }
        }

        HippoTranslatedNode translatedNode = new HippoTranslatedNode(rootSubject);
        Set<String> translations;
        try {
            translations = translatedNode.getTranslations();
        } catch (RepositoryException ex) {
            throw new WorkflowException("Exception during searching for available translations", ex);
        }
        
        hints.put("locales", (Serializable) translations);

        Set<String> available = new TreeSet<String>();
        // for all the available translations we pick the highest ancestor of rootSubject of type HippoTranslationNodeType.NT_TRANSLATED,
        // and take all the translations for that node
        Node highestTranslatedNode = translatedNode.getFarthestTranslatedAncestor();
        if (highestTranslatedNode != null) {
            try {
                available = new HippoTranslatedNode(highestTranslatedNode).getTranslations();
            } catch (RepositoryException ex) {
                throw new WorkflowException("Exception during searching for available translations", ex);
            }
        }

        hints.put("available", (Serializable) available);
        hints.put("locale", translatedNode.getLocale());
        return hints;
    }


}