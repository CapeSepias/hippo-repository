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
package org.hippoecm.repository.standardworkflow;

import java.rmi.RemoteException;

import java.util.List;
import java.util.Map;
import javax.jcr.RepositoryException;

import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowException;

public interface RepositoryWorkflow extends Workflow {
    final static String SVN_ID = "$Id$";

    /**
     * Instruct the repository to apply the new node definition overriding the
     * earlier node definition.
     */
    public void updateModel(String prefix, String cnd) throws WorkflowException, MappingException,
            RepositoryException, RemoteException;

    /**
     * Instruct the repository to apply the new node definition overriding the
     * earlier node definition.
     */
    public void updateModel(String prefix, String cnd, String contentUpdater, Object contentUpdaterCargo) throws WorkflowException, MappingException,
            RepositoryException, RemoteException;

    /**
     * Instruct the repository to apply the new node definition overriding the
     * earlier node definition.  The map of changes contains, for each type, a set of operations
     * to apply to nodes of the type.  If a type does not exist yet, it should be in the map with
     * an empty list.
     */
    public void updateModel(final String prefix, final String cnd, final String contentUpdater, Map<String, List<Change>> changes) throws WorkflowException, MappingException, RepositoryException, RemoteException;

    /**
     * create a new namespace
     */
    public void createNamespace(String prefix, String namespace) throws WorkflowException, MappingException,
            RepositoryException, RemoteException;
}
