/*
 *  Copyright 2012 Hippo.
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
package org.hippoecm.repository.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.jcr.AccessDeniedException;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemVisitor;
import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.commons.cnd.CompactNodeTypeDefReader;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.core.nodetype.InvalidNodeTypeDefException;
import org.apache.jackrabbit.core.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.spi.commons.nodetype.QDefinitionBuilderFactory;
import org.hippoecm.repository.LocalHippoRepository;
import org.hippoecm.repository.api.HierarchyResolver;
import org.hippoecm.repository.api.HippoNode;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.HippoSession;
import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.ImportMergeBehavior;
import org.hippoecm.repository.api.ImportReferenceBehavior;
import org.hippoecm.repository.api.InitializationProcessor;
import org.hippoecm.repository.jackrabbit.HippoCompactNodeTypeDefReader;
import org.hippoecm.repository.util.JcrUtils;
import org.hippoecm.repository.util.MavenComparableVersion;
import org.onehippo.repository.ManagerServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class InitializationProcessorImpl implements InitializationProcessor {


    private static final Logger log = LoggerFactory.getLogger(InitializationProcessorImpl.class);

    private static final String INIT_PATH = "/" + HippoNodeType.CONFIGURATION_PATH + "/" + HippoNodeType.INITIALIZE_PATH;

    private static final String SHELF_PATH = "/" + HippoNodeType.CONFIGURATION_PATH + "/" + HippoNodeType.SHELF_PATH;

    private static final String TEMP_PATH = "/" + HippoNodeType.CONFIGURATION_PATH + "/" + HippoNodeType.TEMPORARY_PATH;

    private static final String[] INIT_ITEM_PROPERTIES = new String[] {
            HippoNodeType.HIPPO_SEQUENCE,
            HippoNodeType.HIPPO_NAMESPACE,
            HippoNodeType.HIPPO_NODETYPESRESOURCE,
            HippoNodeType.HIPPO_NODETYPES,
            HippoNodeType.HIPPO_CONTENTRESOURCE,
            HippoNodeType.HIPPO_CONTENT,
            HippoNodeType.HIPPO_CONTENTROOT,
            HippoNodeType.HIPPO_CONTENTDELETE,
            HippoNodeType.HIPPO_CONTENTPROPSET,
            HippoNodeType.HIPPO_CONTENTPROPADD,
            HippoNodeType.HIPPO_RELOADONSTARTUP,
            HippoNodeType.HIPPO_VERSION };

    private final static String GET_INITIALIZE_ITEMS =
            "SELECT * FROM hipposys:initializeitem " +
                    "WHERE jcr:path = '/hippo:configuration/hippo:initialize/%' AND " +
                    HippoNodeType.HIPPO_STATUS + " = 'pending'" +
                    "ORDER BY " + HippoNodeType.HIPPO_SEQUENCE + " ASC";

    private static XmlPullParserFactory factory;

    static {
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            log.error("Could not get xpp factory instance: " + e.getMessage());
        }
    }

    private Logger logger;

    public InitializationProcessorImpl() {}

    public InitializationProcessorImpl(Logger logger) {
        this.logger = logger;
    }

    public void dryRun(Session session) {
        try {
            Node initializeFolder = session.getRootNode().addNode("initialize");
            session.save();
            loadExtensions(session, initializeFolder);
            session.save();
            final List<Node> initializeItems = new ArrayList<Node>();
            final Query getInitializeItems = session.getWorkspace().getQueryManager().createQuery(
                    "SELECT * FROM hipposys:initializeitem " +
                            "WHERE jcr:path = '/initialize/%' AND " +
                            HippoNodeType.HIPPO_STATUS + " = 'pending'" +
                            "ORDER BY " + HippoNodeType.HIPPO_SEQUENCE + " ASC", Query.SQL);
            final NodeIterator nodes = getInitializeItems.execute().getNodes();
            while (nodes.hasNext()) {
                initializeItems.add(nodes.nextNode());
            }
            processInitializeItems(session, initializeItems, true);
            session.refresh(false);
            initializeFolder.remove();
            session.save();
        } catch (IOException ex) {
            getLogger().error(ex.getClass().getName()+": "+ex.getMessage(), ex);
        } catch (RepositoryException ex) {
            getLogger().error(ex.getClass().getName()+": "+ex.getMessage(), ex);
        }
    }

    @Override
    public List<Node> loadExtensions(final Session session) throws RepositoryException, IOException {
        return loadExtensions(session, session.getNode(INITIALIZATION_FOLDER));
    }

    @Override
    public List<Node> loadExtension(final Session session, final URL extension) throws RepositoryException, IOException {
        return loadExtension(extension, session, session.getNode(INITIALIZATION_FOLDER));
    }

    @Override
    public void processInitializeItems(Session session) {
        try {
            final List<Node> initializeItems = new ArrayList<Node>();
            final Query getInitializeItems = session.getWorkspace().getQueryManager().createQuery(GET_INITIALIZE_ITEMS, Query.SQL);
            final NodeIterator nodes = getInitializeItems.execute().getNodes();
            while(nodes.hasNext()) {
                initializeItems.add(nodes.nextNode());
            }
            processInitializeItems(session, initializeItems, false);
        } catch (InvalidQueryException ex) {
            getLogger().error(ex.getMessage(), ex);
        } catch (RepositoryException ex) {
            getLogger().error(ex.getMessage(), ex);
        }
    }

    @Override
    public void processInitializeItems(Session session, List<Node> initializeItems) {
        processInitializeItems(session, initializeItems, false);
    }

    @Override
    public void setLogger(final Logger logger) {
        this.logger = logger;
    }

    private void processInitializeItems(Session session, List<Node> initializeItems, boolean dryRun) {

        try {
            if (session == null || !session.isLive()) {
                getLogger().warn("Unable to refresh initialize nodes, no session available");
                return;
            }

            session.refresh(false);

            for (Node initializeItem : initializeItems) {
                getLogger().info("Initializing configuration from " + initializeItem.getName());
                try {

                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_NAMESPACE)) {
                        processNamespaceItem(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_NODETYPESRESOURCE)) {
                        processNodeTypesFromFile(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_NODETYPES)) {
                        processNodeTypesFromNode(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_CONTENTDELETE)) {
                        processContentDelete(initializeItem, session, dryRun);
                    }

                    // Content from file
                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_CONTENTRESOURCE)) {
                        processContentFromFile(initializeItem, session, dryRun);
                    }

                    // CONTENT FROM NODE
                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_CONTENT)) {
                        processContentFromNode(initializeItem, session, dryRun);
                    }

                    // SET OR ADD PROPERTY CONTENT
                    if (initializeItem.hasProperty(HippoNodeType.HIPPO_CONTENTPROPSET) || initializeItem.hasProperty(HippoNodeType.HIPPO_CONTENTPROPADD)) {
                        processSetOrAddProperty(initializeItem, session, dryRun);
                    }

                    if (dryRun) {
                        if (getLogger().isDebugEnabled()) {
                            getLogger().debug("configuration as specified by " + initializeItem.getName());
                            for (NodeIterator iter = ((HippoSession)session).pendingChanges(); iter.hasNext();) {
                                Node pendingNode = iter.nextNode();
                                if (pendingNode != null) {
                                    getLogger().debug("configuration as specified by " + initializeItem.getName() + " modified node " + pendingNode.getPath());
                                }
                            }
                        }
                        session.refresh(false);
                    } else {
                        initializeItem.setProperty(HippoNodeType.HIPPO_STATUS, "done");
                        session.save();
                    }

                } catch (MalformedURLException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (IOException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (ParseException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (AccessDeniedException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (ConstraintViolationException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (InvalidItemStateException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (ItemExistsException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (LockException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (NoSuchNodeTypeException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (UnsupportedRepositoryOperationException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (ValueFormatException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (VersionException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } catch (PathNotFoundException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } finally {
                    session.refresh(false);
                }
            }
        } catch (RepositoryException ex) {
            getLogger().error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }
    }

    private void processSetOrAddProperty(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found content property set/add configuration");
        }
        LinkedList<String> newValues = new LinkedList<String>();
        Property contentSetProperty = (node.hasProperty(HippoNodeType.HIPPO_CONTENTPROPSET) ?
                node.getProperty(HippoNodeType.HIPPO_CONTENTPROPSET) : null);
        Property contentAddProperty = (node.hasProperty(HippoNodeType.HIPPO_CONTENTPROPADD) ?
                node.getProperty(HippoNodeType.HIPPO_CONTENTPROPADD) : null);
        if (contentSetProperty != null) {
            if (contentSetProperty.isMultiple()) {
                for (Value value : contentSetProperty.getValues()) {
                    newValues.add(value.getString());
                }
            } else {
                newValues.add(contentSetProperty.getString());
            }
        }
        if (contentAddProperty != null) {
            if (contentAddProperty.isMultiple()) {
                for (Value value : contentAddProperty.getValues()) {
                    newValues.add(value.getString());
                }
            } else {
                newValues.add(contentAddProperty.getString());
            }
        }
        String root = "/";
        if (node.hasProperty(HippoNodeType.HIPPO_CONTENTROOT)) {
            root = node.getProperty(HippoNodeType.HIPPO_CONTENTROOT).getString();
        }
        getLogger().info("Initializing content set/add property " + root);
        HierarchyResolver.Entry last = new HierarchyResolver.Entry();
        HierarchyResolver hierarchyResolver;
        if (session.getWorkspace() instanceof HippoWorkspace) {
            hierarchyResolver = ((HippoWorkspace)session.getWorkspace()).getHierarchyResolver();
        } else {
            hierarchyResolver = ManagerServiceFactory.getManagerService(session).getHierarchyResolver();
        }
        Property property = hierarchyResolver.getProperty(session.getRootNode(), root.substring(1), last);
        if (property == null) {
            String propertyName = root.substring(root.lastIndexOf("/") + 1);
            if (!root.substring(0, root.lastIndexOf("/")).equals(last.node.getPath())) {
                throw new PathNotFoundException(root);
            }
            boolean isMultiple = false;
            boolean isSingle = false;
            Set<NodeType> nodeTypes = new HashSet<NodeType>();
            nodeTypes.add(last.node.getPrimaryNodeType());
            for (NodeType nodeType : last.node.getMixinNodeTypes()) {
                nodeTypes.add(nodeType);
            }
            for (NodeType nodeType : nodeTypes) {
                for (PropertyDefinition propertyDefinition : nodeType.getPropertyDefinitions()) {
                    if (propertyDefinition.getName().equals("*") || propertyDefinition.getName().equals(propertyName)) {
                        if (propertyDefinition.isMultiple()) {
                            isMultiple = true;
                        } else {
                            isSingle = true;
                        }
                    }
                }
            }
            if (newValues.size() == 1 && contentAddProperty == null && (isSingle || !isMultiple)) {
                last.node.setProperty(last.relPath, newValues.get(0));
            } else if (newValues.isEmpty() && (isSingle || !isMultiple)) {
                // no-op, the property does not exist
            } else {
                last.node.setProperty(last.relPath, newValues.toArray(new String[newValues.size()]));
            }
        } else {
            if (contentSetProperty == null && property.isMultiple()) {
                LinkedList<String> currentValues = new LinkedList<String>();
                for (Value value : property.getValues()) {
                    currentValues.add(value.getString());
                }
                currentValues.addAll(newValues);
                newValues = currentValues;
            }
            if (property.isMultiple()) {
                property.setValue(newValues.toArray(new String[newValues.size()]));
            } else if (newValues.size() == 1 && contentAddProperty == null) {
                property.setValue(newValues.get(0));
            } else if (newValues.isEmpty()) {
                property.remove();
            } else {
                property.setValue(newValues.toArray(new String[newValues.size()]));
            }
        }
    }

    private void processContentFromNode(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found content configuration");
        }

        Property contentProperty = node.getProperty(HippoNodeType.HIPPO_CONTENT);
        String contentName = "<<internal>>";
        InputStream contentStream = contentProperty.getStream();

        if (contentStream == null) {
            getLogger().error("Cannot locate content configuration '" + contentName + "', initialization skipped");
            return;
        }

        final String root = JcrUtils.getStringProperty(node, HippoNodeType.HIPPO_CONTENTROOT, "/");
        // verify that content root is not under the initialization node
        if (root.startsWith(INIT_PATH)) {
            getLogger().error("Bootstrapping content to " + INIT_PATH + " is no supported");
            return;
        }
        initializeNodecontent(session, root, contentStream, contentName + ":" + node.getPath());
    }

    public void processContentFromFile(final Node node, final Session session, final boolean dryRun) throws RepositoryException, IOException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found content resource configuration");
        }

        String contentResource = node.getProperty(HippoNodeType.HIPPO_CONTENTRESOURCE).getString();
        InputStream contentStream = getResourceStream(node, contentResource);

        if (contentStream == null) {
            getLogger().error("Cannot locate content configuration '" + contentResource + "', initialization skipped");
            return;
        }

        final String root = JcrUtils.getStringProperty(node, HippoNodeType.HIPPO_CONTENTROOT, "/");
        // verify that content root is not under the initialization node
        if (root.startsWith(INIT_PATH)) {
            getLogger().error("Bootstrapping content to " + INIT_PATH + " is not supported");
            return;
        }

        if (isReloadable(node)) {
            final String contextNodeName = JcrUtils.getStringProperty(node, HippoNodeType.HIPPO_CONTEXTNODENAME, null);
            final boolean restore = JcrUtils.getBooleanProperty(node, HippoNodeType.HIPPO_RESTOREAFTERRELOAD, true);
            if (contextNodeName != null) {
                final String contextNodePath = root.equals("/") ? root + contextNodeName : root + "/" + contextNodeName;
                Node backupContextNode = null;
                if (restore) {
                    backupContextNode = backupContextNode(session, contextNodePath);
                }
                try {
                    if (removeNodecontent(session, contextNodePath, false)) {
                        initializeNodecontent(session, root, contentStream, contentResource);
                        if (!dryRun) {
                            session.save();
                        }
                        if (restore && backupContextNode != null) {
                            restoreNodes(session, contextNodePath, backupContextNode, dryRun);
                        }
                    } else {
                        getLogger().error("Cannot reload item " + node.getPath() + ": removing node failed");
                    }
                } finally {
                    if (backupContextNode != null) {
                        backupContextNode.remove();
                    }
                }
            } else {
                getLogger().error("Cannot reload item " + node.getPath() + ": possibly because it is a delta.");
            }
        } else {
            initializeNodecontent(session, root, contentStream, contentResource);
        }

    }

    private Node backupContextNode(final Session session, final String contextNodePath) throws RepositoryException {
        if (session.nodeExists(contextNodePath)) {
            final Node contextNode = session.getNode(contextNodePath);
            final String tempNodePath = TEMP_PATH + "/" + contextNode.getIdentifier();
            JcrUtils.copy(session, contextNodePath, tempNodePath);
            return session.getNode(tempNodePath);
        }
        return null;
    }

    private void restoreNodes(final Session session, final String contextNodePath, final Node backupNode, final boolean dryRun) throws RepositoryException {
        final Node contextNode = session.getNode(contextNodePath);
        final List<Node> restorableNodes = new ArrayList<Node>();
        contextNode.accept(new ItemVisitor() {
            @Override
            public void visit(final Property property) throws RepositoryException {}

            @Override
            public void visit(final Node node) throws RepositoryException {
                if (node instanceof HippoNode && ((HippoNode) node).isVirtual()) {
                    return;
                }

                if (node.isNodeType(HippoNodeType.NT_RESTORABLE)) {
                    if (node == contextNode) {
                        getLogger().warn("Reload root is restorable: net effect of reload will be zero");
                    }
                    restorableNodes.add(node);
                    return;
                }

                final NodeIterator nodes = node.getNodes();
                while (nodes.hasNext()) {
                    nodes.nextNode().accept(this);
                }
            }
        });
        for (Node restorableNode : restorableNodes) {
            final String destAbsPath = restorableNode.getPath();
            final String srcAbsPath = backupNode.getPath() + (restorableNode == contextNode ? "" : "/" + restorableNode.getPath().substring(contextNodePath.length()+1));
            try {
                if (session.nodeExists(srcAbsPath)) {
                    restorableNode.remove();
                    JcrUtils.copy(session, srcAbsPath, destAbsPath);
                    if (!dryRun) {
                        session.save();
                    }
                    getLogger().info("Restored node at " + destAbsPath);
                }
            } catch (ConstraintViolationException e) {
                getLogger().warn("Failed to restore node at " + destAbsPath + ": shelving item");
                session.refresh(false);
                shelveNode(session, srcAbsPath, destAbsPath);
            }
        }

    }

    private void shelveNode(final Session session, final String srcAbsPath, final String restoreLocation) throws RepositoryException {
        createShelfIfNotExists(session);
        final Node failedRestorable = session.getNode(srcAbsPath);
        final String destAbsPath = SHELF_PATH + "/" + failedRestorable.getIdentifier();
        JcrUtils.copy(session, srcAbsPath, destAbsPath);
        final Node shelvedItem = session.getNode(destAbsPath);
        shelvedItem.addMixin(HippoNodeType.NT_SHELVEDITEM);
        shelvedItem.setProperty(HippoNodeType.HIPPOSYS_RESTORELOCATION, restoreLocation);
        session.save();
    }

    private void createShelfIfNotExists(final Session session) throws RepositoryException {
        if (!session.nodeExists(SHELF_PATH)) {
            final Node configurationNode = session.getRootNode().getNode(HippoNodeType.CONFIGURATION_PATH);
            configurationNode.addNode(HippoNodeType.SHELF_PATH, HippoNodeType.NT_SHELF);
            session.save();
        }
    }

    private void processContentDelete(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        final String path = node.getProperty(HippoNodeType.HIPPO_CONTENTDELETE).getString();
        final boolean immediateSave = !node.hasProperty(HippoNodeType.HIPPO_CONTENTRESOURCE) && !node.hasProperty(HippoNodeType.HIPPO_CONTENT);
        getLogger().info("Delete content in initialization: " + node.getName() + " " + path);
        final boolean success = removeNodecontent(session, path, immediateSave && !dryRun);
        if (!success) {
            getLogger().error("Content delete in item " + node.getName() + " failed");
        }
    }

    private void processNodeTypesFromNode(final Node node, final Session session, final boolean dryRun) throws RepositoryException, ParseException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found nodetypes configuration");
        }
        String cndName = "<<internal>>";
        InputStream cndStream = node.getProperty(HippoNodeType.HIPPO_NODETYPES).getStream();
        if (cndStream == null) {
            getLogger().error("Cannot get stream for nodetypes definition property.");
        } else {
            getLogger().info("Initializing node types from nodetypes property.");
            if (!dryRun) {
                initializeNodetypes(session.getWorkspace(), cndStream, cndName);
            }
        }
    }

    private void processNodeTypesFromFile(final Node node, final Session session, final boolean dryRun) throws RepositoryException, IOException, ParseException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found nodetypes resource configuration");
        }
        String cndResource = node.getProperty(HippoNodeType.HIPPO_NODETYPESRESOURCE).getString();
        InputStream cndStream = getResourceStream(node, cndResource);
        if (cndStream == null) {
            getLogger().error("Cannot locate nodetype configuration '" + cndResource + "', initialization skipped");
        } else {
            getLogger().info("Initializing nodetypes from: " + cndResource);
            if (!dryRun) {
                initializeNodetypes(session.getWorkspace(), cndStream, cndResource);
            }
        }
    }

    private void processNamespaceItem(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found namespace configuration");
        }
        String namespace = node.getProperty(HippoNodeType.HIPPO_NAMESPACE).getString();
        getLogger().info("Initializing namespace: " + node.getName() + " " + namespace);
        // Add namespace if it doesn't exist
        if (!dryRun) {
            initializeNamespace(session.getWorkspace().getNamespaceRegistry(), node.getName(), namespace);
        }
    }

    public List<Node> loadExtensions(Session session, Node initializationFolder) throws IOException, RepositoryException {
        final List<URL> extensions = scanForExtensions();
        final List<Node> initializeItems = new ArrayList<Node>();
        for(final URL configurationURL : extensions) {
            initializeItems.addAll(loadExtension(configurationURL, session, initializationFolder));
        }
        return initializeItems;
    }

    public List<Node> loadExtension(final URL configurationURL, final Session session, final Node initializationFolder) throws RepositoryException, IOException {
        List<Node> initializeItems = new ArrayList<Node>();
        getLogger().info("Initializing extension "+configurationURL);
        try {
            initializeNodecontent(session, "/hippo:configuration/hippo:temporary", configurationURL.openStream(), configurationURL.getPath());
            final Node tempInitFolderNode = session.getNode("/hippo:configuration/hippo:temporary/hippo:initialize");
            final String moduleVersion = getModuleVersion(configurationURL);
            final NodeIterator tempIter = tempInitFolderNode.getNodes();
            while (tempIter.hasNext()) {
                initializeItems.addAll(initializeInitializeItem(session, tempIter.nextNode(), initializationFolder, moduleVersion, configurationURL));

            }
            if(tempInitFolderNode.hasProperty(HippoNodeType.HIPPO_VERSION)) {
                Set<String> tags = new TreeSet<String>();
                if (initializationFolder.hasProperty(HippoNodeType.HIPPO_VERSION)) {
                    for (Value value : initializationFolder.getProperty(HippoNodeType.HIPPO_VERSION).getValues()) {
                        tags.add(value.getString());
                    }
                }
                Value[] added = tempInitFolderNode.getProperty(HippoNodeType.HIPPO_VERSION).getValues();
                for (Value value : added) {
                    tags.add(value.getString());
                }
                initializationFolder.setProperty(HippoNodeType.HIPPO_VERSION, tags.toArray(new String[tags.size()]));
            }
            tempInitFolderNode.remove();
            session.save();
        } catch (PathNotFoundException ex) {
            getLogger().error("Rejected old style configuration content", ex);
            for(NodeIterator removeTempIter = session.getRootNode().getNode("hippo:configuration/hippo:temporary").getNodes(); removeTempIter.hasNext(); ) {
                removeTempIter.nextNode().remove();
            }
            session.save();
        } catch (RepositoryException ex) {
            throw new RepositoryException("Initializing extension " + configurationURL.getPath() + " failed", ex);
        }
        return initializeItems;
    }

    private List<Node> initializeInitializeItem(final Session session, final Node tempInitItemNode, final Node initializationFolder, final String moduleVersion, final URL configurationURL) throws RepositoryException {

        final List<Node> initializeItems = new ArrayList<Node>();
        Node initItemNode = JcrUtils.getNodeIfExists(initializationFolder, tempInitItemNode.getName());
        final String existingModuleVersion = initItemNode != null ? JcrUtils.getStringProperty(initItemNode, HippoNodeType.HIPPO_EXTENSIONVERSION, null) : null;
        final String deprecatedExistingItemVersion = initItemNode != null ? JcrUtils.getStringProperty(initItemNode, HippoNodeType.HIPPO_EXTENSIONBUILD, null) : null;
        final String existingItemVersion = initItemNode != null ? JcrUtils.getStringProperty(initItemNode, HippoNodeType.HIPPO_VERSION, deprecatedExistingItemVersion) : deprecatedExistingItemVersion;
        final String itemVersion = JcrUtils.getStringProperty(tempInitItemNode, HippoNodeType.HIPPO_VERSION, null);

        if (initItemNode == null || shouldReload(tempInitItemNode, initItemNode, moduleVersion, existingModuleVersion, itemVersion, existingItemVersion)) {

            boolean isReload = false;
            if(initItemNode != null) {
                getLogger().info("Item " + tempInitItemNode.getName() + " needs to be reloaded");
                initItemNode.remove();
                isReload = true;
            }

            initItemNode = initializationFolder.addNode(tempInitItemNode.getName(), HippoNodeType.NT_INITIALIZEITEM);
            if(isExtension(configurationURL)) {
                initItemNode.setProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE, configurationURL.toString());
                if (moduleVersion != null) {
                    initItemNode.setProperty(HippoNodeType.HIPPO_EXTENSIONVERSION, moduleVersion);
                }
            }
            for (String propertyName : INIT_ITEM_PROPERTIES) {
                copyProperty(tempInitItemNode, initItemNode, propertyName);
            }

            final String contextNodeName = initItemNode.hasProperty(HippoNodeType.HIPPO_CONTENTRESOURCE) ? readContextNodeName(initItemNode) : null;
            if (contextNodeName != null) {
                initItemNode.setProperty(HippoNodeType.HIPPO_CONTEXTNODENAME, contextNodeName);
                if (isReload) {
                    final String root = JcrUtils.getStringProperty(initItemNode, HippoNodeType.HIPPO_CONTENTROOT, "/");
                    final String contextNodePath = root.equals("/") ? root + contextNodeName : root + "/" + contextNodeName;
                    final NodeIterator downstreamItems = getDownstreamItems(session, contextNodePath);
                    while (downstreamItems.hasNext()) {
                        final Node downStreamItem = downstreamItems.nextNode();
                        getLogger().info("Marking downstream item " + downStreamItem.getName() + " for reload");
                        downStreamItem.setProperty(HippoNodeType.HIPPO_STATUS, "pending");
                        initializeItems.add(downStreamItem);
                    }
                }
            }

            initItemNode.setProperty(HippoNodeType.HIPPO_STATUS, "pending");
            initializeItems.add(initItemNode);
        } else if (isExtension(configurationURL)) {
            if (initItemNode.isNodeType(HippoNodeType.NT_INITIALIZEITEM)) {
                // we need an up to date location in order to reload items
                initItemNode.setProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE, configurationURL.toString());
            }
        }

        return initializeItems;
    }

    private boolean isExtension(final URL configurationURL) {
        return "hippoecm-extension.xml".equals(configurationURL.getFile().contains("/")
                ? configurationURL.getFile().substring(configurationURL.getFile().lastIndexOf("/")+1)
                : configurationURL.getFile());
    }

    private List<URL> scanForExtensions() throws IOException {
        final List<URL> extensions = new LinkedList<URL>();
        Enumeration<URL> iter = Thread.currentThread().getContextClassLoader().getResources("org/hippoecm/repository/extension.xml");
        while (iter.hasMoreElements()) {
            extensions.add(iter.nextElement());
        }
        iter = Thread.currentThread().getContextClassLoader().getResources("hippoecm-extension.xml");
        while (iter.hasMoreElements()) {
            extensions.add(iter.nextElement());
        }
        return extensions;
    }

    private boolean shouldReload(final Node temp, final Node existing, final String moduleVersion, final String existingModuleVersion, final String itemVersion, final String existingItemVersion) throws RepositoryException {
        if (!isReloadable(temp)) {
            return false;
        }
        if (itemVersion != null && !isNewerVersion(itemVersion, existingItemVersion)) {
            return false;
        }
        if (itemVersion == null && !isNewerVersion(moduleVersion, existingModuleVersion)) {
            return false;
        }
        if (existing.hasProperty(HippoNodeType.HIPPO_STATUS) && existing.getProperty(HippoNodeType.HIPPO_STATUS).getString().equals("disabled")) {
            return false;
        }
        return true;
    }

    private boolean isNewerVersion(final String version, final String existingVersion) {
        if (version == null) {
            return false;
        }
        if (existingVersion == null) {
            return true;
        }
        try {
            return new MavenComparableVersion(version).compareTo(new MavenComparableVersion(existingVersion)) > 0;
        } catch (RuntimeException e) {
            // version could not be parsed
            getLogger().error("Invalid version: " + version + " or existing: " + existingVersion);
        }
        return false;
    }

    private String getModuleVersion(URL configurationURL) {
        String configurationURLString = configurationURL.toString();
        if (configurationURLString.endsWith("hippoecm-extension.xml")) {
            String manifestUrlString = configurationURLString.substring(0, configurationURLString.length() - "hippoecm-extension.xml".length()) + "META-INF/MANIFEST.MF";
            try {
                Manifest manifest = new Manifest(new URL(manifestUrlString).openStream());
                return manifest.getMainAttributes().getValue(new Attributes.Name("Implementation-Build"));
            } catch (IOException ex) {
                // deliberate ignore, manifest file not available so no build number can be obtained
            }
        }
        return null;
    }

    public NodeIterator getDownstreamItems(final Session session, final String contextNodePath) throws RepositoryException {
        return session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM hipposys:initializeitem WHERE " +
                        "jcr:path = '/hippo:configuration/hippo:initialize/%' AND (" +
                        HippoNodeType.HIPPO_CONTENTROOT + " = '" + contextNodePath + "' OR " +
                        HippoNodeType.HIPPO_CONTENTROOT + " LIKE '" + contextNodePath + "/%')", Query.SQL
        ).execute().getNodes();
    }

    public String readContextNodeName(final Node item) {
        if (factory == null) {
            return null;
        }
        try {
            InputStream contentStream = getResourceStream(item, item.getProperty(HippoNodeType.HIPPO_CONTENTRESOURCE).getString());
            if (contentStream != null) {
                try {
                    // inspect the xml file to find out if it is a delta xml and to read the name of the context node we must remove
                    boolean removeSupported = true;
                    String contextNodeName = null;
                    XmlPullParser xpp = factory.newPullParser();
                    xpp.setInput(contentStream, null);
                    while(xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                        if (xpp.getEventType() == XmlPullParser.START_TAG) {
                            String mergeDirective = xpp.getAttributeValue("http://www.onehippo.org/jcr/xmlimport", "merge");
                            if (mergeDirective != null && (mergeDirective.equals("combine") || mergeDirective.equals("overlay"))) {
                                removeSupported = false;
                            }
                            contextNodeName = xpp.getAttributeValue("http://www.jcp.org/jcr/sv/1.0", "name");
                            break;
                        }
                        xpp.next();
                    }
                    if (removeSupported) {
                        return contextNodeName;
                    }
                } finally {
                    try { contentStream.close(); } catch (IOException ignore) {}
                }
            }
        } catch (RepositoryException e) {
            getLogger().error("Could not read root node name from content file", e);
        } catch (XmlPullParserException e) {
            getLogger().error("Could not read root node name from content file", e);
        } catch (IOException e) {
            getLogger().error("Could not read root node name from content file", e);
        }
        return null;
    }

    private InputStream getResourceStream(final Node item, String resourcePath) throws RepositoryException, IOException {
        InputStream resourceStream = null;
        if (resourcePath.startsWith("file:")) {
            if (resourcePath.startsWith("file://")) {
                resourcePath = resourcePath.substring(6);
            } else if (resourcePath.startsWith("file:/")) {
                resourcePath = resourcePath.substring(5);
            } else if (resourcePath.startsWith("file:")) {
                resourcePath = "/" + resourcePath.substring(5);
            }
            File localFile = new File(resourcePath);
            try {
                resourceStream = new BufferedInputStream(new FileInputStream(localFile));
            } catch (FileNotFoundException e) {
                getLogger().error("Resource file not found: " + resourceStream, e);
            }
        } else {
            if (item.hasProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE)) {
                URL resource = new URL(item.getProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE).getString());
                resource = new URL(resource, resourcePath);
                resourceStream = resource.openStream();
            } else {
                resourceStream = LocalHippoRepository.class.getResourceAsStream(resourcePath);
            }
        }
        return resourceStream;
    }

    public void initializeNamespace(NamespaceRegistry nsreg, String prefix, String uri) throws RepositoryException {
        try {

            /* Try to remap namespace if a namespace already exists and the uri is similar.
             * This assumes a convention to use in the namespace URI.  It should end with a version
             * number of the nodetypes, such as in http://www.sample.org/nt/1.0.0
             */
            try {
                String currentURI = nsreg.getURI(prefix);
                if (currentURI.equals(uri)) {
                    getLogger().debug("Namespace already exists: " + prefix + ":" + uri);
                    return;
                }
                String uriPrefix = currentURI.substring(0, currentURI.lastIndexOf("/") + 1);
                if(!uriPrefix.equals(uri.substring(0,uri.lastIndexOf("/")+1))) {
                    getLogger().error("Prefix already used for different namespace: " + prefix + ":" + uri);
                    return;
                }
                // do not remap namespace, the upgrading infrastructure must take care of this
                return;
            } catch (NamespaceException ex) {
                if (!ex.getMessage().endsWith("is not a registered namespace prefix.")) {
                    getLogger().error(ex.getMessage() +" For: " + prefix + ":" + uri);
                }
            }

            nsreg.registerNamespace(prefix, uri);

        } catch (NamespaceException ex) {
            if (ex.getMessage().endsWith("mapping already exists")) {
                getLogger().error("Namespace already exists: " + prefix + ":" + uri);
            } else {
                getLogger().error(ex.getMessage()+" For: " + prefix + ":" + uri);
            }
        }
    }

    public void initializeNodetypes(Workspace workspace, InputStream cndStream, String cndName) throws ParseException, RepositoryException {
        CompactNodeTypeDefReader<QNodeTypeDefinition,NamespaceMapping> cndReader = new HippoCompactNodeTypeDefReader<QNodeTypeDefinition, NamespaceMapping>(new InputStreamReader(cndStream), cndName, workspace.getNamespaceRegistry(), new QDefinitionBuilderFactory());
        List<QNodeTypeDefinition> ntdList = cndReader.getNodeTypeDefinitions();
        NodeTypeManagerImpl ntmgr = (NodeTypeManagerImpl) workspace.getNodeTypeManager();
        NodeTypeRegistry ntreg = ntmgr.getNodeTypeRegistry();

        for (Iterator<QNodeTypeDefinition> iter = ntdList.iterator(); iter.hasNext();) {
            QNodeTypeDefinition ntd = iter.next();

            try {
                ntreg.registerNodeType(ntd);
                getLogger().info("Registered node type: " + ntd.getName().getLocalName());
            } catch (NamespaceException ex) {
                getLogger().error(ex.getMessage()+". In " + cndName +" error for "+  ntd.getName().getNamespaceURI() +":"+ntd.getName().getLocalName(), ex);
            } catch (InvalidNodeTypeDefException ex) {
                if (ex.getMessage().endsWith("already exists")) {
                    try {
                        ntreg.reregisterNodeType(ntd);
                        getLogger().info("Replaced node type: " + ntd.getName().getLocalName());
                    } catch (NamespaceException e) {
                        getLogger().error(e.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), e);
                    } catch (InvalidNodeTypeDefException e) {
                        getLogger().info(e.getMessage() +". In " + cndName +" for "+  ntd.getName().getNamespaceURI() +":"+ntd.getName().getLocalName(), e);
                    } catch (RepositoryException e) {
                        if (!e.getMessage().equals("not yet implemented")) {
                            getLogger().warn(e.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), e);
                        }
                    }
                } else {
                    getLogger().error(ex.getMessage()+". In " + cndName +" error for "+  ntd.getName().getNamespaceURI() +":"+ntd.getName().getLocalName(), ex);
                }
            } catch (RepositoryException ex) {
                if (!ex.getMessage().equals("not yet implemented")) {
                    getLogger().warn(ex.getMessage()+". In " + cndName +" error for "+  ntd.getName().getNamespaceURI() +":"+ntd.getName().getLocalName(), ex);
                }
            }
        }
    }

    public boolean removeNodecontent(Session session, String absPath, boolean save) {
        if ("".equals(absPath) || "/".equals(absPath)) {
            getLogger().warn("Not allowed to delete rootNode from initialization.");
            return false;
        }

        String relpath = (absPath.startsWith("/") ? absPath.substring(1) : absPath);
        try {
            if (relpath.length() > 0) {
                if (session.getRootNode().hasNode(relpath)) {
                    if (session.getRootNode().getNodes(relpath).getSize() > 1) {
                        getLogger().warn("Removing same name sibling is not supported: not removing " + absPath);
                        return false;
                    }
                    session.getRootNode().getNode(relpath).remove();
                    if (save) {
                        session.save();
                    }
                }
                return true;
            }
        } catch (RepositoryException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error while removing content from '" + absPath + "' : " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error while removing content from '" + absPath + "' : " + ex.getMessage());
            }
        }
        return false;
    }

    public void initializeNodecontent(Session session, String parentAbsPath, InputStream istream, String location) {
        getLogger().info("Initializing content from: " + location + " to " + parentAbsPath);
        try {
            String relpath = (parentAbsPath.startsWith("/") ? parentAbsPath.substring(1) : parentAbsPath);
            if (relpath.length() > 0 && !session.getRootNode().hasNode(relpath)) {
                session.getRootNode().addNode(relpath);
            }
            if (session instanceof HippoSession) {
                ((HippoSession) session).importDereferencedXML(parentAbsPath, istream, ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW,
                        ImportReferenceBehavior.IMPORT_REFERENCE_NOT_FOUND_REMOVE, ImportMergeBehavior.IMPORT_MERGE_ADD_OR_SKIP);
            } else {
                session.importXML(parentAbsPath, istream, ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            }
        } catch (IOException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        } catch (PathNotFoundException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        } catch (ItemExistsException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                if(!ex.getMessage().startsWith("Node with the same UUID exists:") || getLogger().isDebugEnabled()) {
                    getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
                }
            }
        } catch (ConstraintViolationException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        } catch (VersionException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        } catch (InvalidSerializedDataException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                if(!ex.getMessage().startsWith("Node with the same UUID exists:") || getLogger().isDebugEnabled()) {
                    getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
                }
            }
        } catch (LockException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        } catch (RepositoryException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error initializing content for "+location+" in '" + parentAbsPath + "' : " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        }
    }

    private boolean isReloadable(Node node) throws RepositoryException {
        return JcrUtils.getBooleanProperty(node, HippoNodeType.HIPPO_RELOADONSTARTUP, false);
    }

    private void copyProperty(Node source, Node target, String propertyName) throws RepositoryException {
        final Property property = JcrUtils.getPropertyIfExists(source, propertyName);
        if (property != null) {
            if (property.getDefinition().isMultiple()) {
                target.setProperty(propertyName, property.getValues(), property.getType());
            } else {
                target.setProperty(propertyName, property.getValue());
            }
        }
    }

    private Logger getLogger() {
        if (logger != null) {
            return logger;
        }
        return log;
    }

}
