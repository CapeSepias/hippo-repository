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

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryResult;
import javax.jcr.query.RowIterator;

/**
 */
public class QueryResultDecorator extends AbstractDecorator implements QueryResult {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    protected final QueryResult result;

    protected QueryResultDecorator(DecoratorFactory factory, SessionDecorator session, QueryResult result) {
        super(factory, session);
        this.result = result;
    }

    @Override
    protected void repair(Session upstreamSession) throws RepositoryException {
        throw new RepositoryException("query result is no longer valid");
    }

    /**
     * @inheritDoc
     */
    public String[] getColumnNames() throws RepositoryException {
        check();
        return result.getColumnNames();
    }

    /**
     * @inheritDoc
     */
    public RowIterator getRows() throws RepositoryException {
        check();
        return result.getRows();
    }

    /**
     * @inheritDoc
     */
    public NodeIterator getNodes() throws RepositoryException {
        check();
        NodeIterator nodes = result.getNodes();
        return new NodeIteratorDecorator(factory, session, nodes);
    }

    public String[] getSelectorNames() throws RepositoryException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
