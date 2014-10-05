/*
 *  Copyright 2012-2014 Hippo B.V. (http://www.onehippo.com)
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
package org.onehippo.repository.bootstrap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.lock.LockManager;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import com.google.common.base.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ClosedInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.cnd.CompactNodeTypeDefReader;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.core.nodetype.InvalidNodeTypeDefException;
import org.apache.jackrabbit.core.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.spi.commons.nodetype.QDefinitionBuilderFactory;
import org.hippoecm.repository.LocalHippoRepository;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.HippoSession;
import org.hippoecm.repository.api.ImportReferenceBehavior;
import org.hippoecm.repository.api.InitializationProcessor;
import org.hippoecm.repository.api.PostStartupTask;
import org.hippoecm.repository.jackrabbit.HippoCompactNodeTypeDefReader;
import org.hippoecm.repository.util.JcrUtils;
import org.hippoecm.repository.util.MavenComparableVersion;
import org.hippoecm.repository.util.NodeIterable;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.cms7.services.webresources.WebResourceException;
import org.onehippo.cms7.services.webresources.WebResourcesService;
import org.onehippo.repository.util.FileContentResourceLoader;
import org.onehippo.repository.util.ZipFileContentResourceLoader;
import org.onehippo.repository.xml.ContentResourceLoader;
import org.onehippo.repository.xml.DefaultContentHandler;
import org.onehippo.repository.xml.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENT;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENTDELETE;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENTPROPADD;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENTPROPDELETE;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENTPROPSET;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENTRESOURCE;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTENTROOT;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTEXTNODENAME;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_CONTEXTPATHS;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_NAMESPACE;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_NODETYPES;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_NODETYPESRESOURCE;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_RELOADONSTARTUP;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_SEQUENCE;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_STATUS;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_UPSTREAMITEMS;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_VERSION;
import static org.hippoecm.repository.api.HippoNodeType.HIPPO_WEBRESOURCEBUNDLE;
import static org.hippoecm.repository.util.RepoUtils.getClusterNodeId;
import static org.onehippo.repository.util.JcrConstants.MIX_LOCKABLE;

public class InitializationProcessorImpl implements InitializationProcessor {

    private static final Logger log = LoggerFactory.getLogger(InitializationProcessorImpl.class);

    private static final String INIT_PATH = "/" + HippoNodeType.CONFIGURATION_PATH + "/" + HippoNodeType.INITIALIZE_PATH;
    private static final long LOCK_TIMEOUT = Long.getLong("repo.bootstrap.lock.timeout", 60 * 5);
    private static final long LOCK_ATTEMPT_INTERVAL = 1000 * 2;

    private static final String[] INIT_ITEM_PROPERTIES = {
            HIPPO_SEQUENCE,
            HIPPO_NAMESPACE,
            HIPPO_NODETYPESRESOURCE,
            HIPPO_NODETYPES,
            HIPPO_CONTENTRESOURCE,
            HIPPO_CONTENT,
            HIPPO_CONTENTROOT,
            HIPPO_CONTENTDELETE,
            HIPPO_CONTENTPROPDELETE,
            HIPPO_CONTENTPROPSET,
            HIPPO_CONTENTPROPADD,
            HIPPO_RELOADONSTARTUP,
            HIPPO_VERSION,
            HIPPO_WEBRESOURCEBUNDLE
    };

    private final static String GET_INITIALIZE_ITEMS =
            "SELECT * FROM hipposys:initializeitem " +
                    "WHERE jcr:path = '/hippo:configuration/hippo:initialize/%' AND " +
                    HIPPO_STATUS + " LIKE 'pending%' " +
                    "ORDER BY " + HIPPO_SEQUENCE + " ASC";

    private final static String GET_OLD_INITIALIZE_ITEMS = "SELECT * FROM hipposys:initializeitem " +
            "WHERE jcr:path = '/hippo:configuration/hippo:initialize/%' AND (" +
            HippoNodeType.HIPPO_TIMESTAMP + " IS NULL OR " +
            HippoNodeType.HIPPO_TIMESTAMP + " < {})";

    private static final double NO_HIPPO_SEQUENCE = -1.0;

    private static final Comparator<Node> initializeItemComparator = new Comparator<Node>() {

        @Override
        public int compare(final Node n1, final Node n2) {
            try {
                final Double s1 = JcrUtils.getDoubleProperty(n1, HIPPO_SEQUENCE, NO_HIPPO_SEQUENCE);
                final Double s2 = JcrUtils.getDoubleProperty(n2, HIPPO_SEQUENCE, NO_HIPPO_SEQUENCE);
                final int result = s1.compareTo(s2);
                if (result != 0) {
                    return result;
                }
                return n1.getName().compareTo(n2.getName());
            } catch (RepositoryException e) {
                log.error("Error comparing initialize item nodes", e);
            }
            return 0;
        }
    };

    private Logger logger;

    public InitializationProcessorImpl() {}

    public InitializationProcessorImpl(Logger logger) {
        this.logger = logger;
    }

    public void dryRun(Session session) {
        try {
            Node initializeFolder = session.getRootNode().addNode("initialize");
            session.save();
            loadExtensions(session, initializeFolder, false);
            session.save();
            final List<Node> initializeItems = new ArrayList<Node>();
            final Query getInitializeItems = session.getWorkspace().getQueryManager().createQuery(
                    "SELECT * FROM hipposys:initializeitem " +
                            "WHERE jcr:path = '/initialize/%' AND " +
                            HIPPO_STATUS + " = 'pending' " +
                            "ORDER BY " + HIPPO_SEQUENCE + " ASC", Query.SQL);
            final NodeIterator nodes = getInitializeItems.execute().getNodes();
            while (nodes.hasNext()) {
                initializeItems.add(nodes.nextNode());
            }
            processInitializeItems(session, initializeItems, true);
            session.refresh(false);
            initializeFolder.remove();
            session.save();
        } catch (IOException | RepositoryException ex) {
            getLogger().error(ex.getClass().getName()+": "+ex.getMessage(), ex);
        }
    }

    @Override
    public List<Node> loadExtensions(final Session session) throws RepositoryException, IOException {
        return loadExtensions(session, session.getNode(INITIALIZATION_FOLDER), true);
    }

    @Override
    public List<Node> loadExtension(final Session session, final URL extension) throws RepositoryException, IOException {
        return loadExtension(extension, session, session.getNode(INITIALIZATION_FOLDER), new HashSet<String>());
    }

    @Override
    public List<PostStartupTask> processInitializeItems(Session session) {
        try {
            final List<Node> initializeItems = new ArrayList<>();
            final Query getInitializeItems = session.getWorkspace().getQueryManager().createQuery(GET_INITIALIZE_ITEMS, Query.SQL);
            final NodeIterator nodes = getInitializeItems.execute().getNodes();
            while(nodes.hasNext()) {
                initializeItems.add(nodes.nextNode());
            }
            return processInitializeItems(session, initializeItems, false);
        } catch (RepositoryException ex) {
            getLogger().error(ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    @Override
    public List<PostStartupTask> processInitializeItems(Session session, List<Node> initializeItems) {
        return processInitializeItems(session, initializeItems, false);
    }

    @Override
    public void setLogger(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean lock(final Session session) throws RepositoryException {
        ensureIsLockable(session, INIT_PATH);
        final LockManager lockManager = session.getWorkspace().getLockManager();
        final long t1 = System.currentTimeMillis();
        while (true) {
            log.debug("Attempting to obtain lock");
            try {
                lockManager.lock(INIT_PATH, false, false, LOCK_TIMEOUT, getClusterNodeId(session));
                log.debug("Lock successfully obtained");
                return true;
            } catch (LockException e) {
                if (System.currentTimeMillis() - t1 < LOCK_TIMEOUT * 1000) {
                    log.debug("Obtaining lock failed, reattempting in {} ms", LOCK_ATTEMPT_INTERVAL);
                    try {
                        Thread.sleep(LOCK_ATTEMPT_INTERVAL);
                    } catch (InterruptedException ignore) {
                    }
                } else {
                    return false;
                }
            }
        }
    }

    @Override
    public void unlock(final Session session) throws RepositoryException {
        final LockManager lockManager = session.getWorkspace().getLockManager();
        try {
            log.debug("Attempting to release lock");
            lockManager.unlock(INIT_PATH);
            log.debug("Lock successfully released");
        } catch (LockException e) {
            log.warn("Current session no longer holds a lock, please set a longer repo.bootstrap.lock.timeout");
        }
    }

    private List<PostStartupTask> processInitializeItems(Session session, List<Node> initializeItems, boolean dryRun) {
        Collections.sort(initializeItems, initializeItemComparator);
        final List<PostStartupTask> postStartupTasks = new ArrayList<PostStartupTask>();
        try {
            if (session == null || !session.isLive()) {
                getLogger().warn("Unable to refresh initialize nodes, no session available");
                return Collections.emptyList();
            }

            session.refresh(false);

            for (Node initializeItem : initializeItems) {
                getLogger().info("Initializing configuration from " + initializeItem.getName());
                try {

                    if (initializeItem.hasProperty(HIPPO_NAMESPACE)) {
                        processNamespaceItem(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_NODETYPESRESOURCE)) {
                        processNodeTypesFromFile(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_NODETYPES)) {
                        processNodeTypesFromNode(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_CONTENTDELETE)) {
                        processContentDelete(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_CONTENTPROPDELETE)) {
                        processContentPropDelete(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_CONTENTRESOURCE)) {
                        processContentFromFile(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_WEBRESOURCEBUNDLE)) {
                        addTaskIfPresent(postStartupTasks, processWebResourceBundle(initializeItem, session));
                    }

                    if (initializeItem.hasProperty(HIPPO_CONTENT)) {
                        processContentFromNode(initializeItem, session, dryRun);
                    }

                    if (initializeItem.hasProperty(HIPPO_CONTENTPROPSET)) {
                        processContentPropSet(initializeItem, session, dryRun);
                    }
                    if (initializeItem.hasProperty(HIPPO_CONTENTPROPADD)) {
                        processContentPropAdd(initializeItem, session, dryRun);
                    }

                    if (dryRun) {
                        if (getLogger().isDebugEnabled()) {
                            getLogger().debug("configuration as specified by " + initializeItem.getName());
                            for (NodeIterator iter = ((HippoSession)session).pendingChanges(); iter.hasNext();) {
                                Node pendingNode = iter.nextNode();
                                getLogger().debug("configuration as specified by " + initializeItem.getName() + " modified node " + pendingNode.getPath());
                            }
                        }
                        session.refresh(false);
                    } else {
                        initializeItem.setProperty(HIPPO_STATUS, "done");
                        if (initializeItem.hasProperty(HIPPO_UPSTREAMITEMS)) {
                            initializeItem.getProperty(HIPPO_UPSTREAMITEMS).remove();
                        }
                        session.save();
                    }

                } catch (IOException | ParseException | RepositoryException ex) {
                    getLogger().error("configuration as specified by " + initializeItem.getPath() + " failed", ex);
                } finally {
                    session.refresh(false);
                }
            }
        } catch (RepositoryException ex) {
            getLogger().error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }
        return postStartupTasks;
    }

    private void addTaskIfPresent(final List<PostStartupTask> tasks, final Optional<? extends PostStartupTask> optionalTask) {
        if (optionalTask.isPresent()) {
            tasks.add(optionalTask.get());
        }
    }

    private Optional<? extends PostStartupTask> processWebResourceBundle(final Node item, final Session session) throws RepositoryException {
        if (!session.nodeExists(WebResourcesService.JCR_ROOT_PATH)) {
            getLogger().error("Failed to initialize item {}: web resources root {} is missing", item.getName(), WebResourcesService.JCR_ROOT_PATH);
            return Optional.absent();
        }

        String bundlePath = item.getProperty(HIPPO_WEBRESOURCEBUNDLE).getString().trim();
        // remove leading and trailing /
        bundlePath = bundlePath.indexOf('/') == 0 && bundlePath.length() > 1 ? bundlePath.substring(1) : bundlePath;
        bundlePath = bundlePath.lastIndexOf('/') == bundlePath.length()-1 ? bundlePath.substring(0, bundlePath.length()-1) : bundlePath;
        if (bundlePath.isEmpty()) {
            getLogger().error("Failed to initialize item {}: invalid {} property", item.getName(), HIPPO_WEBRESOURCEBUNDLE);
            return Optional.absent();
        }
        final String extensionSource = JcrUtils.getStringProperty(item, HippoNodeType.HIPPO_EXTENSIONSOURCE, null);

        try {
            final Optional<? extends PostStartupTask> importTask = createImportWebResourceTask(extensionSource, bundlePath, session);
            if (importTask.isPresent() && isReloadable(item)) {
                final String bundleName = bundlePath.indexOf('/') == -1 ? bundlePath : bundlePath.substring(bundlePath.lastIndexOf('/') + 1);
                final String contextNodePath = WebResourcesService.JCR_ROOT_PATH + "/" + bundleName;
                int index = getNodeIndex(session, contextNodePath);
                if (!removeNode(session, contextNodePath, false)) {
                    return Optional.absent();
                }
                if (index != -1) {
                    reorderNode(session, contextNodePath, index);
                }
            }
            return importTask;
        } catch (URISyntaxException|IOException e) {
            getLogger().error("Error initializing web resource bundle {} at {}", bundlePath, WebResourcesService.JCR_ROOT_PATH, e);
            return Optional.absent();
        }
    }

    private Optional<? extends PostStartupTask> createImportWebResourceTask(final String extensionSource, final String bundlePath, final Session session) throws IOException, URISyntaxException {
        if (extensionSource == null) {
            return Optional.absent();
        } else if (extensionSource.contains("jar!")) {
            final PartialZipFile bundleZipFile = new PartialZipFile(getBaseZipFileFromURL(new URL(extensionSource)), bundlePath);
            return Optional.of(new ImportWebResourceBundleFromZipTask(session, bundleZipFile));
        } else if (extensionSource.startsWith("file:")) {
            final File extensionFile = FileUtils.toFile(new URL(extensionSource));
            final File bundleDir = new File(extensionFile.getParent(), bundlePath);
            return Optional.of(new ImportWebResourceBundleFromDirectoryTask(session, bundleDir));
        }
        return Optional.absent();
    }

    private void processContentPropSet(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        Property contentSetProperty = node.getProperty(HIPPO_CONTENTPROPSET);
        String contentRoot = StringUtils.trim(JcrUtils.getStringProperty(node, HIPPO_CONTENTROOT, "/"));
        getLogger().info("Contentpropset property " + contentRoot);

        if (session.propertyExists(contentRoot)) {
            final Property property = session.getProperty(contentRoot);
            if (property.isMultiple()) {
                property.setValue(contentSetProperty.getValues());
            } else {
                final Value[] values = contentSetProperty.getValues();
                if (values.length == 0) {
                    property.remove();
                } else if (values.length == 1) {
                    property.setValue(values[0]);
                } else {
                    log.warn("Initialize item {} wants to set multiple values on single valued property", node.getName());
                }
            }
        } else {
            final int offset = contentRoot.lastIndexOf('/');
            final String targetNodePath = offset == 0 ? "/" : contentRoot.substring(0, offset);
            final String propertyName = contentRoot.substring(offset+1);
            final Value[] values = contentSetProperty.getValues();
            if (values.length == 0) {
                log.warn("No value specified for new property");
            } else if (values.length == 1) {
                final Node target = session.getNode(targetNodePath);
                if (isMultiple(target, propertyName)) {
                    target.setProperty(propertyName, values);
                } else {
                    target.setProperty(propertyName, values[0]);
                }
            } else {
                session.getNode(targetNodePath).setProperty(propertyName, values);
            }
        }
    }

    private boolean isMultiple(final Node target, final String propertyName) throws RepositoryException {
        final List<NodeType> nodeTypes = new ArrayList<>(Arrays.asList(target.getMixinNodeTypes()));
        nodeTypes.add(target.getPrimaryNodeType());
        for (NodeType nodeType : nodeTypes) {
            for (PropertyDefinition propertyDefinition : nodeType.getPropertyDefinitions()) {
                if (propertyDefinition.getName().equals("*") || propertyDefinition.getName().equals(propertyName)) {
                    return propertyDefinition.isMultiple();
                }
            }
        }
        return false;
    }

    private void processContentPropAdd(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        Property contentAddProperty = node.getProperty(HIPPO_CONTENTPROPADD);
        String contentRoot = StringUtils.trim(JcrUtils.getStringProperty(node, HIPPO_CONTENTROOT, "/"));
        getLogger().info("Contentpropadd property " + contentRoot);

        final Property property = session.getProperty(contentRoot);
        if (property.isMultiple()) {
            final List<Value> values = new ArrayList<>(Arrays.asList(property.getValues()));
            values.addAll(Arrays.asList(contentAddProperty.getValues()));
            property.setValue(values.toArray(new Value[values.size()]));
        } else {
            log.warn("Cannot add values to a single valued property");
        }
    }

    private void processContentFromNode(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        getLogger().debug("Found content configuration");

        Property contentProperty = node.getProperty(HIPPO_CONTENT);
        String contentName = "<<internal>>";
        InputStream contentStream = contentProperty.getStream();

        if (contentStream == null) {
            getLogger().error("Cannot locate content configuration '" + contentName + "', initialization skipped");
            return;
        }

        final String root = StringUtils.trim(JcrUtils.getStringProperty(node, HIPPO_CONTENTROOT, "/"));
        if (root.startsWith(INIT_PATH)) {
            getLogger().error("Bootstrapping content to " + INIT_PATH + " is no supported");
            return;
        }
        initializeNodecontent(session, root, contentStream, null);
    }

    public void processContentFromFile(final Node item, final Session session, final boolean dryRun) throws RepositoryException, IOException {
        getLogger().debug("Found content resource configuration");

        String contentResource = StringUtils.trim(item.getProperty(HIPPO_CONTENTRESOURCE).getString());
        URL contentURL = getResource(item, contentResource);
        boolean pckg = contentResource.endsWith(".zip") || contentResource.endsWith(".jar");

        if (contentURL == null) {
            getLogger().error("Cannot locate content configuration '" + contentResource + "', initialization skipped");
            return;
        }

        String contentRoot = StringUtils.trim(JcrUtils.getStringProperty(item, HIPPO_CONTENTROOT, "/"));
        if (contentRoot.startsWith(INIT_PATH)) {
            getLogger().error("Bootstrapping content to " + INIT_PATH + " is not supported");
            return;
        }

        ImportResult importResult = null;
        if (isReloadable(item)) {
            final String contextNodeName = StringUtils.trim(JcrUtils.getStringProperty(item, HIPPO_CONTEXTNODENAME, null));
            if (contextNodeName != null ) {
                final String contextNodePath = contentRoot.equals("/") ? contentRoot + contextNodeName : contentRoot + "/" + contextNodeName;
                final int index = getNodeIndex(session, contextNodePath);
                if (removeNode(session, contextNodePath, false)) {
                    InputStream is = null;
                    BufferedInputStream bis = null;
                    try {
                        is = contentURL.openStream();
                        bis = new BufferedInputStream(is); 
                        importResult = initializeNodecontent(session, contentRoot, bis, contentURL, pckg);
                    } finally {
                        IOUtils.closeQuietly(bis);
                        IOUtils.closeQuietly(is);
                    }
                    if (index != -1) {
                        reorderNode(session, contextNodePath, index);
                    }
                } else {
                    getLogger().error("Cannot reload item {}: removing node failed", item.getName());
                }
            } else {
                getLogger().error("Cannot reload item {} because context node could not be determined", item.getName());
            }
        } else {
            InputStream in = null;
            try {
                final Property upstreamItemIds = JcrUtils.getPropertyIfExists(item, HIPPO_UPSTREAMITEMS);
                if (upstreamItemIds != null) {
                    for (Value upstreamItemId : upstreamItemIds.getValues()) {
                        Node upstreamItem = session.getNodeByIdentifier(upstreamItemId.getString());
                        final String upstreamItemContextPath;
                        final String[] upstreamItemContextPaths = JcrUtils.getMultipleStringProperty(upstreamItem, HIPPO_CONTEXTPATHS, null);
                        if (upstreamItemContextPaths != null && upstreamItemContextPaths.length > 0) {
                            upstreamItemContextPath = upstreamItemContextPaths[0];
                        } else {
                            final String upstreamItemContentRoot = JcrUtils.getStringProperty(upstreamItem, HIPPO_CONTENTROOT, null);
                            final String upstreamItemContextNodeName = JcrUtils.getStringProperty(upstreamItem, HIPPO_CONTEXTNODENAME, null);
                            if (upstreamItemContentRoot == null || upstreamItemContextNodeName == null) {
                                throw new RepositoryException("Unable to reload downstream item: can't determine upstream item context path");
                            }
                            upstreamItemContextPath = upstreamItemContentRoot.equals("/") ? "/" + upstreamItemContextNodeName : upstreamItemContentRoot + "/" + upstreamItemContextNodeName;
                        }
                        in = contentURL.openStream();
                        String upstreamItemContentRoot = StringUtils.substringBeforeLast(upstreamItemContextPath, "/");
                        if (upstreamItemContentRoot.length() > contentRoot.length()) {
                            String contextRelPath = StringUtils.substringAfter(upstreamItemContextPath, contentRoot + "/");
                            contentRoot = upstreamItemContentRoot;
                            in = getPartialContentInputStream(in, contextRelPath);
                        }
                        importResult = initializeNodecontent(session, contentRoot, in, contentURL, pckg);
                        IOUtils.closeQuietly(in);
                    }
                } else {
                    in = contentURL.openStream();
                    importResult = initializeNodecontent(session, contentRoot, in, contentURL, pckg);
                }
            } finally {
                IOUtils.closeQuietly(in);
            }
        }
        if (importResult != null) {
            final Collection<String> contextPaths = importResult.getContextPaths();
            if (!contextPaths.isEmpty()) {
                item.setProperty(HIPPO_CONTEXTPATHS, contextPaths.toArray(new String[contextPaths.size()]));
            }
        }
    }

    InputStream getPartialContentInputStream(InputStream in, final String contextRelPath) throws IOException, RepositoryException {
        File file = File.createTempFile("bootstrap", "xml");
        OutputStream out = null;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            SAXParser parser = factory.newSAXParser();

            out = new FileOutputStream(file);
            TransformerHandler handler = ((SAXTransformerFactory)SAXTransformerFactory.newInstance()).newTransformerHandler();
            Transformer transformer = handler.getTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            handler.setResult(new StreamResult(out));

            parser.parse(new InputSource(in), new DefaultContentHandler(new PartialSystemViewFilter(handler, contextRelPath)));
            return new TempFileInputStream(file);
        } catch (FactoryConfigurationError e) {
            throw new RepositoryException("SAX parser implementation not available", e);
        } catch (ParserConfigurationException e) {
            throw new RepositoryException("SAX parser configuration error", e);
        } catch (SAXException e) {
            Exception exception = e.getException();
            if (exception instanceof RepositoryException) {
                throw (RepositoryException) exception;
            } else if (exception instanceof IOException) {
                throw (IOException) exception;
            } else {
                throw new InvalidSerializedDataException("Error parsing XML import", e);
            }
        } catch (TransformerConfigurationException e) {
            throw new RepositoryException("SAX transformation error", e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    private void reorderNode(final Session session, final String nodePath, final int index) throws RepositoryException {
        final Node node = session.getNode(nodePath);
        final String srcChildRelPath = node.getName() + "[" + node.getIndex() + "]";
        final Node parent = node.getParent();
        final NodeIterator nodes = parent.getNodes();
        nodes.skip(index);
        if (nodes.hasNext()) {
            final Node destChild = nodes.nextNode();
            String destChildRelPath = destChild.getName() + "[" + destChild.getIndex() + "]";
            if (!srcChildRelPath.equals(destChildRelPath)) {
                parent.orderBefore(srcChildRelPath, destChildRelPath);
            }
        }
    }

    private int getNodeIndex(final Session session, final String nodePath) throws RepositoryException {
        final Node node = JcrUtils.getNodeIfExists(nodePath, session);
        if (node != null && node.getParent().getPrimaryNodeType().hasOrderableChildNodes()) {
            final NodeIterator nodes = node.getParent().getNodes();
            int index = 0;
            while (nodes.hasNext()) {
                if (nodes.nextNode().isSame(node)) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    private void processContentDelete(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        final String path = StringUtils.trim(node.getProperty(HIPPO_CONTENTDELETE).getString());
        final boolean immediateSave = !node.hasProperty(HIPPO_CONTENTRESOURCE) && !node.hasProperty(HIPPO_CONTENT);
        getLogger().info("Delete content in initialization: {} {}", node.getName(), path);
        final boolean success = removeNode(session, path, immediateSave && !dryRun);
        if (!success) {
            getLogger().error("Content delete in item {} failed", node.getName());
        }
    }

    private void processContentPropDelete(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        final String path = StringUtils.trim(node.getProperty(HIPPO_CONTENTPROPDELETE).getString());
        getLogger().info("Delete content in initialization: {} {}", node.getName(), path);
        final boolean success = removeProperty(session, path, !dryRun);
        if (!success) {
            getLogger().error("Property delete in item {} failed", node.getName());
        }
    }

    private void processNodeTypesFromNode(final Node node, final Session session, final boolean dryRun) throws RepositoryException, ParseException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found nodetypes configuration");
        }
        String cndName = "<<internal>>";
        InputStream cndStream = node.getProperty(HIPPO_NODETYPES).getStream();
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
        String cndResource = StringUtils.trim(node.getProperty(HIPPO_NODETYPESRESOURCE).getString());
        URL cndURL = getResource(node, cndResource);
        if (cndURL == null) {
            getLogger().error("Cannot locate nodetype configuration '" + cndResource + "', initialization skipped");
        } else {
            if (!dryRun) {
                InputStream is = null;
                BufferedInputStream bis = null;
                try {
                    is = cndURL.openStream();
                    bis = new BufferedInputStream(is);
                    initializeNodetypes(session.getWorkspace(), bis, cndURL.toString());
                } finally {
                    IOUtils.closeQuietly(bis);
                    IOUtils.closeQuietly(is);
                }
            }
        }
    }

    private void processNamespaceItem(final Node node, final Session session, final boolean dryRun) throws RepositoryException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Found namespace configuration");
        }
        String namespace = StringUtils.trim(node.getProperty(HIPPO_NAMESPACE).getString());
        getLogger().info("Initializing namespace: " + node.getName() + " " + namespace);
        if (!dryRun) {
            initializeNamespace(session.getWorkspace().getNamespaceRegistry(), node.getName(), namespace);
        }
    }

    public List<Node> loadExtensions(Session session, Node initializationFolder, boolean cleanup) throws IOException, RepositoryException {
        final Set<String> reloadItems = new HashSet<String>();
        final long now = System.currentTimeMillis();
        final List<URL> extensions = scanForExtensions();
        final List<Node> initializeItems = new ArrayList<Node>();
        for(final URL configurationURL : extensions) {
            initializeItems.addAll(loadExtension(configurationURL, session, initializationFolder, reloadItems));
        }
        if (cleanup) {
            cleanupInitializeItems(session, now);
        }
        initializeItems.addAll(markReloadDownstreamItems(session, reloadItems));
        return initializeItems;
    }

    List<Node> markReloadDownstreamItems(final Session session, final Set<String> reloadItems) throws RepositoryException {
        List<Node> initializeItems = new ArrayList<Node>();
        for (String reloadItem : reloadItems) {
            final Node initItemNode = session.getNodeByIdentifier(reloadItem);
            final String contextNodeName = StringUtils.trim(JcrUtils.getStringProperty(initItemNode, HIPPO_CONTEXTNODENAME, null));
            final String contentRoot = StringUtils.trim(JcrUtils.getStringProperty(initItemNode, HIPPO_CONTENTROOT, "/"));
            for (Node downStreamItem : resolveDownstreamItems(session, contentRoot, contextNodeName)) {
                getLogger().info("Marking item {} pending because downstream from {}", new Object[] { downStreamItem.getName(), initItemNode.getName() });
                downStreamItem.setProperty(HIPPO_STATUS, "pending");
                Value[] upstreamItems;
                if (downStreamItem.hasProperty(HIPPO_UPSTREAMITEMS)) {
                    List<Value> values = new ArrayList<>(Arrays.asList(downStreamItem.getProperty(HIPPO_UPSTREAMITEMS).getValues()));
                    values.add(session.getValueFactory().createValue(reloadItem));
                    upstreamItems = values.toArray(new Value[values.size()]);
                } else {
                    upstreamItems = new Value[] { session.getValueFactory().createValue(reloadItem) };
                }
                downStreamItem.setProperty(HIPPO_UPSTREAMITEMS, upstreamItems);
                initializeItems.add(downStreamItem);
            }
        }
        session.save();
        return initializeItems;
    }

    private void cleanupInitializeItems(final Session session, final long cleanBefore) throws RepositoryException {
        try {
            final String statement = GET_OLD_INITIALIZE_ITEMS.replace("{}", String.valueOf(cleanBefore));
            final Query query = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
            for (Node node : new NodeIterable(query.execute().getNodes())) {
                if (node != null) {
                    log.info("Removing old initialize item {}", node.getName());
                    node.remove();
                }
            }
            session.save();
        } catch (RepositoryException e) {
            log.error("Exception occurred while cleaning up old initialize items", e);
            session.refresh(false);
        }
    }

    private List<Node> loadExtension(final URL configurationURL, final Session session, final Node initializationFolder, final Set<String> reloadItems) throws RepositoryException, IOException {
        List<Node> initializeItems = new ArrayList<Node>();
        getLogger().info("Initializing extension "+configurationURL);
        try {
            initializeNodecontent(session, "/hippo:configuration/hippo:temporary", configurationURL.openStream(), configurationURL);
            final Node tempInitFolderNode = session.getNode("/hippo:configuration/hippo:temporary/hippo:initialize");
            final String moduleVersion = getModuleVersion(configurationURL);
            for (final Node tempInitItemNode : new NodeIterable(tempInitFolderNode.getNodes())) {
                initializeItems.addAll(initializeInitializeItem(tempInitItemNode, initializationFolder, moduleVersion, configurationURL, reloadItems));

            }
            if(tempInitFolderNode.hasProperty(HIPPO_VERSION)) {
                Set<String> tags = new TreeSet<String>();
                if (initializationFolder.hasProperty(HIPPO_VERSION)) {
                    for (Value value : initializationFolder.getProperty(HIPPO_VERSION).getValues()) {
                        tags.add(value.getString());
                    }
                }
                Value[] added = tempInitFolderNode.getProperty(HIPPO_VERSION).getValues();
                for (Value value : added) {
                    tags.add(value.getString());
                }
                initializationFolder.setProperty(HIPPO_VERSION, tags.toArray(new String[tags.size()]));
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

    private List<Node> initializeInitializeItem(final Node tempInitItemNode, final Node initializationFolder, final String moduleVersion, final URL configurationURL, final Set<String> reloadItems) throws RepositoryException {

        getLogger().debug("Initializing item: " + tempInitItemNode.getName());

        final List<Node> initializeItems = new ArrayList<Node>();
        Node initItemNode = JcrUtils.getNodeIfExists(initializationFolder, tempInitItemNode.getName());
        final String deprecatedExistingModuleVersion = initItemNode != null ? JcrUtils.getStringProperty(initItemNode, HippoNodeType.HIPPO_EXTENSIONBUILD, null) : null;
        final String existingModuleVersion = initItemNode != null ? JcrUtils.getStringProperty(initItemNode, HippoNodeType.HIPPO_EXTENSIONVERSION, deprecatedExistingModuleVersion) : deprecatedExistingModuleVersion;
        final String existingItemVersion = initItemNode != null ? JcrUtils.getStringProperty(initItemNode, HIPPO_VERSION, null) : null;
        final String itemVersion = JcrUtils.getStringProperty(tempInitItemNode, HIPPO_VERSION, null);

        final boolean isReload = initItemNode != null && shouldReload(tempInitItemNode, initItemNode, moduleVersion, existingModuleVersion, itemVersion, existingItemVersion);

        if (isReload) {
            getLogger().info("Item {} needs to be reloaded", tempInitItemNode.getName());
            initItemNode.remove();
            initItemNode = null;
        }

        if (initItemNode == null) {
            getLogger().info("Item {} set to status pending", tempInitItemNode.getName());
            initItemNode = initializationFolder.addNode(tempInitItemNode.getName(), HippoNodeType.NT_INITIALIZEITEM);
            initItemNode.setProperty(HIPPO_STATUS, "pending");
            initializeItems.add(initItemNode);
        }

        if (isExtension(configurationURL)) {
            initItemNode.setProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE, configurationURL.toString());
            if (moduleVersion != null) {
                initItemNode.setProperty(HippoNodeType.HIPPO_EXTENSIONVERSION, moduleVersion);
            }
        }

        for (String propertyName : INIT_ITEM_PROPERTIES) {
            copyProperty(tempInitItemNode, initItemNode, propertyName);
        }

        ContentFileInfo info = initItemNode.hasProperty(HIPPO_CONTENTRESOURCE) ? readContentFileInfo(initItemNode) : null;
        if (info != null) {
            initItemNode.setProperty(HIPPO_CONTEXTNODENAME, info.contextNodeName);
            initItemNode.setProperty(HippoNodeType.HIPPOSYS_DELTADIRECTIVE, info.deltaDirective);
            if (isReload) {
                reloadItems.add(initItemNode.getIdentifier());
            }
        }

        initItemNode.setProperty(HippoNodeType.HIPPO_TIMESTAMP, System.currentTimeMillis());

        return initializeItems;
    }

    private boolean isExtension(final URL configurationURL) {
        return configurationURL.getFile().endsWith("hippoecm-extension.xml");
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
            getLogger().debug("Item {} is not reloadable", temp.getName());
            return false;
        }
        if (itemVersion != null) {
            final boolean isNewer = isNewerVersion(itemVersion, existingItemVersion);
            getLogger().debug("Comparing item versions of item {}: new version = {}; old version = {}; newer = {}", temp.getName(), itemVersion, existingItemVersion, isNewer);
            if (!isNewer) {
                return false;
            }
        } else {
            final boolean isNewer = isNewerVersion(moduleVersion, existingModuleVersion);
            getLogger().debug("Comparing module versions of item {}: new module version {}; old module version = {}; newer = {}", temp.getName(), moduleVersion, existingModuleVersion, isNewer);
            if (!isNewer) {
                return false;
            }
        }
        if ("disabled".equals(JcrUtils.getStringProperty(existing, HIPPO_STATUS, null))) {
            getLogger().debug("Item {} is disabled", temp.getName());
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

    private Iterable<Node> resolveDownstreamItems(final Session session, final String contentRoot, final String contextNodeName) throws RepositoryException {
        List<Node> downStreamItems = resolveDownstreamItemsBasedOnContentRoot(session, contentRoot, contextNodeName);
        downStreamItems.addAll(resolveDownstreamItemsBasedOnContextPaths(session, contentRoot, contextNodeName));
        return downStreamItems;
    }

    List<Node> resolveDownstreamItemsBasedOnContextPaths(final Session session, final String contentRoot, final String contextNodeName) throws RepositoryException {
        final String contextNodePath = contentRoot.equals("/") ? contentRoot + contextNodeName : contentRoot + "/" + contextNodeName;
        final List<Node> downStreamItems = new ArrayList<>();
        QueryResult result = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM hipposys:initializeitem WHERE " +
                        "jcr:path = '/hippo:configuration/hippo:initialize/%' AND (" +
                        HIPPO_CONTEXTPATHS + " LIKE '" + contextNodePath + "/%' OR " +
                        HIPPO_CONTEXTPATHS + " = '" + contextNodePath + "')", Query.SQL
        ).execute();
        for (Node item : new NodeIterable(result.getNodes())) {
            downStreamItems.add(item);
        }
        return downStreamItems;
    }

    List<Node> resolveDownstreamItemsBasedOnContentRoot(final Session session, final String contentRoot, final String contextNodeName) throws RepositoryException {
        QueryResult result = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM hipposys:initializeitem WHERE " +
                        "jcr:path = '/hippo:configuration/hippo:initialize/%' AND " +
                        HIPPO_CONTENTROOT + " LIKE '" + contentRoot + "%'", Query.SQL
        ).execute();
        final List<Node> downStreamItems = new ArrayList<>();
        final String contextNodePath = contentRoot.equals("/") ? contentRoot + contextNodeName : contentRoot + "/" + contextNodeName;
        for (Node item : new NodeIterable(result.getNodes())) {
            final String dsContentRoot = StringUtils.trim(JcrUtils.getStringProperty(item, HIPPO_CONTENTROOT, null));
            final String dsContextNodeName = StringUtils.trim(JcrUtils.getStringProperty(item, HIPPO_CONTEXTNODENAME, null));
            final String dsContextNodePath = dsContentRoot.equals("/") ? dsContentRoot + dsContextNodeName : dsContentRoot + "/" + dsContextNodeName;
            if (contextNodePath.equals(dsContextNodePath) || dsContextNodePath.startsWith(contextNodePath + "/")) {
                downStreamItems.add(item);
            }
        }
        return downStreamItems;
    }

    ContentFileInfo readContentFileInfo(final Node item) {
        try {
            final String contentResource = StringUtils.trim(item.getProperty(HIPPO_CONTENTRESOURCE).getString());
            if (contentResource.endsWith(".zip") || contentResource.endsWith(".jar")) {
                return null;
            }
            URL contentURL = getResource(item, contentResource);
            if (contentURL != null) {
                InputStream is = null;
                BufferedInputStream bis = null;
                try {
                    // inspect the xml file to find out if it is a delta xml and to read the name of the context node we must remove
                    String contextNodeName = null;
                    String deltaDirective = null;
                    final XmlPullParser xpp = new MXParser();
                    xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                    is = contentURL.openStream();
                    bis = new BufferedInputStream(is);
                    xpp.setInput(bis, null);
                    while(xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                        if (xpp.getEventType() == XmlPullParser.START_TAG) {
                            contextNodeName = xpp.getAttributeValue("http://www.jcp.org/jcr/sv/1.0", "name");
                            deltaDirective = xpp.getAttributeValue("http://www.onehippo.org/jcr/xmlimport", "merge");
                            break;
                        }
                        xpp.next();
                    }
                    return new ContentFileInfo(contextNodeName, deltaDirective);
                } finally {
                    IOUtils.closeQuietly(bis);
                    IOUtils.closeQuietly(is);
                }
            }
        } catch (RepositoryException | XmlPullParserException | IOException e) {
            getLogger().error("Could not read root node name from content file", e);
        }
        return null;
    }

    private URL getResource(final Node item, String resourcePath) throws RepositoryException, IOException {
        if (resourcePath.startsWith("file:")) {
            return URI.create(resourcePath).toURL();
        } else {
            if (item.hasProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE)) {
                URL resource = new URL(item.getProperty(HippoNodeType.HIPPO_EXTENSIONSOURCE).getString());
                resource = new URL(resource, resourcePath);
                return resource;
            } else {
                return LocalHippoRepository.class.getResource(resourcePath);
            }
        }
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
        getLogger().info("Initializing nodetypes from: " + cndName);
        CompactNodeTypeDefReader<QNodeTypeDefinition,NamespaceMapping> cndReader = new HippoCompactNodeTypeDefReader<QNodeTypeDefinition, NamespaceMapping>(new InputStreamReader(cndStream), cndName, workspace.getNamespaceRegistry(), new QDefinitionBuilderFactory());
        List<QNodeTypeDefinition> ntdList = cndReader.getNodeTypeDefinitions();
        NodeTypeManagerImpl ntmgr = (NodeTypeManagerImpl) workspace.getNodeTypeManager();
        NodeTypeRegistry ntreg = ntmgr.getNodeTypeRegistry();

        for (QNodeTypeDefinition ntd : ntdList) {
            try {
                ntreg.registerNodeType(ntd);
                getLogger().info("Registered node type: " + ntd.getName().getLocalName());
            } catch (NamespaceException ex) {
                getLogger().error(ex.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), ex);
            } catch (InvalidNodeTypeDefException ex) {
                if (ex.getMessage().endsWith("already exists")) {
                    try {
                        ntreg.reregisterNodeType(ntd);
                        getLogger().info("Replaced node type: " + ntd.getName().getLocalName());
                    } catch (NamespaceException e) {
                        getLogger().error(e.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), e);
                    } catch (InvalidNodeTypeDefException e) {
                        getLogger().info(e.getMessage() + ". In " + cndName + " for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), e);
                    } catch (RepositoryException e) {
                        if (!e.getMessage().equals("not yet implemented")) {
                            getLogger().warn(e.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), e);
                        }
                    }
                } else {
                    getLogger().error(ex.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), ex);
                }
            } catch (RepositoryException ex) {
                if (!ex.getMessage().equals("not yet implemented")) {
                    getLogger().warn(ex.getMessage() + ". In " + cndName + " error for " + ntd.getName().getNamespaceURI() + ":" + ntd.getName().getLocalName(), ex);
                }
            }
        }
    }

    public boolean removeNode(Session session, String absPath, boolean save) {
        if (!absPath.startsWith("/")) {
            getLogger().warn("Not an absolute path: {}", absPath);
            return false;
        }
        if ("/".equals(absPath)) {
            getLogger().warn("Not allowed to delete rootNode from initialization");
            return false;
        }

        try {
            if (session.nodeExists(absPath)) {
                final int offset = absPath.lastIndexOf('/');
                final String nodeName = absPath.substring(offset+1);
                final String parentPath = offset == 0 ? "/" : absPath.substring(0, offset);
                final Node parent = session.getNode(parentPath);
                if (parent.getNodes(nodeName).getSize() > 1) {
                    getLogger().warn("Removing same name sibling is not supported: not removing {}", absPath);
                } else {
                    session.getNode(absPath).remove();
                }
                if (save) {
                    session.save();
                }
            }
            return true;
        } catch (RepositoryException ex) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error while removing node '" + absPath + "' : " + ex.getMessage(), ex);
            } else {
                getLogger().error("Error while removing node '" + absPath + "' : " + ex.getMessage());
            }
        }
        return false;
    }

    private boolean removeProperty(final Session session, final String absPath, final boolean save) {
        if (!absPath.startsWith("/")) {
            getLogger().warn("Not an absolute path: {}", absPath);
            return false;
        }
        try {
            if (session.propertyExists(absPath)) {
                session.getProperty(absPath).remove();
                if (save) {
                    session.save();
                }
            }
            return true;
        } catch (RepositoryException e) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error while removing property '" + absPath + "' : " + e.getMessage(), e);
            } else {
                getLogger().error("Error while removing property '" + absPath + "' : " + e.getMessage());
            }
        }
        return false;
    }

    public ImportResult initializeNodecontent(Session session, String parentAbsPath, InputStream istream, URL location) {
        return initializeNodecontent(session, parentAbsPath, istream, location, false);
    }

    public ImportResult initializeNodecontent(Session session, String parentAbsPath, InputStream istream, URL location, boolean pckg) {
        if (location != null) {
            getLogger().info("Initializing content from: {} to {}", location, parentAbsPath);
        } else {
            getLogger().info("Initializing content to {}", parentAbsPath);
        }
        File tempFile = null;
        ZipFile zipFile = null;
        InputStream esvIn = null;
        FileOutputStream out = null;
        try {
            if (session instanceof HippoSession) {
                HippoSession hippoSession = (HippoSession) session;
                int uuidBehaviour = ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW;
                int referenceBehaviour = ImportReferenceBehavior.IMPORT_REFERENCE_NOT_FOUND_REMOVE;
                if (pckg) {
                    tempFile = File.createTempFile("package", ".zip");
                    out = new FileOutputStream(tempFile);
                    IOUtils.copy(istream, out);
                    out.close();
                    out = null;
                    zipFile = new ZipFile(tempFile);
                    ContentResourceLoader contentResourceLoader = new ZipFileContentResourceLoader(zipFile);
                    esvIn = contentResourceLoader.getResourceAsStream("esv.xml");
                    return hippoSession.importEnhancedSystemViewXML(parentAbsPath, esvIn, uuidBehaviour, referenceBehaviour, contentResourceLoader);
                } else {
                    ContentResourceLoader contentResourceLoader = null;
                    if (location != null) {
                        int offset = location.getFile().indexOf(".jar!");
                        if (offset != -1) {
                            zipFile = new ZipFile(getBaseZipFileFromURL(location));
                            contentResourceLoader = new ZipFileContentResourceLoader(zipFile);
                        } else if (location.getProtocol().equals("file")) {
                            File sourceFile = new File(location.toURI());
                            contentResourceLoader = new FileContentResourceLoader(sourceFile.getParentFile());
                        }
                    }
                    return hippoSession.importEnhancedSystemViewXML(parentAbsPath, istream, uuidBehaviour, referenceBehaviour, contentResourceLoader);
                }
            } else {
                throw new IllegalStateException("Not a HippoSession");
            }
        } catch (IOException | RepositoryException | URISyntaxException e) {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("Error initializing content for " + location + " in '" + parentAbsPath + "' : " + e.getClass().getName() + ": " + e.getMessage(), e);
            } else {
                getLogger().error("Error initializing content for " + location + " in '" + parentAbsPath + "' : " + e.getClass().getName() + ": " + e.getMessage());
            }
        } finally {
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(esvIn);
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception ignore) {
                }
            }
            FileUtils.deleteQuietly(tempFile);
        }
        return null;
    }

    /**
     * Returns a {@link java.io.File} object which bases the input JAR / ZIP file URL.
     * <P>
     * For example, if the <code>url</code> represents "file:/a/b/c.jar!/d/e/f.xml", then
     * this method will return a File object representing "file:/a/b/c.jar" from the input.
     * </P>
     * @param url
     * @return
     * @throws URISyntaxException
     */
    protected File getBaseZipFileFromURL(final URL url) throws URISyntaxException {
        String file = url.getFile();
        int offset = file.indexOf(".jar!");

        if (offset == -1) {
            throw new IllegalArgumentException("Not a jar or zip url: " + url);
        }

        file = file.substring(0, offset + 4);

        if (!file.startsWith("file:")) {
            if (file.startsWith("/")) {
                file = "file://" + file;
            } else {
                file = "file:///" + file;
            }
        }

        return new File(URI.create(file));
    }

    private boolean isReloadable(Node item) throws RepositoryException {
        if (JcrUtils.getBooleanProperty(item, HIPPO_RELOADONSTARTUP, false)) {
            final String deltaDirective = StringUtils.trim(JcrUtils.getStringProperty(item, HippoNodeType.HIPPOSYS_DELTADIRECTIVE, null));
            if (deltaDirective != null && (deltaDirective.equals("combine") || deltaDirective.equals("overlay"))) {
                getLogger().error("Cannot reload initialize item {} because it is a combine or overlay delta", item.getName());
                return false;
            }
            return true;
        }
        return false;
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

    private void ensureIsLockable(final Session session, final String absPath) throws RepositoryException {
        final Node node = session.getNode(absPath);
        if (!node.isNodeType(MIX_LOCKABLE)) {
            node.addMixin(MIX_LOCKABLE);
            session.save();
        }
    }

    static class ContentFileInfo {

        final String contextNodeName;
        final String deltaDirective;

        private ContentFileInfo(final String contextNodeName, final String deltaDirective) {
            this.contextNodeName = contextNodeName;
            this.deltaDirective = deltaDirective;
        }
    }

    private class ImportWebResourceBundleFromZipTask implements PostStartupTask {

        private final Session session;
        private final PartialZipFile bundleZipFile;

        public ImportWebResourceBundleFromZipTask(final Session session, final PartialZipFile bundleZipFile) {
            this.session = session;
            this.bundleZipFile = bundleZipFile;
        }

        @Override
        public void execute() {
            final WebResourcesService service = HippoServiceRegistry.getService(WebResourcesService.class);
            if (service == null) {
                getLogger().error("Failed to import web resource bundle '{}' from '{}': missing service for '{}'",
                        bundleZipFile.getSubPath(), bundleZipFile.getName(), WebResourcesService.class.getName());
                return;
            }
            try {
                service.importJcrWebResourceBundle(session, bundleZipFile);
                session.save();
            } catch (IOException|RepositoryException|WebResourceException e) {
                getLogger().error("Failed to import web resource bundle '{}' from '{}'", bundleZipFile.getSubPath(),
                        bundleZipFile.getName(), e);
            }
        }
    }

    private class ImportWebResourceBundleFromDirectoryTask implements PostStartupTask {

        private final Session session;
        private final File bundleDir;

        public ImportWebResourceBundleFromDirectoryTask(final Session session, final File bundleDir) {
            this.session = session;
            this.bundleDir = bundleDir;
        }

        @Override
        public void execute() {
            final WebResourcesService service = HippoServiceRegistry.getService(WebResourcesService.class);
            if (service == null) {
                getLogger().error("Failed to import web resource bundle from '{}': missing service for '{}'",
                        bundleDir, WebResourcesService.class.getName());
                return;
            }
            try {
                service.importJcrWebResourceBundle(session, bundleDir);
                session.save();
            } catch (IOException|RepositoryException|WebResourceException e) {
                getLogger().error("Failed to import web resource bundle from '{}'", bundleDir, e);
            }
        }
    }

    /**
     * An input stream from a temporary file. The file is deleted when the stream is
     * closed or garbage collected.
     */
    private static class TempFileInputStream extends FilterInputStream {

        private final File file;

        public TempFileInputStream(File file) throws FileNotFoundException {
            this(new FileInputStream(file), file);
        }

        protected TempFileInputStream(FileInputStream in, File file) {
            super(in);
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            in.close();
            in = new ClosedInputStream();
            file.delete();
        }

        @Override
        protected void finalize() throws Throwable {
            close();
            super.finalize();
        }

    }

}
