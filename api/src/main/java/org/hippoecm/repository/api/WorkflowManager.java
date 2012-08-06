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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * The work-flow manager is a service associated with a JCR session which provides access to a work-flow associated with a document stored
 * in the repository.  A work-flow is an implementation that can perform some transformation on the document.
 * This implementation is represented as a Java interface, while the possible work-flow steps are methods of this interface.
 * Because multiple work-flows may be active, a category is used to uniquely identify a specific work-flow.
 * Although a work-flow manager is associated with a session, the actual transformation is commonly used by a new session, to obtain
 * higher credentials.  The internal implementation of the repository provides these credentials which are only accessible by the repository itself,
 * and have the same permissions as the system user (effectively permissions to do everything).
 *
 * </p>
 * The work-flow manager is obtained by casting a {@link javax.jcr.Workspace} to a {@link HippoWorkspace}, where the {@link HippoWorkspace#getWorkflowManager()} method
 * returns the work-flow manager for the session.  The {@link javax.jcr.Workspace} itself is obtained though the {@link javax.jcr.Session#getWorkspace()}.
 *
 * From the work-flow manager, you might directly obtain access to a work-flow, or obtain a reference (a WorkflowDescriptor) to the
 * work-flow, which is a cheaper operation.  This WorkflowDescriptor allows some querying to introspect the work-flow available.
 * The work-flow descriptor may be used to obtain the actual work-flow again from the work-flow manager.
 * The methods to obtain either the work-flow directly or a descriptor allow you to access a work-flow based on a Document object or on
 * a {@link javax.jcr.Node}.  In the latter case, the indicated node should be of a hippo:document (or derived) node type or of a hippo:request
 * (or derived) node type.  There are some internal-only exceptions, such as the root node, but in all cases the node must be
 * referenceable and contain no pending changes in the current session.
 */
public interface WorkflowManager {

    /**
     * Workflow Managers are associated with an authenticated session, even though most of the operations executed by workflows
     * are executed from a seperated (unaccessible) session with higher credentials.
     * @return the session which with this workflow manager object is associated with
     * @throws javax.jcr.RepositoryException
     */
    public Session getSession() throws RepositoryException;

    /**
     * Obtains a reference to the workflow for the document as indicated by the javax.jcr.Node in the specified category.
     * @param category category in which to look for the workflow
     * @param item the document or request for which to obtain the workflow
     * @return
     * @throws javax.jcr.RepositoryException
     */
    public WorkflowDescriptor getWorkflowDescriptor(String category, Node item) throws RepositoryException;

    /**
     * Obtains a reference to the workflow for the document as indicated Document instance in the specified category.
     * @param category category in which to look for the workflow
     * @param document the document for which to obtain the workflow
     * @return
     * @throws javax.jcr.RepositoryException
     */
    public WorkflowDescriptor getWorkflowDescriptor(String category, Document document) throws RepositoryException;

    /**
     * Obtains the workflow for the document as indicated by the javax.jcr.Node instance in the specified category.
     * @param category category in which to look for the workflow
     * @param item
     * @return the document or request for which to obtain the workflow
     * @throws org.hippoecm.repository.api.MappingException
     * @throws javax.jcr.RepositoryException
     */
    public Workflow getWorkflow(String category, Node item) throws MappingException, RepositoryException;

    /**
     * Obtains the workflow for the document as indicated Document instance in the specified category.
     * @param category category in which to look for the workflow
     * @param document the document for which to obtain the workflow
     * @return
     * @throws org.hippoecm.repository.api.MappingException
     * @throws javax.jcr.RepositoryException
     */
    public Workflow getWorkflow(String category, Document document) throws MappingException, RepositoryException;

    /**
     * Obtains the workflow for the previously requested workflow descriptor within the same session.
     * @param descriptor the workflow descriptor obtained from the same workflow manager earlier
     * @return the workflow associated with the document and category as descriped in the descriptor
     * @throws org.hippoecm.repository.api.MappingException
     * @throws javax.jcr.RepositoryException
     */
    public Workflow getWorkflow(WorkflowDescriptor descriptor) throws MappingException, RepositoryException;

    /**
     * Obtains an alternative workflow context, which has special behaviour depending on the specification parameter passed.
     * Any workflow obtained though an alternative workflow manager (see {@link #getWorkflow}) will be subject to the alternate rules
     * as indicated by the specification.  For example, the specification may indicate that any workflow step invocation must
     * not be performed immediately, but on a specific date.
     * @param specification implementation dependent specification, alternate workflow context implementations are passed
     * this object in order to pass parameters.  The type of the object also determins which alternative implementation is used.
     * @return a workflow context with alternate behaviour
     * @throws org.hippoecm.repository.api.MappingException when no implementation is available for the specificaiton passed
     * @throws javax.jcr.RepositoryException when a generic error happens
     */
    public WorkflowManager getContextWorkflowManager(Object specification) throws MappingException, RepositoryException;
}

