/*
 *  Copyright 2008-2013 Hippo B.V. (http://www.onehippo.com)
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

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.hippoecm.repository.util.JcrUtils;

/**
 * A Plain Old Java Object (POJO) representing a document in a JCR repository.
 * Instances of this object can be returned by workflow calls to indicate to the callee which document has been created or otherwise affected.
 * See {@link DocumentManager} on how to obtain a document instance manually.
 * </p>
 * Workflows returning specific implementation of a document
 * object will notice that the caller of the workflow gets only a simple Document object back, however through the
 * DocumentManager more complex Document based objects may be obtained.  The Document as returned by workflow calls
 * are only useful in subseqent calls to the workflowmanager to return a new workflow, or from a document the
 * getIdentity() method may be used to obtain the UUID of the javax.jcr.Node representing the document.
 */
public class Document extends Object implements Serializable, Cloneable {

    private String identity = null;
    private transient Node node;

    /** 
     * Constructor that should be considered to have a protected signature rather than public and may be used for extending classes to
     * create a new Document.
     */
    public Document() {
    }

    /**
     * <b>This call is not part of the API, in no circumstance should this call be used.</b><p/>
     * Extended classes <b>must</b> honor this constructor!
     * @param node the backing {@link javax.jcr.Node} in the repository that this document instance represents.
     */
    public Document(Node node) throws RepositoryException {
        initialize(node);
    }

    /**
     * <b>This call is not part of the API, in no circumstance should this call be used.</b><p/>
     * TODO DEJDO: this method should be 'protected' such as not to expose the internal backing Node as it is bound
     *             to the WorkflowManager <em>root</em> Session
     * @return the backing Node of this Document
     */
    public Node getNode() {
        return node;
    }

    /**
     * <b>This call is not part of the API, in no circumstance should this call be used.</b><p/>
     * TODO DEJDO: this method should be 'protected' such as not to expose the internal backing Node as it is bound
     *             to the WorkflowManager <em>root</em> Session
     * @return the ensured to be checked out backing Node of this Document
     */
    public Node getCheckedOutNode() throws RepositoryException {
        JcrUtils.ensureIsCheckedOut(node, true);
        return node;
    }

    /**
     * @return true if this document has a backing Node
     */
    protected boolean hasNode() {
        return node != null;
    }

    /**
     * Obtain the identity, if known at this point, of a document.  The
     * identity of a Document is the identity of the primary {@link javax.jcr.Node}
     * used in persisting the data of the document.</p>
     * A Document returned for example by a workflow step can be accessed
     * using:
     * <pre>Node node = session.getNodeByUUID(document.getIdentity());</pre>
     *
     * @return a string containing the UUID of the Node representing the Document.
     * or <code>null</code> if not available.
     */
    public final String getIdentity() {
        return identity;
    }

    /**
     * <b>This call is not part of the API, in no circumstance should this call be used.</b><p/>
     * @param uuid the UUID of the backing {@link javax.jcr.Node} this document instance represents
     */
    public final void setIdentity(String uuid) {
        identity = uuid;
        node = null;
    }

    /**
     * <b>This call is not part of the API, in no circumstance should this call be used.</b><p/>
     * Extended classes which need custom/extra initialization based on the backing Node should
     * use the {@link #initialized()} method to get wired into the initialization chain.
     * @param node the backing {@link javax.jcr.Node} in the repository that this document instance represents.
     */
    public final void initialize(Node node) throws RepositoryException {
        this.node = node;
        this.identity = node.getIdentifier();
        initialized();
    }

    /**
     * Extended classes which need custom/extra initialization based on the backing Node can
     * use this method which will get called after {@link #initialize(javax.jcr.Node)} has been called.
     */
    protected void initialized() {}

    protected String getNodeStringProperty(String relPath) throws RepositoryException {
        return hasNode() ? JcrUtils.getStringProperty(getNode(), relPath, null) : null;
    }

    protected void setNodeStringProperty(String relPath, String value) throws RepositoryException {
        if (hasNode()) {
            getCheckedOutNode().setProperty(relPath, value);
        }
    }

    protected void setNodeNodeProperty(String relPath, Node nodeValue) throws RepositoryException {
        if (hasNode()) {
            getCheckedOutNode().setProperty(relPath, nodeValue);
        }
    }

    protected String[] getNodeStringsProperty(String relPath) throws RepositoryException {
        String[] result = null;
        if (hasNode() && getNode().hasProperty(relPath)) {
            Value[] values = getNode().getProperty(relPath).getValues();
            result = new String[values.length];
            int i = 0;
            for (Value v : values) {
                result[i++] = v.getString();
            }
        }
        return result;
    }

    protected void setNodeStringsProperty(String relPath, String[] values) throws RepositoryException {
        if (hasNode()) {
            getCheckedOutNode().setProperty(relPath, values);
        }
    }

    protected Date getNodeDateProperty(String relPath) throws RepositoryException {
        Calendar cal = null;
        if (hasNode()) {
            cal = JcrUtils.getDateProperty(getNode(), relPath, null);
        }
        return cal != null ? cal.getTime() : null;
    }

    protected void setNodeDateProperty(String relPath, Date date) throws RepositoryException {
        if (hasNode()) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            getCheckedOutNode().setProperty(relPath, cal);
        }
    }

    // TODO DEJDO: consider adding more common get|setNode<type>Property methods

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getClass().getName());
        sb.append("[");
        if (identity != null) {
            sb.append("uuid=");
            sb.append(identity);
        } else {
            sb.append("new");
        }
        sb.append("]");
        return new String(sb);
    }
}
