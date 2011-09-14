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
package org.hippoecm.repository.security;

import org.apache.jackrabbit.api.security.user.UserManager;
import org.hippoecm.repository.security.group.DummyGroupManager;
import org.hippoecm.repository.security.group.GroupManager;
import org.hippoecm.repository.security.role.DummyRoleManager;
import org.hippoecm.repository.security.role.RoleManager;
import org.hippoecm.repository.security.user.DummyUserManager;

public abstract class AbstractSecurityProvider implements SecurityProvider {

    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    protected UserManager userManager = new DummyUserManager();
    protected GroupManager groupManager = new DummyGroupManager();
    protected RoleManager roleManager = new DummyRoleManager();

    /**
     * {@inheritDoc}
     */
    public void sync() {
        // can be overridden for synchronization with backend
    }

    /**
     * {@inheritDoc}
     */
    public void remove() {
        // shutdown hook for things like listeners
    }

    /**
     * {@inheritDoc}
     */
    public UserManager getUserManager() {
        return userManager;
    }

    /**
     * {@inheritDoc}
     */
    public GroupManager getGroupManager() {
        return groupManager;
    }

    /**
     * {@inheritDoc}
     */
    public RoleManager getRoleManager() {
        return roleManager;
    }

}
