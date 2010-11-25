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
package org.hippoecm.repository.security.group;

import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.security.ManagerContext;

/**
 * The GroupManager that stores the groups in the JCR Repository
 */
public class RepositoryGroupManager extends AbstractGroupManager {

    /** SVN id placeholder */
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    public void initManager(ManagerContext context) throws RepositoryException {
        initialized = true;
    }

    /**
     * The backend is the repository, so just return the current memberships
     */
    public Set<String> backendGetMemberships(Node user) throws RepositoryException {
        return getMemberships(user.getName());
    }

    public String getNodeType() {
        return HippoNodeType.NT_GROUP;
    }

    public boolean isCaseSensitive() {
        return true;
    }
}
