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
package org.hippoecm.repository.jackrabbit;

import java.security.AccessControlException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.jcr.version.VersionException;
import javax.security.auth.Subject;

import org.apache.jackrabbit.core.HierarchyManager;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.nodetype.NodeTypeConflictException;
import org.apache.jackrabbit.core.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.security.AccessManager;
import org.apache.jackrabbit.core.security.AnonymousPrincipal;
import org.apache.jackrabbit.core.security.SystemPrincipal;
import org.apache.jackrabbit.core.security.UserPrincipal;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.NoSuchItemStateException;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.SessionItemStateManager;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.util.XMLChar;
import org.hippoecm.repository.dataprovider.HippoNodeId;
import org.hippoecm.repository.dataprovider.MirrorNodeId;
import org.hippoecm.repository.decorating.NodeDecorator;
import org.hippoecm.repository.jackrabbit.xml.DereferencedImportHandler;
import org.hippoecm.repository.jackrabbit.xml.DereferencedSessionImporter;
import org.hippoecm.repository.security.principals.AdminPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

abstract class SessionImplHelper {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private static Logger log = LoggerFactory.getLogger(SessionImpl.class);

    /**
     * the user ID that was used to acquire this session
     */
    private String userId;

    NodeTypeManagerImpl ntMgr;
    RepositoryImpl rep;
    Subject subject;
    org.apache.jackrabbit.core.SessionImpl sessionImpl;

    /**
     * Local namespace mappings. Prefixes as keys and namespace URIs as values.
     * <p>
     * This map is only accessed from synchronized methods (see
     * <a href="https://issues.apache.org/jira/browse/JCR-1793">JCR-1793</a>).
     */
    private final ConcurrentMap<String, String> namespaces = new ConcurrentHashMap<String, String>();

    /**
     * Clears the local namespace mappings. Subclasses that for example
     * want to participate in a session pools should remember to call
     * <code>super.logout()</code> when overriding this method to avoid
     * namespace mappings to be carried over to a new session.
     */
    public void logout() {
        namespaces.clear();
    }

    //------------------------------------------------< Namespace handling >--

    /**
     * Returns the namespace prefix mapped to the given URI. The mapping is
     * added to the set of session-local namespace mappings unless it already
     * exists there.
     * <p>
     * This behaviour is based on JSR 283 (JCR 2.0), but remains backwards
     * compatible with JCR 1.0.
     *
     * @param uri namespace URI
     * @return namespace prefix
     * @throws NamespaceException if the namespace is not found
     * @throws RepositoryException if a repository error occurs
     */
    public String getNamespacePrefix(String uri)
            throws NamespaceException, RepositoryException {
        Iterator<Map.Entry<String, String>> iterator = namespaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (entry.getValue().equals(uri)) {
                return entry.getKey();
            }
        }

        // The following throws an exception if the URI is not found, that's OK
        String prefix = sessionImpl.getWorkspace().getNamespaceRegistry().getPrefix(uri);

        // Generate a new prefix if the global mapping is already taken
        String base = prefix;
        for (int i = 2; namespaces.containsKey(prefix); i++) {
            prefix = base + i;
        }

