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
package org.hippoecm.repository.impl;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.jackrabbit.api.XASession;

import org.hippoecm.repository.decorating.DecoratorFactory;

public class DecoratorFactoryImpl extends org.hippoecm.repository.decorating.DecoratorFactoryImpl implements DecoratorFactory {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private final static Logger log = LoggerFactory.getLogger(DecoratorFactory.class);

    SessionDecorator decoratedSession;
    WorkspaceDecorator decoratedWorkspace;

    public DecoratorFactoryImpl() {
        this.decoratedSession = null;
    }

    private DecoratorFactoryImpl(SessionDecorator session) {
        this.decoratedSession = session;
    }

    @Override
    public Repository getRepositoryDecorator(Repository repository) {
        return new RepositoryDecorator(this, repository);
    }

    public static SessionDecorator getSessionDecorator(Session session) {
        DecoratorFactoryImpl newFactory = new DecoratorFactoryImpl();
        SessionDecorator decoratedSession = null;
        RepositoryDecorator decoratedRepository = new RepositoryDecorator(newFactory, session.getRepository());
        if (session instanceof XASession) {
            try {
                decoratedSession = new SessionDecorator(newFactory, decoratedRepository, (XASession)session);
            } catch (RepositoryException ex) {
                log.error("cannot compose transactional session, reverting to regular session");
                // fall through
            }
        }
        if (decoratedSession == null) {
            decoratedSession = new SessionDecorator(newFactory, decoratedRepository, session);
        }
        newFactory.decoratedSession = decoratedSession;
        return decoratedSession;
    }

    public Session getSessionDecorator(Repository repository, Session session) {
        return session;
    }

    public Workspace getWorkspaceDecorator(Session session, Workspace workspace) {
        if (decoratedWorkspace == null) {
            decoratedWorkspace = new WorkspaceDecorator(this, session, workspace);
        }
        return decoratedWorkspace;
    }

    public Node getBasicNodeDecorator(Session session, Node node) {
        return new NodeDecorator(this, session, node);
    }

    public Version getVersionDecorator(Session session, Version version) {
        return new VersionDecorator(this, session, version);
    }

    public VersionHistory getVersionHistoryDecorator(Session session, VersionHistory versionHistory) {
        return new VersionHistoryDecorator(this, session, versionHistory);
    }

    public Query getQueryDecorator(Session session, Query query) {
        return new QueryDecorator(this, session, query);
    }

    public QueryResult getQueryResultDecorator(Session session, QueryResult result) {
        return new QueryResultDecorator(this, decoratedSession, result);
    }

    @Override
    public Query getQueryDecorator(Session session, Query query, Node node) {
        return new QueryDecorator(this, session, query, node);
    }

    @Override
    public QueryManager getQueryManagerDecorator(Session session,
                                                 QueryManager queryManager) {
        return new QueryManagerDecorator(this, session, queryManager);
    }
}
