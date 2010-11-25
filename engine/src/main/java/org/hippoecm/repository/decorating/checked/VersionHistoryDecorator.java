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

import javax.jcr.AccessDeniedException;
import javax.jcr.NodeIterator;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionException;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.hippoecm.repository.api.HippoNode;

/**
 */
public class VersionHistoryDecorator extends NodeDecorator implements VersionHistory {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    protected final VersionHistory versionHistory;

    protected VersionHistoryDecorator(DecoratorFactory factory, SessionDecorator session, VersionHistory versionHistory) {
        super(factory, session, (HippoNode)versionHistory);
        this.versionHistory = versionHistory;
    }

    /**
     * @inheritDoc
     */
    public String getVersionableUUID() throws RepositoryException {
        check();
        return versionHistory.getVersionableUUID();
    }

    /**
     * @inheritDoc
     */
    public Version getRootVersion() throws RepositoryException {
        check();
        Version version = versionHistory.getRootVersion();
        return factory.getVersionDecorator(session, version);
    }

    /**
     * @inheritDoc
     */
    public VersionIterator getAllVersions() throws RepositoryException {
        check();
        return new VersionIteratorDecorator(factory, session, versionHistory.getAllVersions());
    }

    /**
     * @inheritDoc
     */
    public Version getVersion(String versionName) throws VersionException, RepositoryException {
        check();
        Version version = versionHistory.getVersion(versionName);
        return factory.getVersionDecorator(session, version);
    }

    /**
     * @inheritDoc
     */
    public Version getVersionByLabel(String label) throws RepositoryException {
        check();
        Version version = versionHistory.getVersionByLabel(label);
        return factory.getVersionDecorator(session, version);
    }

    /**
     * @inheritDoc
     */
    public void addVersionLabel(String versionName, String label, boolean moveLabel) throws VersionException,
            RepositoryException {
        check();
        versionHistory.addVersionLabel(versionName, label, moveLabel);
    }

    /**
     * @inheritDoc
     */
    public void removeVersionLabel(String label) throws VersionException, RepositoryException {
        check();
        versionHistory.removeVersionLabel(label);
    }

    /**
     * @inheritDoc
     */
    public boolean hasVersionLabel(String label) throws RepositoryException {
        check();
        return versionHistory.hasVersionLabel(label);
    }

    /**
     * @inheritDoc
     */
    public boolean hasVersionLabel(Version version, String label) throws VersionException, RepositoryException {
        check();
        return versionHistory.hasVersionLabel(VersionDecorator.unwrap(version), label);
    }

    /**
     * @inheritDoc
     */
    public String[] getVersionLabels() throws RepositoryException {
        check();
        return versionHistory.getVersionLabels();
    }

    /**
     * @inheritDoc
     */
    public String[] getVersionLabels(Version version) throws VersionException, RepositoryException {
        check();
        return versionHistory.getVersionLabels(VersionDecorator.unwrap(version));
    }

    /**
     * @inheritDoc
     */
    public void removeVersion(String versionName) throws ReferentialIntegrityException, AccessDeniedException,
            UnsupportedRepositoryOperationException, VersionException, RepositoryException {
        check();
        versionHistory.removeVersion(versionName);
    }

    public String getVersionableIdentifier() throws RepositoryException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public VersionIterator getAllLinearVersions() throws RepositoryException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public NodeIterator getAllLinearFrozenNodes() throws RepositoryException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public NodeIterator getAllFrozenNodes() throws RepositoryException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