        namespaces.put(prefix, uri);
        return prefix;
    }

    /**
     * Returns the namespace URI mapped to the given prefix. The mapping is
     * added to the set of session-local namespace mappings unless it already
     * exists there.
     * <p>
     * This behaviour is based on JSR 283 (JCR 2.0), but remains backwards
     * compatible with JCR 1.0.
     *
     * @param prefix namespace prefix
     * @return namespace URI
     * @throws NamespaceException if the namespace is not found
     * @throws RepositoryException if a repository error occurs
     */
    public String getNamespaceURI(String prefix)
            throws NamespaceException, RepositoryException {
        String uri = (String) namespaces.get(prefix);

        if (uri == null) {
            // Not in local mappings, try the global ones
            uri = sessionImpl.getWorkspace().getNamespaceRegistry().getURI(prefix);
            if (namespaces.containsValue(uri)) {
                // The global URI is locally mapped to some other prefix,
                // so there are no mappings for this prefix
                throw new NamespaceException("Namespace not found: " + prefix);
            }
            // Add the mapping to the local set, we already know that
            // the prefix is not taken
            namespaces.put(prefix, uri);
        }

        return uri;
    }

    /**
     * Returns the prefixes of all known namespace mappings. All global
     * mappings not already included in the local set of namespace mappings
     * are added there.
     * <p>
     * This behaviour is based on JSR 283 (JCR 2.0), but remains backwards
     * compatible with JCR 1.0.
     *
     * @return namespace prefixes
     * @throws RepositoryException if a repository error occurs
     */
    public String[] getNamespacePrefixes()
            throws RepositoryException {
        NamespaceRegistry registry = sessionImpl.getWorkspace().getNamespaceRegistry();
        String[] uris = registry.getURIs();
        for (int i = 0; i < uris.length; i++) {
            getNamespacePrefix(uris[i]);
        }

        return (String[])
            namespaces.keySet().toArray(new String[namespaces.size()]);
    }

    /**
     * Modifies the session local namespace mappings to contain the given
     * prefix to URI mapping.
     * <p>
     * This behaviour is based on JSR 283 (JCR 2.0), but remains backwards
     * compatible with JCR 1.0.
     *
     * @param prefix namespace prefix
     * @param uri namespace URI
     * @throws NamespaceException if the mapping is illegal
     * @throws RepositoryException if a repository error occurs
     */
    public void setNamespacePrefix(String prefix, String uri)
            throws NamespaceException, RepositoryException {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix must not be null");
        } else if (uri == null) {
            throw new IllegalArgumentException("Namespace must not be null");
        } else if (prefix.length() == 0) {
            throw new NamespaceException(
                    "Empty prefix is reserved and can not be remapped");
        } else if (uri.length() == 0) {
            throw new NamespaceException(
                    "Default namespace is reserved and can not be remapped");
        } else if (prefix.toLowerCase().startsWith("xml")) {
            throw new NamespaceException(
                    "XML prefixes are reserved: " + prefix);
        } else if (!XMLChar.isValidNCName(prefix)) {
            throw new NamespaceException(
                    "Prefix is not a valid XML NCName: " + prefix);
        }

        // FIXME Figure out how this should be handled
        // Currently JSR 283 does not specify this exception, but for
        // compatibility with JCR 1.0 TCK it probably should.
        // Note that the solution here also affects the remove() code below
        String previous = (String) namespaces.get(prefix);
        if (previous != null && !previous.equals(uri)) {
            throw new NamespaceException("Namespace already mapped");
        }

        namespaces.remove(prefix);
        Iterator<Map.Entry<String, String>> iterator = namespaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (entry.getValue().equals(uri)) {
                iterator.remove();
            }
        }

        namespaces.put(prefix, uri);
    }

    private HashSet<Privilege> jcrPrivileges = new HashSet<Privilege>();
    
    SessionImplHelper(org.apache.jackrabbit.core.SessionImpl sessionImpl,
                      NodeTypeManagerImpl ntMgr, RepositoryImpl rep, Subject subject) throws RepositoryException {
        this.sessionImpl = sessionImpl;
        this.ntMgr = ntMgr;
        this.rep = rep;
        this.subject = subject;
        setUserId();
        AccessControlManager acMgr = sessionImpl.getAccessControlManager();
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_ADD_CHILD_NODES));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_LIFECYCLE_MANAGEMENT));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_LOCK_MANAGEMENT));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_MODIFY_ACCESS_CONTROL));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_MODIFY_PROPERTIES));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_NODE_TYPE_MANAGEMENT));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_READ));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_READ_ACCESS_CONTROL));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_REMOVE_CHILD_NODES));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_REMOVE_NODE));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_RETENTION_MANAGEMENT));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_VERSION_MANAGEMENT));
        jcrPrivileges.add(acMgr.privilegeFromName(Privilege.JCR_WRITE));
    }

    /**
     * Override jackrabbits default userid, because it just uses
     * the first principal it can find, which can lead to strange "usernames"
     */
    protected void setUserId() {
        if (!subject.getPrincipals(SystemPrincipal.class).isEmpty()) {
            Principal principal = subject.getPrincipals(SystemPrincipal.class).iterator().next();
            userId = principal.getName();
        } else if (!subject.getPrincipals(org.apache.jackrabbit.core.security.principal.AdminPrincipal.class).isEmpty()) {
            Principal principal = subject.getPrincipals(org.apache.jackrabbit.core.security.principal.AdminPrincipal.class).iterator().next();
            userId = principal.getName();
        } else if (!subject.getPrincipals(AdminPrincipal.class).isEmpty()) {
            Principal principal = subject.getPrincipals(AdminPrincipal.class).iterator().next();
            userId = principal.getName();
        } else if (!subject.getPrincipals(UserPrincipal.class).isEmpty()) {
            Principal principal = subject.getPrincipals(UserPrincipal.class).iterator().next();
            userId = principal.getName();
        } else if (!subject.getPrincipals(AnonymousPrincipal.class).isEmpty()) {
            Principal principal = subject.getPrincipals(AnonymousPrincipal.class).iterator().next();
            userId = principal.getName();
        } else {
            userId = "Unknown";
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getUserID() {
        return userId;
    }

    /**
     * Method to expose the authenticated users' principals
     * @return Set An unmodifialble set containing the principals
     */
    public Set<Principal> getUserPrincipals() {
        return Collections.unmodifiableSet(subject.getPrincipals());
    }

    /**
     * Before this method the JackRabbiit Session.checkPermission is called.
     * That function checks the validity of absPath and the default JCR permissions:
     * read, remove, add_node and set_property. So we don't have to check for those
     * things again here.
     * @param absPath
     * @param actions
     * @throws AccessControlException
     * @throws RepositoryException
     */
    public void checkPermission(String absPath, String actions) throws AccessControlException, RepositoryException {
        AccessControlManager acMgr = sessionImpl.getAccessControlManager();

        // build the set of actions to be checked
        HashSet<Privilege> privileges = new HashSet<Privilege>();
        for (String action : actions.split(",")) {
            privileges.add(acMgr.privilegeFromName(action));
        }
        privileges.removeAll(jcrPrivileges);
        if (privileges.size() > 0) {
            if (!acMgr.hasPrivileges(absPath, privileges.toArray(new Privilege[privileges.size()]))) {
                throw new AccessControlException("Privileges '" + actions + "' denied for " + absPath);
            }
        }
    }

    abstract SessionItemStateManager getItemStateManager();

    public NodeIterator pendingChanges(Node node, String nodeType, boolean prune)
        throws NamespaceException, NoSuchNodeTypeException, RepositoryException {
        Name ntName;
        try {
            ntName = (nodeType!=null ? sessionImpl.getQName(nodeType) : null);
        } catch (IllegalNameException ex) {
            throw new NoSuchNodeTypeException(nodeType);
        }
        final Set<NodeId> filteredResults = new HashSet<NodeId>();
        if (node==null) {
            node = sessionImpl.getRootNode();
            if (node.isModified()&&(nodeType==null||node.isNodeType(nodeType))) {
                filteredResults.add(((org.apache.jackrabbit.core.NodeImpl)node).getNodeId());
            }
        }
        NodeId nodeId = ((org.apache.jackrabbit.core.NodeImpl)NodeDecorator.unwrap(node)).getNodeId();

        Iterator iter = getItemStateManager().getDescendantTransientItemStates(nodeId);
        while (iter.hasNext()) {
            ItemState itemState = (ItemState)iter.next();
            NodeState state = null;
            if (!itemState.isNode()) {
                try {
                    if (filteredResults.contains(itemState.getParentId()))
                        continue;
                    state = (NodeState)getItemStateManager().getItemState(itemState.getParentId());
                } catch (NoSuchItemStateException ex) {
                    log.error("Cannot find parent of changed property", ex);
                    continue;
                } catch (ItemStateException ex) {
                    log.error("Cannot find parent of changed property", ex);
                    continue;
                }
            } else {
                state = (NodeState)itemState;
            }

            /* if the node type of the current node state is not of required
             * type (if set), continue with next.
             */
            if (nodeType!=null) {
                if (!ntName.equals(state.getNodeTypeName())) {
                    Set mixins = state.getMixinTypeNames();
                    if (!mixins.contains(ntName)) {
                        // build effective node type of mixins & primary type
                        NodeTypeRegistry ntReg = ntMgr.getNodeTypeRegistry();
                        try {
                            if (!ntReg.getEffectiveNodeType(state.getNodeTypeName(),mixins).includesNodeType(ntName)) {
                                continue;
                            }
                        } catch (NodeTypeConflictException ntce) {
                            String msg = "internal error: failed to build effective node type";
                            log.debug(msg);
                            throw new RepositoryException(msg, ntce);
                        }
                    }
                }
            }

            /* if pruning, check that there are already children in the
             * current list.  If so, remove them.
             */
            if (prune) {
                HierarchyManager hierMgr = sessionImpl.getHierarchyManager();
                for (Iterator<NodeId> i = filteredResults.iterator(); i.hasNext();) {
                    if (hierMgr.isAncestor(state.getNodeId(), i.next()))
                        i.remove();
                }
            }

            filteredResults.add(state.getNodeId());
        }

        return new NodeIterator() {
            private final org.apache.jackrabbit.core.ItemManager itemMgr = sessionImpl.getItemManager();
            private final Iterator<NodeId> iterator = filteredResults.iterator();
            private int pos = 0;

            public Node nextNode() {
                return (Node)next();
            }

            public long getPosition() {
                return pos;
            }

            public long getSize() {
                return -1;
            }

            public void skip(long skipNum) {
                if (skipNum<0) {
                    throw new IllegalArgumentException("skipNum must not be negative");
                } else if (skipNum==0) {
                    return;
                } else {
                    do {
                        NodeId id = iterator.next();
                        ++pos;
                    } while (--skipNum>0);
                }
            }

            public boolean hasNext() {
                return iterator.hasNext();
            }

            public Object next() {
                try {
                    NodeId id = iterator.next();
                    ++pos;
                    return itemMgr.getItem(id);
                } catch (AccessDeniedException ex) {
                    return null;
                } catch (ItemNotFoundException ex) {
                    return null;
                } catch (RepositoryException ex) {
                    return null;
                }
            }

            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    public ContentHandler getDereferencedImportContentHandler(String parentAbsPath, int uuidBehavior,
            int referenceBehavior, int mergeBehavior) throws PathNotFoundException, ConstraintViolationException,
            VersionException, LockException, RepositoryException {

        // check sanity of this session
        if (!sessionImpl.isLive()) {
            throw new RepositoryException("this session has been closed");
        }

        NodeImpl parent;
        try {
            Path p = sessionImpl.getQPath(parentAbsPath).getNormalizedPath();
            if (!p.isAbsolute()) {
                throw new RepositoryException("not an absolute path: " + parentAbsPath);
            }
            parent = sessionImpl.getItemManager().getNode(p);
        } catch (NameException e) {
            String msg = parentAbsPath + ": invalid path";
            log.debug(msg);
            throw new RepositoryException(msg, e);
        } catch (AccessDeniedException ade) {
            throw new PathNotFoundException(parentAbsPath);
        }

        // verify that parent node is checked-out
        if (!parent.isNew()) {
            NodeImpl node = parent;
            while (node.getDepth() != 0) {
                if (node.hasProperty(NameConstants.JCR_ISCHECKEDOUT)) {
                    if (!node.getProperty(NameConstants.JCR_ISCHECKEDOUT).getBoolean()) {
                        String msg = parentAbsPath + ": cannot add a child to a checked-in node";
                        log.debug(msg);
                        throw new VersionException(msg);
                    }
                }
                node = (NodeImpl) node.getParent();
            }
        }


        // check protected flag of parent node
        if (parent.getDefinition().isProtected()) {
            String msg = parentAbsPath + ": cannot add a child to a protected node";
            log.debug(msg);
            throw new ConstraintViolationException(msg);
        }

        // check lock status
        if (!parent.isNew()) {
            sessionImpl.getLockManager().checkLock(parent);
        }

        DereferencedSessionImporter importer = new DereferencedSessionImporter(parent, sessionImpl, uuidBehavior, referenceBehavior, mergeBehavior);
        return new DereferencedImportHandler(importer, sessionImpl, rep.getNamespaceRegistry());
    }

    public Node getCanonicalNode(NodeImpl node) throws RepositoryException {
        NodeId nodeId = node.getNodeId();
        if(nodeId instanceof HippoNodeId) {
            if(nodeId instanceof MirrorNodeId) {
                NodeId upstream = ((MirrorNodeId)nodeId).getCanonicalId();
                try {
                    return sessionImpl.getNodeById(upstream);
                } catch(ItemNotFoundException ex) {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return node;
        }
    }
}
