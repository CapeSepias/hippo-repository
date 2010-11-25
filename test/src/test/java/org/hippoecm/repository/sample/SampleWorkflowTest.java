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
package org.hippoecm.repository.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.hippoecm.repository.HippoRepository;
import org.hippoecm.repository.HippoRepositoryFactory;
import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowDescriptor;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.api.WorkflowManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.atomikos.icatch.jta.UserTransactionManager;

public class SampleWorkflowTest {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private HippoRepository server;

    @Before
    public void setUp() throws Exception {
        System.setProperty("com.atomikos.icatch.file", "../src/test/resources/jta.properties");
        server = HippoRepositoryFactory.getHippoRepository();
    }

    @After
    public void tearDown() throws Exception {
        server.close();
    }

    /**
     * Create UserTransActionManger instance
     * @return
     */
    public TransactionManager getTransactionManager() {
        return new UserTransactionManager();
    }

    @Test
    public void testWorkflow() throws RepositoryException, WorkflowException, IOException, Exception {
        SampleWorkflowSetup.commonStart(server);
        try {
            Session session = server.login("admin","admin".toCharArray());

            UserTransaction ut = server.getUserTransaction(getTransactionManager(), session);
            ut.begin();

            Node root = session.getRootNode();
            Node node = root.getNode("files/myarticle");
            assertEquals(node.getProperty("sample:authorId").getLong(), SampleWorkflowSetup.oldAuthorId);
            WorkflowManager manager = ((HippoWorkspace) session.getWorkspace()).getWorkflowManager();
            try {
                Workflow workflow = manager.getWorkflow("mycategory", node);
                assertNotNull(workflow);
                if (workflow instanceof SampleWorkflow) {
                    SampleWorkflow myworkflow = (SampleWorkflow) workflow;
                    myworkflow.renameAuthor("Jan Smit");
                } else {
                    fail("workflow not of proper type " + workflow.getClass().getName());
                }

                ut.commit();
            } catch (Exception ex) {
                System.err.println(ex.getMessage());
                ex.printStackTrace(System.err);
                ut.rollback();
                throw ex;
            }

            session.save();
            session.refresh(false);
            assertEquals(node.getProperty("sample:authorId").getLong(), SampleWorkflowSetup.newAuthorId);

            session.logout();
        } catch (NotSupportedException ex) {
            System.err.println("NotSupportedException: " + ex.getMessage());
            ex.printStackTrace(System.err);
            fail("NotSupportedException: " + ex.getMessage());
        } catch (SystemException ex) {
            System.err.println("SystemException: " + ex.getMessage());
            ex.printStackTrace(System.err);
            fail("SystemException: " + ex.getMessage());
        } finally {
            SampleWorkflowSetup.commonEnd(server);
        }
    }
}
