/*
 *  Copyright 2009 Hippo.
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
package org.hippoecm.repository.jca;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessControlException;

import javax.jcr.AccessDeniedException;
import javax.jcr.Credentials;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.LoginException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.retention.RetentionManager;
import javax.jcr.security.AccessControlManager;
import javax.jcr.version.VersionException;
import javax.transaction.xa.XAResource;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.hippoecm.repository.api.HippoSession;

/**
 * This class implements the JCA implementation of session.
 */
public final class JCASessionHandle implements HippoSession {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    /**
     * Managed connection.
     */
    private JCAManagedConnection mc;

    /**
     * Construct a new session.
     */
    public JCASessionHandle(JCAManagedConnection mc) {
        this.mc = mc;
    }

    /**
     * Return the managed connection.
     */
    public JCAManagedConnection getManagedConnection() {
        return mc;
    }

    /**
     * Set the managed connection.
     */
    public void setManagedConnection(JCAManagedConnection mc) {
        this.mc = mc;
    }

    /**
     * Return the session.
     */
    private HippoSession getSession() {
        return mc.getSession(this);
    }

    /**
     * Return the repository.
     */
    public Repository getRepository() {
        return getSession().getRepository();
    }

    /**
     * Return the user id.
     */
    public String getUserID() {
        return getSession().getUserID();
    }

    /**
     * Return the attribute.
     */
    public Object getAttribute(String name) {
        return getSession().getAttribute(name);
    }

    /**
     * Return the attribute names.
     */
    public String[] getAttributeNames() {
        return getSession().getAttributeNames();
    }

    /**
     * Return the workspace.
     */
    public Workspace getWorkspace() {
        return getSession().getWorkspace();
    }

    /**
     * Impersonate another user.
     */
    public Session impersonate(Credentials cred)
            throws LoginException, RepositoryException {
        throw new RepositoryException("impersonate(..) not supported in managed environment");
    }

    /**
     * Return the root node.
     */
    public Node getRootNode()
            throws RepositoryException {
        return getSession().getRootNode();
    }

    /**
     * Return node by UUID.
     */
    public Node getNodeByUUID(String uuid)
            throws ItemNotFoundException, RepositoryException {
        return getSession().getNodeByUUID(uuid);
    }

    /**
     * Return the item.
     */
    public Item getItem(String arg0)
            throws PathNotFoundException, RepositoryException {
        return getSession().getItem(arg0);
    }

    /**
     * Return true if item exists.
     */
    public boolean itemExists(String arg0)
            throws RepositoryException {
        return getSession().itemExists(arg0);
    }

    /**
     * Move the item.
     */
    public void move(String arg0, String arg1)
            throws ItemExistsException, PathNotFoundException, VersionException,
            ConstraintViolationException, LockException, RepositoryException {
        getSession().move(arg0, arg1);
    }

    /**
     * Save the session.
     */
    public void save()
            throws AccessDeniedException, ItemExistsException,
            ConstraintViolationException, InvalidItemStateException, VersionException,
            LockException, NoSuchNodeTypeException, RepositoryException {
        getSession().save();
    }

    /**
     * Refresh the session.
     */
    public void refresh(boolean arg0)
            throws RepositoryException {
        getSession().refresh(arg0);
    }

    /**
     * Return true if it has pending changes.
     */
    public boolean hasPendingChanges()
            throws RepositoryException {
        return getSession().hasPendingChanges();
    }

    /**
     * Return the value factory.
     */
    public ValueFactory getValueFactory()
            throws UnsupportedRepositoryOperationException, RepositoryException {
        return getSession().getValueFactory();
    }

    /**
     * Check permission.
     */
    public void checkPermission(String arg0, String arg1)
            throws AccessControlException, RepositoryException {
        getSession().checkPermission(arg0, arg1);
    }

    /**
     * Return the import content handler.
     */
    public ContentHandler getImportContentHandler(String arg0, int arg1)
            throws PathNotFoundException, ConstraintViolationException, VersionException,
            LockException, RepositoryException {
        return getSession().getImportContentHandler(arg0, arg1);
    }

    /**
     * Import XML content.
     */
    public void importXML(String arg0, InputStream arg1, int arg2)
            throws IOException, PathNotFoundException, ItemExistsException,
            ConstraintViolationException, VersionException, InvalidSerializedDataException,
            LockException, RepositoryException {
        getSession().importXML(arg0, arg1, arg2);
    }

    /**
     * Export system view.
     */
    public void exportSystemView(String arg0, ContentHandler arg1, boolean arg2, boolean arg3)
            throws PathNotFoundException, SAXException, RepositoryException {
        getSession().exportSystemView(arg0, arg1, arg2, arg3);
    }

    /**
     * Export system view.
     */
    public void exportSystemView(String arg0, OutputStream arg1, boolean arg2, boolean arg3)
            throws IOException, PathNotFoundException, RepositoryException {
        getSession().exportSystemView(arg0, arg1, arg2, arg3);
    }

