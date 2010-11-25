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
package org.hippoecm.repository.decorating.checked;

import javax.jcr.Credentials;
import javax.jcr.Item;
import javax.jcr.ItemVisitor;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import javax.jcr.Workspace;
import javax.jcr.lock.Lock;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import org.hippoecm.repository.api.DocumentManager;
import org.hippoecm.repository.api.HierarchyResolver;
import org.hippoecm.repository.api.WorkflowManager;

/**
 * Factory interface for creating decorator instances. The decorator
 * classes create new decorator instances using a factory to make it
 * easier to customize the behaviour of a decorator layer.
 */
public interface DecoratorFactory {
    @SuppressWarnings("unused")
    final static String SVN_ID = "$Id$";

    /**
     * Creates a repository decorator.
     *
     * @param repository the underlying repository instance
     * @return decorator for the given repository
     */
    Repository getRepositoryDecorator(Repository repository);

    /**
     * Creates a session decorator. The created session decorator will
     * return the given repository (decorator) instance from the
     * {@link Session#getRepository() getRepository()} method to avoid
     * breaking the decorator layer.
     * <p>
     * The following example code illustrates how this method should be
     * used to implement the repository login methods.
     * <pre>
     *     DecoratorFactory factory = ...; // The decorator factory
     *     Session session = ...;          // The underlying session instance
     *     return factory.getSessionDecorator(this, session);
     * </pre>
     *
     * @param repository the repository (decorator) instance used to create
     *                   the session decorator
     * @param session    the underlying session instance
     * @return decorator for the given session
     */
    Session getSessionDecorator(Repository repository, Session session, Credentials credentials, String workspaceName);

    /**
     * Creates a workspace decorator.
     *
     * @param session   the session (decorator) instance used to create the
     *                  workspace decorator
     * @param workspace the underlying workspace instance
     * @return workspace decorator
     */
    Workspace getWorkspaceDecorator(SessionDecorator session, Workspace workspace);

    /**
     * Creates a node decorator.
     * <p/>
     * Note: this method must also take care to create appropriate decorators
     * for subtypes of node: Version and VersionHistory!
     *
     * @param session the session (decorator) instance used to create the
     *                node decorator
     * @param node    the underlying node instance
     * @return node decorator
     */
    Node getNodeDecorator(SessionDecorator session, Node node);

    /**
     * Creates a property decorator.
     *
     * @param session  the session (decorator) instance used to create the
     *                 property decorator
     * @param property the underlying property instance
     * @return property decorator
     */
    Property getPropertyDecorator(SessionDecorator session, Property property);

    /**
     * Creates a property decorator.
     *
     * @param session  the session (decorator) instance used to create the
     *                 property decorator
     * @param property the underlying property instance
     * @param parent   the parent in which the property is located
     * @return property decorator
     */
    Property getPropertyDecorator(SessionDecorator session, Property property, NodeDecorator parent);

    /**
     * Creates an item decorator.
     *
     * @param session the session (decorator) instance used to create the
     *                item decorator
     * @param item    the underlying item instance
     * @return item decorator
     */
    Item getItemDecorator(SessionDecorator session, Item item);

    /**
     * Creates a lock decorator.
     *
     * @param session the session (decorator) instance used to create the
     *                lock decorator
     * @param lock    the underlying lock instance
     * @return lock decorator
     */
    Lock getLockDecorator(SessionDecorator session, Lock lock);

    /**
     * Creates a version decorator.
     *
     * @param session the session (decorator) instance used to create the version
     *                decorator
     * @param version the underlying version instance
     * @return version decorator
     */
    Version getVersionDecorator(SessionDecorator session, Version version);

    /**
     * Creates a version history decorator.
     *
     * @param session        the session (decorator) instance used to create the
     *                       version history decorator.
     * @param versionHistory the underlying version history instance
     * @return version history decorator
     */
    VersionHistory getVersionHistoryDecorator(SessionDecorator session, VersionHistory versionHistory);

    /**
     * Creates a query manager decorator.
     *
     * @param session      the session (decorator) instance used to create the
     *                     query manager decorator.
     * @param queryManager the underlying query manager instance.
     * @return query manager decorator.
     */
    QueryManager getQueryManagerDecorator(SessionDecorator session, QueryManager queryManager);

    /**
     * Creates a query decorator.
     *
     * @param session the session (decorator) instance used to create the query
     *                decorator.
     * @param query   the underlying query instance.
     * @return query decorator.
     */
    Query getQueryDecorator(SessionDecorator session, Query query);

    /**
     * Creates a query decorator.
     *
     * @param session the session (decorator) instance used to create the query
     *                decorator.
     * @param query   the underlying query instance.
     * @return query decorator.
     */
    Query getQueryDecorator(SessionDecorator session, Query query, Node node);

    /**
     * Creates a query result decorator.
     *
     * @param session the session (decorator) instance used to create the query
     *                result decorator.
     * @param result  the underlying query result instance.
     * @return query result decorator.
     */
    QueryResult getQueryResultDecorator(SessionDecorator session, QueryResult result);

    /**
     * Creates a value factory decorator.
     *
     * @param session      the session (decorator) instance used to create the
     *                     value factory decorator.
     * @param valueFactory the underlying value factory instance.
     * @return value factory decorator.
     */
    ValueFactory getValueFactoryDecorator(SessionDecorator session, ValueFactory valueFactory);

    /**
     * Creates a item visitor decorator.
     *
     * @param session the session (decorator) instance used to create the item
     *                visitor decorator.
     * @param visitor the underlying item visitor instance.
     * @return item visitor decorator.
     */
    ItemVisitor getItemVisitorDecorator(SessionDecorator session, ItemVisitor visitor);

    DocumentManager getDocumentManagerDecorator(SessionDecorator session, DocumentManager documentMgr);

    WorkflowManager getWorkflowManagerDecorator(SessionDecorator session, WorkflowManager workflowMgr);

    HierarchyResolver getHierarchyResolverDecorator(SessionDecorator session, HierarchyResolver hierarchyResolver);
}
