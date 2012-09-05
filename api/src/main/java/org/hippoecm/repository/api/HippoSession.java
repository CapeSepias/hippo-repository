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
package org.hippoecm.repository.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;
import javax.transaction.xa.XAResource;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * An extension of a plain {@link javax.jcr.Session} based session.  Any session as obtained from the Hippo Repository 2 can be cased to
 * a HippoSession allowing access to the extensions to the JCR API.
 */
public interface HippoSession extends Session {

    /**
     * Convenience function to copy a node to a destination path in the same workspace.  Unlike the copy method in the javax.jcr.Workspace class,
     * this copy method does not immediately persist (i.e. requires a save operation on the session, or ancestor node of the destination path) and it does
     * return the produced copy.  The latter makes this the preferred method to copy a node as the method on the Workspace makes it impossible to know for
     * sure which node is produced in case of same-name siblings.  It also provides a save method for copying node types that are extensions of the Hippo repository.
     * @param srcNode the node to copy
     * @param destAbsNodePath the absolute path of the to be created target
     * node which will be a copy of srcNode
     * @return the resulting copy
     * @throws RepositoryException a generic error while accessing the repository
     */
    public Node copy(Node srcNode, String destAbsNodePath) throws RepositoryException;

    /**
     * Obtains an iterator over the set of nodes that potentially contain
     * changes, starting (and not including) the indicated node.
     * Only nodes for which <code>Node.isNodeType(nodeType)</code> returns
     * true are included in the resulting set.  If the prune boolean value is
     * true, then the nodes matching in the hierarchy first are returned.  If
     * matching modified node exists beneath the nodes, these are not
     * included.
     *
     * @param node The starting node for which to look for changes, will not
     *             be included in result, may be null to indicate to search whole tree
     * @param nodeType Only nodes that are (derived) of this nodeType are
     *                 included in the result, may be null to indicate no filtering on nodeType
     * @param prune Wheter only to return the first matching modified node in
     *              a subtree (true), or provide a depth search for all modified
     *              nodes (false)
     * @throws NamespaceException an invalid nodeType was passed
     * @throws RepositoryException a generic error while accessing the repository
     * @throws NoSuchNodeTypeException an invalid nodeType was passed
     * @return A NodeIterator instance which iterates over all modified
     *         nodes, not including the passed node
     */
    public NodeIterator pendingChanges(Node node, String nodeType, boolean prune) throws NamespaceException,
                                                                           NoSuchNodeTypeException, RepositoryException;

    /** Conveniance method for
     * <code>pendingChanges(node,nodeType,false)</code>
     *
     * @param node The starting node for which to look for changes, will not
     *             be included in result, may be null to indicate to search whole tree
     * @param nodeType Only nodes that are (derived) of this nodeType are
     *                 included in the result, may be null to indicate no filtering on nodeType
     * @throws NamespaceException an invalid nodeType was passed
     * @throws RepositoryException a generic error while accessing the repository
     * @throws NoSuchNodeTypeException an invalid nodeType was passed
     * @return A NodeIterator instance which iterates over all modified
     *         nodes, not including the passed node
     * @see #pendingChanges(Node,String,boolean)
     */
    public NodeIterator pendingChanges(Node node, String nodeType) throws NamespaceException, NoSuchNodeTypeException,
                                                                          RepositoryException;


    /** Largely a conveniance method for
     * <code>pendingChanges(Session.getRootNode(), "nt:base", false)</code> however
     * will also return the root node if modified.
     *
     * @return A NodeIterator instance which iterates over all modified nodes, including the root
     * @throws RepositoryException 
     * @see #pendingChanges(Node,String,boolean)
     */
    public NodeIterator pendingChanges() throws RepositoryException;

    /**
     * Export a dereferenced view of a node.
     *
     * @param absPath the absolute path to the subtree to be exported
     * @param out the output stream to which the resulting XML should be outputted
     * @param binaryAsLink whether to include binaries
     * @param noRecurse whether to output just a single node or the whole subtree
     * @throws IOException in case of an error writing to the output stream
     * @throws RepositoryException a generic error while accessing the repository
     * @throws PathNotFoundException in case the absPath parameter does not point to a valid node
     * @see javax.jcr.Session#exportSystemView(String,OutputStream,boolean,boolean)
     */
    public void exportDereferencedView(String absPath, OutputStream out, boolean binaryAsLink, boolean noRecurse)
            throws IOException, PathNotFoundException, RepositoryException;