    /**
     * Export document view.
     */
    public void exportDocumentView(String arg0, ContentHandler arg1, boolean arg2, boolean arg3)
            throws PathNotFoundException, SAXException, RepositoryException {
        getSession().exportDocumentView(arg0, arg1, arg2, arg3);
    }

    /**
     * Export document view.
     */
    public void exportDocumentView(String arg0, OutputStream arg1, boolean arg2, boolean arg3)
            throws IOException, PathNotFoundException, RepositoryException {
        getSession().exportDocumentView(arg0, arg1, arg2, arg3);
    }

    /**
     * Set namespace prefix.
     */
    public void setNamespacePrefix(String arg0, String arg1)
            throws NamespaceException, RepositoryException {
        getSession().setNamespacePrefix(arg0, arg1);
    }

    /**
     * Return namespace prefixes.
     */
    public String[] getNamespacePrefixes()
            throws RepositoryException {
        return getSession().getNamespacePrefixes();
    }

    /**
     * Return namespace URI.
     */
    public String getNamespaceURI(String arg0)
            throws NamespaceException, RepositoryException {
        return getSession().getNamespaceURI(arg0);
    }

    /**
     * Return namespace prefix.
     */
    public String getNamespacePrefix(String arg0)
            throws NamespaceException, RepositoryException {
        return getSession().getNamespacePrefix(arg0);
    }

    /**
     * Logout the session.
     */
    public void logout() {
        mc.closeHandle(this);
    }

    /**
     * Return true if session is live.
     */
    public boolean isLive() {
        return getSession().isLive();
    }

    /**
     * Add lock token.
     */
    public void addLockToken(String arg0) {
        getSession().addLockToken(arg0);
    }

    /**
     * Return the lock tokens.
     */
    public String[] getLockTokens() {
        return getSession().getLockTokens();
    }

    /**
     * Remove lock token.
     */
    public void removeLockToken(String arg0) {
        getSession().removeLockToken(arg0);
    }

    //---------------------------------------------------------< XASession >--

    /**
     * Returns the XAResource associated with this session.
     *
     * @return XA resource
     */
    public XAResource getXAResource() {
        return getSession().getXAResource();
    }

    public Node copy(Node original, String absPath) throws RepositoryException {
        return getSession().copy(original, absPath);
    }

    public NodeIterator pendingChanges(Node node, String nodeType, boolean prune) throws NamespaceException, NoSuchNodeTypeException, RepositoryException {
        return getSession().pendingChanges(node, nodeType, prune);
    }

    public NodeIterator pendingChanges(Node node, String nodeType) throws NamespaceException, NoSuchNodeTypeException, RepositoryException {
        return getSession().pendingChanges(node, nodeType);
    }

    public NodeIterator pendingChanges() throws RepositoryException {
        return getSession().pendingChanges();
    }

    public void exportDereferencedView(String absPath, OutputStream out, boolean binaryAsLink, boolean noRecurse) throws IOException, PathNotFoundException, RepositoryException {
        getSession().exportDereferencedView(absPath, out, binaryAsLink, noRecurse);
    }

    public void importDereferencedXML(String parentAbsPath, InputStream in, int uuidBehavior, int referenceBehavior, int mergeBehavior) throws IOException, PathNotFoundException, ItemExistsException, ConstraintViolationException, VersionException, InvalidSerializedDataException, LockException, RepositoryException {
        getSession().importDereferencedXML(parentAbsPath, in, uuidBehavior, referenceBehavior, mergeBehavior);
    }

    public ClassLoader getSessionClassLoader() throws RepositoryException {
        return getSession().getSessionClassLoader();
    }

    public Node getNodeByIdentifier(String id) throws ItemNotFoundException, RepositoryException {
        return getSession().getNodeByIdentifier(id);
    }

    public Node getNode(String absPath) throws PathNotFoundException, RepositoryException {
        return getSession().getNode(absPath);
    }

    public Property getProperty(String absPath) throws PathNotFoundException, RepositoryException {
        return getSession().getProperty(absPath);
    }

    public boolean nodeExists(String absPath) throws RepositoryException {
        return getSession().nodeExists(absPath);
    }

    public boolean propertyExists(String absPath) throws RepositoryException {
        return getSession().propertyExists(absPath);
    }

    public void removeItem(String absPath) throws VersionException, LockException, ConstraintViolationException, AccessDeniedException, RepositoryException {
        getSession().removeItem(absPath);
    }

    public boolean hasPermission(String absPath, String actions) throws RepositoryException {
        return getSession().hasPermission(absPath, actions);
    }

    public boolean hasCapability(String methodName, Object target, Object[] arguments) throws RepositoryException {
        return getSession().hasCapability(methodName, target, arguments);
    }

    public AccessControlManager getAccessControlManager() throws UnsupportedRepositoryOperationException, RepositoryException {
        return getSession().getAccessControlManager();
    }

    public RetentionManager getRetentionManager() throws UnsupportedRepositoryOperationException, RepositoryException {
        return getSession().getRetentionManager();
    }
}
