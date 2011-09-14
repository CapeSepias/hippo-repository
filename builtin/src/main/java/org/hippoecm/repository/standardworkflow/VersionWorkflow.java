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

import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import java.rmi.RemoteException;
import javax.jcr.RepositoryException;

import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowException;

public interface VersionWorkflow extends Workflow {
    static final String SVN_ID = "$Id$";

    public Document version()
      throws WorkflowException, MappingException, RepositoryException, RemoteException;

    public Document revert(Calendar historic)
      throws WorkflowException, MappingException, RepositoryException, RemoteException;

    /**
     * Restore a historic version by putting its contents in the target document.
     * Can only be used when the workflow is used on an nt:version Node (a {@link Version}).
     * 
     * @param target the Document representation of the target node
     * @return the updated target node
     */
    public Document restoreTo(Document target)
      throws WorkflowException, MappingException, RepositoryException, RemoteException;

    public Document restore(Calendar historic)
      throws WorkflowException, MappingException, RepositoryException, RemoteException;

    public Document restore(Calendar historic, Map<String, String[]> replacements)
      throws WorkflowException, MappingException, RepositoryException, RemoteException;

    public SortedMap<Calendar,Set<String>> list()
      throws WorkflowException, MappingException, RepositoryException, RemoteException;

    public Document retrieve(Calendar historic)
      throws WorkflowException, MappingException, RepositoryException, RemoteException;
}
