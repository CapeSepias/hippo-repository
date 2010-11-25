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
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.hippoecm.repository.decorating.DecoratorFactory;

/**
 */
public class QueryManagerDecorator extends org.hippoecm.repository.decorating.QueryManagerDecorator {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    public QueryManagerDecorator(DecoratorFactory factory, Session session, QueryManager manager) {
        super(factory, session, manager);
    }

    /**
     * @inheritDoc
     */
    public Query createQuery(String statement, String language) throws InvalidQueryException, RepositoryException {
        statement = QueryDecorator.mangleArguments(statement);
        return super.createQuery(statement, language);
    }
}
