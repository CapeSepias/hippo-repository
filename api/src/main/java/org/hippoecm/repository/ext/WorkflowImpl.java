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
package org.hippoecm.repository.ext;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.TreeMap;

import javax.jcr.RepositoryException;

import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowContext;

/**
 * Implementors of a work-flow in the repository must extend from the WorkflowImpl base type.
 */
public abstract class WorkflowImpl implements Remote, Workflow
{

    /**
     * Work-flow context in use, which ought to be not public accessible.  Use getWorkflowContext instead.
     */
    protected WorkflowContext context;

    /**
     * All implementations of a work-flow must provide a single, no-argument constructor.
     * @throws RemoteException mandatory exception that must be thrown by all Remote objects
     */
    public WorkflowImpl() throws RemoteException {
    }

    /**
     * <b>This call is not (yet) part of the API, but under evaluation.</b><p/>
     * @param context the new context that should be used
     */
    final public void setWorkflowContext(WorkflowContext context) {
        this.context = context;
    }

    /**
     * 
     * @return
     */
    final protected WorkflowContext getWorkflowContext() {
        return context;
    }

    /**
     * This is a shorthand for getWorkflowContext().getWorkflowContext(specification)
     * @param specification implementation dependent specification, alternate work-flow context implementations are passed
     * this object in order to pass parameters.  The type of the object also determines which alternative implementation is used.
     * @return a work-flow context with alternate behavior
     * @throws org.hippoecm.repository.api.MappingException when no implementation is available for the specification passed
     * @throws javax.jcr.RepositoryException when a generic error happens
     * @see WorkflowContext
     */
    final protected WorkflowContext getWorkflowContext(Object specification) throws MappingException, RepositoryException {
        return context.getWorkflowContext(specification);
    }

    /**
     * A shorthand method to get an unchained {@link WorkflowContext}
     *
     * @return
     * @throws MappingException
     * @throws RepositoryException
     */
    final protected WorkflowContext getNonChainingWorkflowContext() throws MappingException, RepositoryException {
        return context.getWorkflowContext(null);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,Serializable> hints() {
        return hints(this);
    }

    static Map<String,Serializable> hints(Workflow workflow) {
        Map<String,Serializable> map = new TreeMap<String,Serializable>();
        for(Class cls : workflow.getClass().getInterfaces()) {
            if(Workflow.class.isAssignableFrom(cls)) {
                for(Method method : cls.getDeclaredMethods()) {
                    String methodName = method.getName();
                    if(methodName.equals("hints")) {
                        map.put(methodName, new Boolean(true));
                    }
                }
            }
        }
        return map;
    }
}
