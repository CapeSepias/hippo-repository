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

import java.io.File;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.transaction.NotSupportedException;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.hippoecm.repository.api.InitializationProcessor;
import org.hippoecm.repository.api.ReferenceWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hippoecm.repository.api.RepositoryMap;
import org.hippoecm.repository.api.ValueMap;

public abstract class HippoRepositoryImpl implements HippoRepository {

    protected Repository repository;
    protected final Logger log = LoggerFactory.getLogger(HippoRepositoryImpl.class);

    private String JTSLookupName = "java:comp/env/TransactionManager";
    private boolean initialized = false;

    private void initialize() {
        initialized = true;
    }

    private String workingDirectory;

    protected HippoRepositoryImpl() {
        workingDirectory = new File(System.getProperty("user.dir")).getAbsolutePath();
        initialize();
    }

    protected HippoRepositoryImpl(String workingDirectory) {
        if (workingDirectory == null || workingDirectory.equals("")) {
            throw new NullPointerException();
        }
        this.workingDirectory = new File(workingDirectory).getAbsolutePath();
        initialize();
    }

    /* Implementors of this abstract class should also provide the following
     * static method:
     *     public static HippoRepository create(String location);
     * which may throw any number of exceptions.
     */

    public Repository getRepository() {
        return repository;
    }

    protected String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getLocation() {
        // trick to allow repository users to determine if it is (maybe no longer) initialized or not
        // without having to extend the API
        // REPO-662 will provide a more permanent solution
        return initialized ? workingDirectory : null;
    }

    /**
     * Mimic jcr repository login.
     * @return Session with Anonymous credentials
     * @throws LoginException
     * @throws RepositoryException
     */
    public Session login() throws LoginException, RepositoryException {
        return login(null);
    }

    public Session login(String username, char[] password) throws LoginException, RepositoryException {
        if (username != null && !username.equals("")) {
            // SimpleCredentials give NPE on null as password
            if (password == null) {
                throw new LoginException("Password is null");
            }
            // https://issues.apache.org/jira/browse/JCR-2740
            SimpleCredentials creds = new SimpleCredentials(username, password);
            creds.setAttribute("org.apache.jackrabbit.autoFixCorruptions", "true"); 
            return login(creds, null);
        } else {
            return login(null);
        }
    }

    public Session login(SimpleCredentials credentials, String workspaceName) throws LoginException, RepositoryException {
        if (repository == null) {
            throw new RepositoryException("Repository not initialized yet.");
        }

        // try to login with credentials
        Session session = (Session) repository.login(credentials, workspaceName);
        if (session != null) {
            log.info("Logged in as " + session.getUserID() + " to a "
                    + repository.getDescriptor(Repository.REP_NAME_DESC) + " repository.");
        } else if (credentials == null) {
            log.error("Failed to login to repository with no credentials");
        } else {
            log.error("Failed to login to repository with credentials " + credentials.toString());
        }
        return session;
    }

    public Session login(SimpleCredentials credentials) throws LoginException, RepositoryException {
        return login(credentials, null);
    }

    public void close() {
        HippoRepositoryFactory.unregister(this);
        initialized = false;
    }

    /**
     * Get a UserTransaction from the JTA transaction manager through JNDI
     * @param session
     * @return a new UserTransactionImpl object
     * @throws RepositoryException
     * @throws NotSupportedException
     */
    public UserTransaction getUserTransaction(Session session) throws RepositoryException, NotSupportedException {
        TransactionManager tm = null;
        InitialContext ic;
        try {
            ic = new InitialContext();
            tm = (TransactionManager)ic.lookup(JTSLookupName);
            log.info("Got TransactionManager through JNDI from " + JTSLookupName);
        } catch (NamingException e) {
            log.error("Failed to get TransactionManager", e);
            throw new RepositoryException("Failed to get TransactionManager.");
        }
        return getUserTransaction(tm, session);
    }

    /**
     * Get a UserTransaction from the JTA transaction manager.
     * @param tm the (external) transaction manager
     * @param session
     * @return a new UserTransactionImpl object
     * @throws NotSupportedException when Session is not a XASession
     */
    public UserTransaction getUserTransaction(TransactionManager tm, Session session) throws NotSupportedException {
        UserTransaction ut = new UserTransactionImpl(tm, session);
        return ut;
    }

    public RepositoryMap getRepositoryMap(Node node) throws RepositoryException {
        return new RepositoryMapImpl(node);
    }

    public ValueMap getValueMap(Node node) throws RepositoryException {
        return new RepositoryValueMap(node);
    }

    @Override
    public InitializationProcessor getInitializationProcessor() {
        return null;
    }

    @Override
    public ReferenceWorkspace getOrCreateReferenceWorkspace() throws RepositoryException {
        return null;
    }
}
