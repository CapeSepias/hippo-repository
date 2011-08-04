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
package org.hippoecm.repository.security.user;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.hippoecm.repository.PasswordHelper;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.security.ManagerContext;

/**
 * UserManager backend that stores the users inside the JCR repository
 */
public class RepositoryUserManager extends AbstractUserManager {

    /** SVN id placeholder */
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private boolean maintenanceMode = false;
    
    private long passwordMaxAge = -1l;
    
    public void initManager(ManagerContext context) throws RepositoryException {
        initialized = true;
        maintenanceMode = context.isMaintenanceMode();
        Node securityNode = session.getRootNode().getNode(HippoNodeType.CONFIGURATION_PATH).getNode(HippoNodeType.SECURITY_PATH);
        if (securityNode.hasProperty(HippoNodeType.HIPPO_PASSWORDMAXAGE)) {
            passwordMaxAge = securityNode.getProperty(HippoNodeType.HIPPO_PASSWORDMAXAGE).getLong();
        }
    }

    @Override
    public boolean isActive(String rawUserId) throws RepositoryException {
        return super.isActive(rawUserId) && !isPasswordExpired(rawUserId);
    }
    
    private boolean isPasswordExpired(String rawUserId) throws RepositoryException {
        if (passwordMaxAge > 0) {
            Node user = getUser(rawUserId);
            if (user.hasProperty(HippoNodeType.HIPPO_PASSWORDLASTMODIFIED)) {
                long passwordLastModified = user.getProperty(HippoNodeType.HIPPO_PASSWORDLASTMODIFIED).getLong();
                return passwordLastModified + passwordMaxAge < System.currentTimeMillis();
            }
        }
        return false;
    }

    /**
     * Authenticate the user against the hash stored in the user node
     */
    public boolean authenticate(SimpleCredentials creds) throws RepositoryException {
        if (!isInitialized()) {
            throw new IllegalStateException("Not initialized.");
        }
        // check if user exists
        if (!hasUser(creds.getUserID())) {
            return false;
        }
        try {
            char[] password = creds.getPassword();
            Node userinfo = getUser(creds.getUserID());
            
            // check for pre-authenticated user
            if (password != null && password.length > 0 && userinfo.hasProperty(HippoNodeType.HIPPO_PASSKEY) &&
                userinfo.getProperty(HippoNodeType.HIPPO_PASSKEY).getString().equals(new String(password))) {
                return true;
            }

            if (maintenanceMode) {
                return true;
            }

            // do regular password check
            return PasswordHelper.checkHash(password, getPasswordHash(userinfo));
        } catch (NoSuchAlgorithmException e) {
            throw new RepositoryException("Unknown algorithm found when authenticating user: " + creds.getUserID(), e);
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException("Unsupported encoding found when authenticating user: " + creds.getUserID(), e);
        }
    }

    public String getNodeType() {
        return HippoNodeType.NT_USER;
    }

    public boolean isCaseSensitive() {
        return true;
    }

    /**
     * The backend is the repository, no need to sync anything.
     */
    public void syncUserInfo(String userId) {
        return;
    }

    /**
     * Get the (optionally) hashed password of the user
     * @return the password hash
     * @throws RepositoryException
     */
    private String getPasswordHash(Node user) throws RepositoryException {
        if (user.hasProperty(HippoNodeType.HIPPO_PASSWORD)) {
            return user.getProperty(HippoNodeType.HIPPO_PASSWORD).getString();
        } else {
            return null;
        }
    }

    public boolean isAutoSave() {
        return false;
    }
}