    /**
     * Export a dereferenced view of a node.
     *
     * @param absPath the absolute path to the subtree to be exported
     * @param contentHandler The  <code>org.xml.sax.ContentHandler</code> to
     *                       which the SAX events representing the XML serialization of the subgraph
     *                       will be output.
     * @param binaryAsLink whether to include binaries
     * @param noRecurse whether to output just a single node or the whole subtree
     * @throws IOException in case of an error writing to the output stream
     * @throws RepositoryException a generic error while accessing the repository
     * @throws PathNotFoundException in case the absPath parameter does not point to a valid node
     * @see javax.jcr.Session#exportSystemView(String,OutputStream,boolean,boolean)
     */
    public void exportDereferencedView(String absPath, ContentHandler contentHandler, boolean binaryAsLink, boolean noRecurse)
            throws PathNotFoundException, SAXException, RepositoryException;

    /**
     * <b>This call is not (yet) part of the API, but under evaluation.</b>
     * Import a dereferenced export.
     * @param parentAbsPath the parent node below which to in
     * @param in the input stream from which to read the XML
     * @param uuidBehavior how to handle deserialized UUIDs in the input stream {@link javax.jcr.ImportUUIDBehavior}
     * @param mergeBehavior an options flag containing one of the values of {@link ImportMergeBehavior} indicating how to merge nodes that already exist
     * @param referenceBehavior an options flag containing one of the values of {@link ImportReferenceBehavior} indicating how to handle references
     * @throws IOException if incoming stream is not a valid XML document.
     * @throws PathNotFoundException in case the parentAbsPath parameter does not point to a valid node
     * @throws ItemExistsException in case the to be imported node already exist below the parent and same-name siblings are not allowed, or when the merge behavior does not allow merging on an existing node and the node does exist
     * @throws ConstraintViolationException when imported node is marked protected accoring to the node definition of the parent
     * @throws InvalidSerializedDataException 
     * @throws VersionException when the parent node is not in checked-out status
     * @throws LockException when the parent node is locked
     * @throws RepositoryException a generic error while accessing the repository
     * @see #exportDereferencedView(String,OutputStream,boolean,boolean)
     * @see javax.jcr.Session#importXML(java.lang.String, java.io.InputStream, int)
     * @see org.hippoecm.repository.api.ImportReferenceBehavior
     * @see org.hippoecm.repository.api.ImportMergeBehavior
     */
    public void importDereferencedXML(String parentAbsPath, InputStream in, int uuidBehavior, int referenceBehavior,
            int mergeBehavior) throws IOException, PathNotFoundException, ItemExistsException,
            ConstraintViolationException, VersionException, InvalidSerializedDataException, LockException,
            RepositoryException;

    /**
     * Retrieves an {@link XAResource} object that the transaction manager
     * will use to manage this XASession object's participation in
     * a distributed transaction.
     *
     * @return the {@link XAResource} object.
     */
    public XAResource getXAResource();

    /**
     * <b>This call is not (yet) part of the API, but under evaluation.</b>
     * Probably it will change into getSessionClassLoader(Node) or similar.
     * Get a classloader which uses the JCR  repository to load the classes from.
     * @return a classloader instance that will load class definitions stored in the JCR repository
     * @throws RepositoryException  a generic error while accessing the repository
     */
    public ClassLoader getSessionClassLoader() throws RepositoryException;
    
    /**
     * <b>DO NOT USE THIS METHOD.  This call is not yet part of the API.</b><br/>
     * This registers a callback at a JCR session, which will be called when the
     * session is being logged out.  Either by an explicit call of the user
     * program to the logout method or due to other reasons.  It can be used by
     * user modules to clean up state.
     * The session is only available if isLive() returns true, which is not
     * guaranteed (though is guaranteed in case of a normal logout procedure.
     * @param callback an object implementing the CloseCallback interface
     * that will be informed when a session is closed.
     */
    public void registerSessionCloseCallback(CloseCallback callback);
    
    /**
     * <b>DO NOT USE THIS METHOD.  This call is not yet part of the API.</b><br/>
     * The interface of the callback handler that is called when the session is
     * logged out.
     */
    interface CloseCallback {
        /** Called upon notification of the session is being logged out.
         * No runtime exception may be thrown.
         */
        public void close();
    }
}
