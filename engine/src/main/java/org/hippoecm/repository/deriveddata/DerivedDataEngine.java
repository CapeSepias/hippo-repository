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

package org.hippoecm.repository.deriveddata;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.ItemVisitor;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.commons.iterator.NodeIterable;
import org.apache.jackrabbit.commons.iterator.PropertyIterable;
import org.hippoecm.repository.LocalHippoRepository;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.HippoSession;
import org.hippoecm.repository.ext.DerivedDataFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DerivedDataEngine {

    static final Logger log = LoggerFactory.getLogger(DerivedDataEngine.class);

    private static final String DERIVATIVES_PATH = "/" + HippoNodeType.CONFIGURATION_PATH + "/hippo:derivatives";

    private final HippoSession session;

    public DerivedDataEngine(HippoSession session) {
        this.session = session;
    }

    public void save() throws VersionException, LockException, ConstraintViolationException, RepositoryException {
        save(null);
    }

    public void save(Node node) throws VersionException, LockException, ConstraintViolationException, RepositoryException {
        long start = 0;
        if (!session.nodeExists(DERIVATIVES_PATH)) {
            return;
        }
        if (log.isDebugEnabled()) {
            start = System.currentTimeMillis();
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("Derived engine active");
            }
            Set<Node> recomputeSet = getRecomputeSet(node);
            if (log.isDebugEnabled()) {
                log.debug("Derived engine found " + recomputeSet.size() + " nodes to be evaluated in " +
                                     (System.currentTimeMillis() - start) + " ms");
            }

            if (recomputeSet.size() == 0) {
                return;
            }

            Node derivatesFolder = session.getNode(DERIVATIVES_PATH);
            for (Node modified : recomputeSet) {
                compute(derivatesFolder, modified);
            }
        } catch (NamespaceException ex) {
            // be lenient against configuration problems
            log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        } catch (ConstraintViolationException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Derived engine done in " + (System.currentTimeMillis() - start) + " ms");
            }
        }
    }

    private Set<Node> getRecomputeSet(final Node node) throws RepositoryException {
        Set<Node> recomputeSet = new TreeSet<Node>(new Comparator<Node>() {

            @Override
            public int compare(Node o1, Node o2) {
                try {
                    int comparison = o1.getPath().length() - o2.getPath().length();
                    if (comparison == 0) {
                        return o1.getPath().compareTo(o2.getPath());
                    } else {
                        return comparison;
                    }
                } catch (RepositoryException ex) {
                    log.error("Error while comparing nodes: " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
                    return 0;
                }
            }

        });

        // add derived data nodes that depend on (referenceable) nodes that have been modified
        try {
            for (Node modified : new NodeIterable(session.pendingChanges(node, "mix:referenceable"))) {
                if (modified == null) {
                    log.error("Unable to access node that was changed by own session");
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Derived engine found modified referenceable node " + modified.getPath() +
                                         " with " + modified.getReferences().getSize() + " references");
                }
                for (Property property : new PropertyIterable(modified.getReferences())) {
                    try {
                        Node dependency = property.getParent();
                        if (dependency.isNodeType(HippoNodeType.NT_DERIVED)) {
                            recomputeSet.add(dependency);
                        }
                    } catch (AccessDeniedException ex) {
                        log.error(ex.getClass().getName() + ": " + ex.getMessage());
                        throw new RepositoryException(ex); // configuration problem
                    } catch (ItemNotFoundException ex) {
                        log.error(ex.getClass().getName() + ": " + ex.getMessage());
                        throw new RepositoryException(ex); // inconsistent state
                    }
                }
            }
        } catch (NamespaceException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage()); // internal error jcr:uuid not accessible
            throw new RepositoryException("Internal error accessing jcr:uuid");
        } catch (NoSuchNodeTypeException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage()); // internal error jcr:uuid not found
            throw new RepositoryException("Internal error jcr:uuid not found");
        }

        // add derived data nodes that have been modified themselves
        try {
            for (Node modified : new NodeIterable(session.pendingChanges(node, HippoNodeType.NT_DERIVED))) {
                if (modified == null) {
                    log.error("Unable to access node that was changed by own session");
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Derived engine found " + modified.getPath() + " " + (modified.isNodeType(
                            "mix:referenceable") ? modified.getUUID() : "") + " with derived mixin");
                }
                recomputeSet.add(modified);
            }
        } catch (NamespaceException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException("Internal error " + HippoNodeType.NT_DERIVED + " not found");
        } catch (NoSuchNodeTypeException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException("Internal error " + HippoNodeType.NT_DERIVED + " not found");
        }
        return recomputeSet;
    }

    public void validate() throws ConstraintViolationException, RepositoryException {
        int totalCount = 0, changedCount = 0;
        Node derivatesFolder = session.getNode(DERIVATIVES_PATH);
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM hippo:derived", Query.SQL);
        QueryResult result = query.execute();
        for (Node node : new NodeIterable(result.getNodes())) {
            ++totalCount;
            if (compute(derivatesFolder, node)) {
                ++changedCount;
                if ((changedCount % LocalHippoRepository.batchThreshold) == 0) {
                    session.save();
                }
            }
        }
        log.warn("Validated " + totalCount + " nodes, and reset " + changedCount + " nodes");
        session.save();
    }

    private boolean compute(Node derivatesFolder, Node modified) throws ConstraintViolationException, RepositoryException {
        if (!modified.isCheckedOut()) {
            Node ancestor = modified;
            while (!ancestor.isNodeType("mix:versionable")) {
                ancestor = ancestor.getParent();
            }
            ancestor.checkout();
        }

        SortedSet<String> dependencies = new TreeSet<String>();
        for (Node functionNode : new NodeIterable(derivatesFolder.getNodes())) {
            if (functionNode == null) {
                log.error("unable to access all derived data functions");
                continue;
            }

            FunctionDescription functionDescription = new FunctionDescription(functionNode);
            String nodetypeName = functionDescription.getNodeTypeName();
            if (!modified.isNodeType(nodetypeName)) {
                continue;
            }

            applyFunction(modified, dependencies, functionDescription);
        }

        return persistDependencies(modified, dependencies);
    }

    private void applyFunction(final Node modified, final SortedSet<String> dependencies, final FunctionDescription function) throws RepositoryException {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Derived node " + modified.getPath() + " is of derived type as defined in " +
                        function);
            }

            String nodetypeName = function.getNodeTypeName();

            /* preparation: build the map of parameters to be fed to
            * the function and instantiate the class containing the
            * compute function.
            */
            Class clazz = Class.forName(function.getClassName());
            DerivedDataFunction func = (DerivedDataFunction) clazz.newInstance();
            func.setValueFactory(session.getValueFactory());

            NodeType nodetype = session.getWorkspace().getNodeTypeManager().getNodeType(nodetypeName);
            PropertyMapper mapper = new PropertyMapper(function, modified, session.getValueFactory(), nodetype);

            Map<String, Value[]> parameters = mapper.getParameters(dependencies);

            /* Perform the computation.
            */
            parameters = func.compute(parameters);

            mapper.persistValues(parameters);

        } catch (AccessDeniedException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // should not be possible
        } catch (ItemNotFoundException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // impossible
        } catch (PathNotFoundException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // impossible
        } catch (ValueFormatException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // impossible
        } catch (ClassNotFoundException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // impossible
        } catch (InstantiationException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // impossible
        } catch (IllegalAccessException ex) {
            log.error(ex.getClass().getName() + ": " + ex.getMessage());
            throw new RepositoryException(ex); // impossible
        }
    }

    private boolean persistDependencies(final Node modified, final SortedSet<String> dependencies) throws RepositoryException {
        if (modified.isNodeType("mix:referenceable")) {
            dependencies.remove(modified.getIdentifier());
        }
        Value[] dependenciesValues = new Value[dependencies.size()];
        int i = 0;
        for (String dependency : dependencies) {
            dependenciesValues[i++] = session.getValueFactory().createValue(dependency, PropertyType.REFERENCE);
        }
        Value[] oldDependenciesValues = null;
        if (modified.hasProperty(HippoNodeType.HIPPO_RELATED)) {
            oldDependenciesValues = modified.getProperty(HippoNodeType.HIPPO_RELATED).getValues();
        }
        boolean changed = false;
        if (oldDependenciesValues != null && dependenciesValues.length == oldDependenciesValues.length) {
            for (i = 0; i < dependenciesValues.length; i++) {
                if (!dependenciesValues[i].equals(oldDependenciesValues[i])) {
                    changed = true;
                    break;
                }
            }
        } else {
            changed = true;
        }
        if (changed) {
            try {
                if (!modified.isCheckedOut()) {
                    modified.checkout(); // FIXME: is this node always versionalble?
                }
                modified.setProperty(HippoNodeType.HIPPO_RELATED, dependenciesValues, PropertyType.REFERENCE);
            } catch (ItemNotFoundException ex) {
                log.info("write error on modified node " + modified.getPath(), ex);
            }
        }
        return changed;
    }

    public static void removal(Node removed) throws RepositoryException {
        if (removed.isNodeType("mix:referenceable")) {
            final String uuid = removed.getIdentifier();
            removed.accept(new ItemVisitor() {
                public void visit(Property property) throws RepositoryException {
                }

                public void visit(Node node) throws RepositoryException {
                    for (PropertyIterator iter = node.getReferences(); iter.hasNext(); ) {
                        Property prop = iter.nextProperty();
                        if (prop.getDefinition().getName().equals(HippoNodeType.HIPPO_RELATED)) {
                            Value[] values = prop.getValues();
                            for (int i = 0; i < values.length; i++) {
                                if (values[i].getString().equals(uuid)) {
                                    Value[] newValues = new Value[values.length - 1];
                                    if (i > 0) {
                                        System.arraycopy(values, 0, newValues, 0, i);
                                    }
                                    if (values.length - i > 1) {
                                        System.arraycopy(values, i + 1, newValues, i, values.length - i - 1);
                                    }
                                    Node ancestor = prop.getParent();
                                    if (!ancestor.isCheckedOut()) {
                                        while (!ancestor.isNodeType("mix:versionable")) {
                                            ancestor = ancestor.getParent();
                                        }
                                        ancestor.checkout();
                                    }
                                    prop.setValue(newValues);
                                    break;
                                }
                            }
                        }
                    }
                }
            });
        }
    }

}