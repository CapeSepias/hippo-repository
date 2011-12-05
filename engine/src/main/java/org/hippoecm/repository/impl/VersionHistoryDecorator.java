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
import javax.jcr.version.VersionHistory;

import org.hippoecm.repository.api.HippoNode;
import org.hippoecm.repository.api.Localized;
import org.hippoecm.repository.decorating.DecoratorFactory;

/**
 */
public class VersionHistoryDecorator extends org.hippoecm.repository.decorating.VersionHistoryDecorator {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    public VersionHistoryDecorator(DecoratorFactory factory, Session session, VersionHistory versionHistory) {
        super(factory, session, versionHistory);
    }

    public Node getCanonicalNode() throws RepositoryException {
        Node canonical = ((SessionDecorator)getSession()).getCanonicalNode(versionHistory);
        if(canonical != null) {
            return factory.getNodeDecorator(session, canonical);
        } else {
            return null;
        }
    }

    public String getLocalizedName() throws RepositoryException {
        return ((HippoNode)versionHistory).getLocalizedName();
    }

    public String getLocalizedName(Localized localized) throws RepositoryException {
        return ((HippoNode)versionHistory).getLocalizedName(localized);
    }
}
