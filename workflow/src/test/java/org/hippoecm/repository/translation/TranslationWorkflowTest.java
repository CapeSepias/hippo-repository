/*
 *  Copyright 2010-2013 Hippo B.V. (http://www.onehippo.com)
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
package org.hippoecm.repository.translation;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;

import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowManager;
import org.hippoecm.repository.standardworkflow.FolderWorkflow;
import org.hippoecm.repository.util.JcrUtils;
import org.junit.Before;
import org.junit.Test;
import org.onehippo.repository.testutils.RepositoryTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TranslationWorkflowTest extends RepositoryTestCase {
    private static final String INVALID_ID = "invalid id - to be overwritten by folderworkflow";

    
    static final String FOLDER_T9N_ID = "700a09f1-eac5-482c-a09e-ec0a6a5d6abc";
    static final String DOCUMENT_T9N_ID = "dbe51269-1211-4695-9dd4-1d6ea578f134";
    
    String[] content = {
        "/test/folder", "hippostd:folder",
            "jcr:mixinTypes", "hippo:harddocument",
        "/test/folder/document", "hippo:handle",
            "jcr:mixinTypes", "hippo:hardhandle",
        "/test/folder/document/document", "hippo:testdocument",
            "hippostd:state", "unpublished",
            "hippostd:holder", "admin",
        "/test/folder_nl", "hippostd:folder",
            "jcr:mixinTypes", "hippo:harddocument",
        "/test/hipposysedit:prototype", "hippo:testdocument",
            "hippostd:state", "draft",
            "hippostd:holder", "admin",
    };

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        session.getRootNode().addNode("test");
        session.save();

        build(session, content);

        for (String category : new String[] { "translation", "embedded", "translation-copy", "translation-internal" }) {
            Node workflowsNode = session.getRootNode().getNode("hippo:configuration/hippo:workflows/" + category);
            for (NodeIterator handlers = workflowsNode.getNodes(); handlers.hasNext();) {
                Node wfNode = handlers.nextNode();
                if (wfNode.hasProperty(HippoNodeType.HIPPO_PRIVILEGES)) {
                    wfNode.getProperty(HippoNodeType.HIPPO_PRIVILEGES).remove();
                }
            }
        }
        Node newDocTemplateQuery = session.getRootNode().getNode("hippo:configuration/hippo:queries/hippo:templates/new-document");
        newDocTemplateQuery.setProperty("jcr:statement", "/jcr:root/test/hipposysedit:prototype");
        Node prototype = session.getNode("/test/hipposysedit:prototype");
        prototype.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
        prototype.setProperty(HippoTranslationNodeType.LOCALE, "en");
        prototype.setProperty(HippoTranslationNodeType.ID, INVALID_ID);

        Node document = session.getRootNode().getNode("test/folder/document/document");
        document.addMixin("hippo:harddocument");
        document.addNode(HippoTranslationNodeType.TRANSLATIONS, HippoTranslationNodeType.TRANSLATIONS);

        Node folder = session.getRootNode().getNode("test/folder");
        folder.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
        folder.setProperty(HippoTranslationNodeType.LOCALE, "en");
        folder.setProperty(HippoTranslationNodeType.ID, FOLDER_T9N_ID);
        folder.addNode(HippoTranslationNodeType.TRANSLATIONS, HippoTranslationNodeType.TRANSLATIONS);

        Node folderNl = session.getRootNode().getNode("test/folder_nl");
        folderNl.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
        folderNl.setProperty(HippoTranslationNodeType.LOCALE, "nl");
        folderNl.setProperty(HippoTranslationNodeType.ID, FOLDER_T9N_ID);

        session.save();
        session.refresh(false);
    }

    @Test
    public void testTranslateDocumentInTranslatedFolder() throws Exception {
        WorkflowManager manager = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
        Node handle = session.getRootNode().getNode("test/folder/document");
        Node document = handle.getNode(handle.getName());
        document.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
        document.setProperty(HippoTranslationNodeType.ID, DOCUMENT_T9N_ID);
        document.setProperty(HippoTranslationNodeType.LOCALE, "en");
        session.save();
        session.refresh(false);

        Workflow workflowInterface = manager.getWorkflow("translation", document);
        assertTrue(workflowInterface instanceof TranslationWorkflow);
        TranslationWorkflow workflow = (TranslationWorkflow) workflowInterface;
        Map<String, Serializable> hints = workflow.hints();
        assertTrue(hints.containsKey("available"));
        Set<String> languages = (Set<String>) hints.get("available");
        assertTrue(languages.contains("nl"));

        workflow.addTranslation("nl", "dokument");

        session.refresh(false);
        assertTrue(session.nodeExists("/test/folder_nl/dokument"));
        assertTrue(session.nodeExists("/test/folder_nl/dokument/dokument"));
    }

    @Test
    public void testTranslateFolder() throws Exception {
        Node deFolder = session.getRootNode().getNode("test").addNode("folder_de", "hippostd:folder");
        deFolder.addMixin(HippoNodeType.NT_HARDDOCUMENT);
        session.save();
        session.refresh(false);

        WorkflowManager manager = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
        Workflow workflowInterface = manager.getWorkflow("translation", session.getRootNode().getNode("test/folder"));
        assertTrue(workflowInterface instanceof TranslationWorkflow);
        TranslationWorkflow workflow = (TranslationWorkflow) workflowInterface;
        workflow.addTranslation("de", new Document(deFolder));

        session.refresh(false);
        assertEquals(FOLDER_T9N_ID, session.getProperty("/test/folder_de/" + HippoTranslationNodeType.ID).getString());
    }

    @Test
    public void testTranslatedFolderIsEmpty() throws Exception {
        Node subFolder = session.getRootNode().getNode("test/folder").addNode("subfolder", "hippostd:folder");
        subFolder.addMixin(HippoNodeType.NT_HARDDOCUMENT);
        subFolder.addMixin(HippoTranslationNodeType.NT_TRANSLATED);
        String id = UUID.randomUUID().toString();
        subFolder.setProperty(HippoTranslationNodeType.ID, id);
        subFolder.setProperty(HippoTranslationNodeType.LOCALE, "en");

        JcrUtils.copy(session.getNode("/test/folder/document"), "document", session.getNode("/test/folder/subfolder"));

        session.save();
        session.refresh(false);

        WorkflowManager manager = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
        Workflow workflowInterface = manager.getWorkflow("translation", session.getRootNode().getNode("test/folder/subfolder"));
        assertTrue(workflowInterface instanceof TranslationWorkflow);
        TranslationWorkflow workflow = (TranslationWorkflow) workflowInterface;
        workflow.addTranslation("nl", "ondermap");

        session.refresh(false);
        assertTrue(session.nodeExists("/test/folder_nl/ondermap"));
        assertEquals(id, session.getNode("/test/folder_nl/ondermap").getProperty(HippoTranslationNodeType.ID).getString());
        assertFalse(session.nodeExists("/test/folder_nl/ondermap/document"));
    }

    @Test
    public void testFolderWorkflowCreatesNewDocumentThatInheritsLocale() throws Exception {
        WorkflowManager manager = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
        Workflow workflowInterface = manager.getWorkflow("internal", session.getRootNode().getNode("test/folder"));
        assertTrue(workflowInterface instanceof FolderWorkflow);
        FolderWorkflow fw = (FolderWorkflow) workflowInterface;
        fw.add("new-document", "hippo:testdocument", "test-document");
        session.refresh(true);

        assertTrue(session.nodeExists("/test/folder/test-document/test-document"));
        Node docNode = session.getNode("/test/folder/test-document/test-document");
        assertTrue(docNode.isNodeType(HippoTranslationNodeType.NT_TRANSLATED));
        assertEquals("en", docNode.getProperty(HippoTranslationNodeType.LOCALE).getString());
        assertFalse(INVALID_ID.equals(docNode.getProperty(HippoTranslationNodeType.ID).getString()));
    }
}
