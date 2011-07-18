/*
 *  Copyright 2011 Hippo.
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
package org.onehippo.cms7.repository.wfdropbox;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.MappingException;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowDescriptor;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.api.WorkflowManager;
import org.hippoecm.repository.ext.DaemonModule;

public class DropboxDaemonModule extends Thread implements DaemonModule {
    private final static String SVN_ID = "$Id$";

    private static Logger log = LoggerFactory.getLogger(DropboxDaemonModule.class);
    protected Session session;
    protected WorkflowManager workflowManager;
    private volatile boolean shutdown;
    private long initialDelay = 5000;
    private long iterateInterval = 5000;
    private boolean enabled = true;

    @Override
    public void initialize(Session session) throws RepositoryException {
        if (session.getRootNode().hasNode("hippo:configuration/hippo:modules/brokenlinks/hippo:moduleconfig/hippo:moduleconfig")) {
            Node configNode = session.getNode("/hippo:configuration/hippo:modules/brokenlinks/hippo:moduleconfig/hippo:moduleconfig");
            if (configNode.hasProperty("wfdropbox:delay")) {
                initialDelay = configNode.getProperty("wfdropbox:delay").getLong();
            }
            if (configNode.hasProperty("wfdropbox:interval")) {
                iterateInterval = configNode.getProperty("wfdropbox:interval").getLong();
            }
            if (configNode.hasProperty("wfdropbox:enabled")) {
                enabled = configNode.getProperty("wfdropbox:enabled").getBoolean();
            }
        }
        this.session = session;
        this.workflowManager = ((HippoWorkspace)session.getWorkspace()).getWorkflowManager();
        if (enabled) {
            start();
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
        if (enabled) {
            interrupt();
            try {
                this.join();
            } catch (InterruptedException ex) {
            }
        }
        session.logout();
    }

    public void run() {
        try {
            Thread.sleep(initialDelay);
        } catch (InterruptedException ex) {
            if (shutdown) {
                return;
            }
        }
        Set<String> nodeIds = new TreeSet<String>();
        while (!shutdown) {
            long processTime = System.currentTimeMillis();
            nodeIds.clear();
            synchronized (session) {
                try {
                    Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [wfdropbox:call]", Query.JCR_SQL2);
                    QueryResult result = query.execute();
                    for (NodeIterator nodeIter = result.getNodes(); nodeIter.hasNext();) {
                        nodeIds.add(nodeIter.nextNode().getIdentifier());
                    }
                } catch (RepositoryException ex) {
                    log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                }
            }
            for (String nodeId : nodeIds) {
                if (shutdown)
                    break;;
                synchronized (session) {
                    try {
                        Node node = session.getNodeByIdentifier(nodeId);
                        run(node);
                        if (node.isNodeType("wfdropbox:node")) {
                            node.remove();
                        } else {
                            node.removeMixin("wfdropbox:call");
                        }
                        session.save();
                    } catch (WorkflowException ex) {
                        log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                    } catch (MappingException ex) {
                        log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                    } catch (RemoteException ex) {
                        log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                    } catch (RepositoryException ex) {
                        log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                    }
                }
            }
            processTime = System.currentTimeMillis() - processTime;
            long sleepTime = iterateInterval - processTime;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ex) {
                // delibate ignore
            }
        }
    }

    void run(Node node) throws RepositoryException, WorkflowException, MappingException, RemoteException {
        Node documentNode = node;
        if (node.hasProperty("wfdropbox:alternate")) {
            documentNode = node.getProperty("wfdropbox:alternate").getNode();
        }

        String category = node.getProperty("wfdropbox:category").getString();
        WorkflowDescriptor wfDesc = workflowManager.getWorkflowDescriptor(category, documentNode);
        if (wfDesc == null) {
            throw new MappingException("no workflow defined for " + documentNode.getPath() + " in category" + category);
        }
        Class<? extends Workflow> workflowClass = null, workflowMatch = null;
        if (node.hasProperty("wfdropbox:workflow")) {
            try {
                workflowClass = (Class<? extends Workflow>)Class.forName(node.getProperty("wfdropbox:workflow").getString());
                for (Class<? extends Workflow> candidate : wfDesc.getInterfaces()) {
                    if (candidate.isAssignableFrom(workflowClass)) {
                        workflowMatch = candidate;
                        break;
                    }
                }
            } catch (ClassNotFoundException ex) {
                throw new MappingException(node.getProperty("wfdropbox:workflow").getString());
            }
            if (workflowMatch == null) {
                throw new MappingException(workflowClass.getName());
            }
        }
        Workflow wf = workflowManager.getWorkflow(wfDesc);
        Class[] formalArguments;
        Object[] actualArguments = null;
        Method method = null;
        for (Method wfMethod : wf.getClass().getMethods()) {
            if (!wfMethod.getName().equals(node.getProperty("wfdropbox:method").getString()))
                continue;
            if (workflowClass != null && wfMethod.getDeclaringClass() != workflowMatch)
                continue;

            formalArguments = wfMethod.getParameterTypes(); // fallback
            NodeIterator argumentsNodesIter = node.getNode("wfdropbox:arguments").getNodes();
            formalArguments = new Class[(int)argumentsNodesIter.getSize()];
            try {
                int idx = 0;
                while (argumentsNodesIter.hasNext()) {
                    formalArguments[idx++] = Class.forName(argumentsNodesIter.nextNode().getProperty("wfdropbox:formal").getString());
                }
                idx = 0;
                for (Class formalParameter : wfMethod.getParameterTypes()) {
                    if (!formalParameter.equals(formalArguments[idx])) {
                        break;
                    }
                    ++idx;
                }
            } catch (ClassNotFoundException ex) {
            }

            actualArguments = new Object[formalArguments.length];
            argumentsNodesIter = node.getNode("wfdropbox:arguments").getNodes();
            try {
                for (int i = 0; i < formalArguments.length; i++) {
                    Property argumentProperty = argumentsNodesIter.nextNode().getProperty("wfdropbox:actual");
                    if (argumentProperty.getType() == PropertyType.STRING) {
                        Constructor constructor;
                        try {
                            constructor = formalArguments[i].getConstructor(new Class[] {String.class});
                            actualArguments[i] = constructor.newInstance(new Object[] {argumentProperty.getString()});
                        } catch (NoSuchMethodException ex) {
                            log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
                        }
                    } else if (argumentProperty.getType() == PropertyType.LONG) {
                        Constructor constructor;
                        try {
                            constructor = formalArguments[i].getConstructor(new Class[] {Long.class});
                            actualArguments[i] = constructor.newInstance(new Object[] {argumentProperty.getLong()});
                        } catch (NoSuchMethodException ex) {
                            log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
                        }
                    } else if (argumentProperty.getType() == PropertyType.DATE) {
                        if (formalArguments[i].equals(Date.class)) {
                            actualArguments[i] = argumentProperty.getDate().getTime();
                        } else {
                            Constructor constructor;
                            try {
                                constructor = formalArguments[i].getConstructor(new Class[] {Date.class});
                                actualArguments[i] = constructor.newInstance(new Object[] {argumentProperty.getDate().getTime()});
                            } catch (NoSuchMethodException ex) {
                                log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
                            }
                        }
                    }
                }
                method = wfMethod;
                break;
            } catch (InstantiationException ex) {
                log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
            } catch (IllegalAccessException ex) {
                log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
            } catch (InvocationTargetException ex) {
                log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
            }
        }
        try {
            method.invoke(wf, actualArguments);
        } catch (IllegalAccessException ex) {
            log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
        } catch (InvocationTargetException ex) {
            log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
        } catch (SecurityException ex) {
            log.warn(ex.getClass().getName()+": "+ex.getMessage(), ex);
        }
    }
}
