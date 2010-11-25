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

import javax.jcr.RepositoryException;

import org.hippoecm.repository.security.group.RepositoryGroupManager;
import org.hippoecm.repository.security.user.AbstractUserManager;
import org.hippoecm.repository.security.user.RepositoryUserManager;

public class RepositorySecurityProvider extends AbstractSecurityProvider {

    @SuppressWarnings("unused")
    final static String SVN_ID = "$Id$";


    public void init(SecurityProviderContext context) throws RepositoryException {
        ManagerContext mgrContext;

        mgrContext = new ManagerContext(context.getSession(), context.getProviderPath(), context.getUsersPath(), context.isMaintenanceMode());
        userManager = new RepositoryUserManager();
        ((AbstractUserManager)userManager).init(mgrContext);

        mgrContext = new ManagerContext(context.getSession(), context.getProviderPath(), context.getGroupsPath(), context.isMaintenanceMode());
        groupManager = new RepositoryGroupManager();
        groupManager.init(mgrContext);
    }
}
