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
package org.hippoecm.repository;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.fs.FileSystem;
import org.apache.jackrabbit.core.fs.FileSystemException;
import org.hippoecm.repository.api.InitializationProcessor;
import org.hippoecm.repository.api.PostStartupTask;
import org.hippoecm.repository.api.ReferenceWorkspace;
import org.hippoecm.repository.impl.DecoratorFactoryImpl;
import org.hippoecm.repository.impl.InitializationProcessorImpl;
import org.hippoecm.repository.impl.ReferenceWorkspaceImpl;
import org.hippoecm.repository.impl.SessionDecorator;
import org.hippoecm.repository.jackrabbit.RepositoryImpl;
import org.hippoecm.repository.security.HippoSecurityManager;
import org.hippoecm.repository.util.RepoUtils;
import org.onehippo.repository.modules.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hippoecm.repository.api.HippoNodeType.NT_INITIALIZEFOLDER;

public class LocalHippoRepository extends HippoRepositoryImpl {


    /** System property for overriding the repository path */
    public static final String SYSTEM_PATH_PROPERTY = "repo.path";

    /** System property for defining the base path for a non-absolute repo.path property */
    public static final String SYSTEM_BASE_PATH_PROPERTY = "repo.base.path";

    /** System property for overriding the repository config file */
    public static final String SYSTEM_CONFIG_PROPERTY = "repo.config";

    /** System property for specifying the upgrade flag */
    public static final String SYSTEM_UPGRADE_PROPERTY = "repo.upgrade";

    /** System property for enabling bootstrap */
    public static final String SYSTEM_BOOTSTRAP_PROPERTY = "repo.bootstrap";

    /** System property for overriding the servlet config file */
    public static final String SYSTEM_SERVLETCONFIG_PROPERTY = "repo.servletconfig";

    /** Default config file */
    public static final String DEFAULT_REPOSITORY_CONFIG = "repository.xml";

    /** The advised threshold on the number of modified nodes to hold in transient session state */
    public static int batchThreshold = 96;

    protected static final Logger log = LoggerFactory.getLogger(LocalHippoRepository.class);

    private LocalRepositoryImpl jackrabbitRepository = null;

    /** Whether to generate a dump.xml file of the /hippo:configuration node at shutdown */
    private final boolean dump = false;

    /** Whether to reindex after upgrading */
    private boolean upgradeReindexFlag = false;

    /** Whether to run a derived properties validation after upgrading */
    private boolean upgradeValidateFlag = true;

    private String repoPath;
    private String repoConfig;

    /** When during startup a situation is detected that a restart is required, this flag signals this, but only one restart should be appropriate */
    boolean needsRestart = false;

    private static enum UpgradeFlag {
        TRUE, FALSE, ABORT
    }

    private ModuleManager moduleManager;

    protected LocalHippoRepository() {
        super();
    }

    protected LocalHippoRepository(String repositoryConfig) throws RepositoryException {
        super();
        this.repoConfig = repositoryConfig;
    }

    protected LocalHippoRepository(String repositoryDirectory, String repositoryConfig) throws RepositoryException {
        super(repositoryDirectory);
        this.repoConfig = repositoryConfig;
    }

    public static HippoRepository create(String repositoryDirectory) throws RepositoryException {
        return create(repositoryDirectory, null);
    }

    public static HippoRepository create(String repositoryDirectory, String repositoryConfig) throws RepositoryException {
        LocalHippoRepository localHippoRepository;
        if (repositoryDirectory == null) {
            localHippoRepository = new LocalHippoRepository(repositoryConfig);
        } else {
            localHippoRepository = new LocalHippoRepository(repositoryDirectory, repositoryConfig);
        }
        localHippoRepository.initialize();
        VMHippoRepository.register(repositoryDirectory, localHippoRepository);
        return localHippoRepository;
    }

    @Override
    public String getLocation() {
        return super.getLocation();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Construct the repository path, default getWorkingDirectory() is used.
     * <p>
     * The system property repo.path can be used to override the default.
     * </p>
     * If repo.path has an absolute path, or system property repo.base.path is undefined/empty, the repo.path
     * is assumed to be an absolute path and returned as such
     * </p>
     * <p>
     * If repo.path starts with '~/' the '~' is expanded to the user.home location, thereby becoming an absolute
     * path and returns as repository path.
     * </p>
     * <p>
     * Else, when repo.path is not an abolute path and system property repo.base.path also is defined,
     * the repo.path is taken relative to the repo.base.path.
     *
     * @return The absolute path to the file repository
     */
    protected String getRepositoryPath() {
        if (repoPath != null) {
            return repoPath;
        }

        String path = System.getProperty(SYSTEM_PATH_PROPERTY);
        if (path != null) {
            if (path.isEmpty()) {
                path = null;
            }
            else {
                path = RepoUtils.stripFileProtocol(path);
                if (path.startsWith("~" + File.separator)) {
                    path = System.getProperty("user.home") + path.substring(1);
                }
            }
        }

        String basePath = path != null ? System.getProperty(SYSTEM_BASE_PATH_PROPERTY) : null;

        if (basePath != null ) {
            if (basePath.isEmpty()) {
                basePath = null;
            }
            else {
                basePath = RepoUtils.stripFileProtocol(basePath);
            }
        }

        if (path == null) {
            repoPath = getWorkingDirectory();
        }
        else if (new File(path).isAbsolute() || basePath == null) {
                repoPath = path;
        }
        else {
            repoPath = basePath + System.getProperty("file.separator") + path;
        }

        log.info("Using repository path: " + repoPath);
        return repoPath;
    }

    /**
     * If the "file://" protocol is used, the path MUST be absolute.
     * In all other cases the config file is used as a class resource.
     * @return InputStream to the repository config
     * @throws RepositoryException
     */
    private InputStream getRepositoryConfigAsStream() throws RepositoryException {

        String configPath = repoConfig;

        if (StringUtils.isEmpty(configPath)) {
            configPath = System.getProperty(SYSTEM_CONFIG_PROPERTY);
        }

        if (StringUtils.isEmpty(configPath)) {
            configPath = System.getProperty(SYSTEM_SERVLETCONFIG_PROPERTY);
        }

        if (StringUtils.isEmpty(configPath)) {
            configPath = DEFAULT_REPOSITORY_CONFIG;
        }

        if (!configPath.startsWith("file:")) {
            final URL configResource = LocalHippoRepository.class.getResource(configPath);
            log.info("Using resource repository config: " + configResource);
            try {
                return configResource.openStream();
            } catch (IOException e) {
                throw new RepositoryException("Failed to open repository configuration", e);
            }
        }

        configPath = RepoUtils.stripFileProtocol(configPath);

        log.info("Using file repository config: file:/" + configPath);

        File configFile = new File(configPath);
        try {
            return new BufferedInputStream(new FileInputStream(configFile));
        } catch (FileNotFoundException e) {
            throw new RepositoryException("Repository config not found: file:/" + configPath);
        }
    }

    private class LocalRepositoryImpl extends RepositoryImpl {
        LocalRepositoryImpl(RepositoryConfig repConfig) throws RepositoryException {
            super(repConfig);
        }
        @Override
        public Session getRootSession(String workspaceName) throws RepositoryException {
            return super.getRootSession(workspaceName);
        }
        void enableVirtualLayer(boolean enabled) throws RepositoryException {
            isStarted = enabled;
        }

        protected FileSystem getFileSystem() {
            return super.getFileSystem();
        }

        private boolean isClustered() {
            return getRepositoryConfig().getClusterConfig() != null;
        }
    }

    protected void initialize() throws RepositoryException {
        initializeStartup();
        if(needsRestart) {
            log.warn("restarting repository after upgrade cycle");
            close();
            if (upgradeReindexFlag) {
                log.warn("post migration cycle forced reindexing");
                initializeReindex();
            }
            initializeStartup();
            ((HippoSecurityManager) jackrabbitRepository.getSecurityManager()).configure();
            if (upgradeValidateFlag) {
                log.warn("post migration cycle validating content");
                final SimpleCredentials credentials = new SimpleCredentials("system", new char[]{});
                SessionDecorator session = DecoratorFactoryImpl.getSessionDecorator(
                        jackrabbitRepository.getRootSession(null).impersonate(
                                credentials), credentials);
                session.postValidation();
                session.logout();
            }
        } else {
            ((HippoSecurityManager) jackrabbitRepository.getSecurityManager()).configure();
        }
    }

    private void initializeReindex() {
        final File basedir = new File(getRepositoryPath());
        try {
            FileUtils.deleteDirectory(new File(basedir, "repository/index"));
            FileUtils.deleteDirectory(new File(basedir, "workspaces/default/index"));
        } catch (IOException e) {
            log.warn("Unable to delete index", e);
        }
    }

    private void initializeStartup() throws RepositoryException {

        Modules.setModules(new Modules(Thread.currentThread().getContextClassLoader()));

        final RepositoryConfig repConfig = RepositoryConfig.create(getRepositoryConfigAsStream(), getRepositoryPath());
        jackrabbitRepository = new LocalRepositoryImpl(repConfig);

        repository = new DecoratorFactoryImpl().getRepositoryDecorator(jackrabbitRepository);
        boolean locked = false;
        Session bootstrapSession = null;
        final InitializationProcessorImpl initializationProcessor = new InitializationProcessorImpl();

        try {
            final Session rootSession =  jackrabbitRepository.getRootSession(null);

            final boolean initializedBefore = initializedBefore(rootSession);
            if (initializedBefore) {
                switch(readUpgradeFlag()) {
                case TRUE:
                    jackrabbitRepository.enableVirtualLayer(false);
                    migrate(rootSession);
                    if (needsRestart) {
                        return;
                    }
                    break;
                case FALSE:
                    break;
                case ABORT:
                    throw new RepositoryException("ABORT");
                }
            }

            List<PostStartupTask> postStartupTasks = Collections.emptyList();

            if (!initializedBefore || isContentBootstrapEnabled()) {
                final SimpleCredentials credentials = new SimpleCredentials("system", new char[]{});
                bootstrapSession = DecoratorFactoryImpl.getSessionDecorator(rootSession.impersonate(credentials), credentials);
                initializeSystemNodeTypes(initializationProcessor, bootstrapSession, jackrabbitRepository.getFileSystem());
                if (!bootstrapSession.getRootNode().hasNode("hippo:configuration")) {
                    log.info("Initializing configuration content");
                    InputStream configuration = getClass().getResourceAsStream("configuration.xml");
                    if (configuration != null) {
                        initializationProcessor.initializeNodecontent(bootstrapSession, "/", configuration, null);
                    } else {
                        log.error("Could not initialize configuration content: ResourceAsStream not found: configuration.xml");
                    }
                    bootstrapSession.save();
                } else {
                    log.info("Initial configuration content already present");
                }
                if (locked = initializationProcessor.lock(bootstrapSession)) {
                    postStartupTasks = contentBootstrap(initializationProcessor, bootstrapSession);
                } else {
                    throw new RepositoryException("Cannot proceed with initialization: failed to obtain lock on initialization processor");
                }
            }

            jackrabbitRepository.enableVirtualLayer(true);

            moduleManager = new ModuleManager(rootSession.impersonate(new SimpleCredentials("system", new char[]{})));
            moduleManager.start();

            log.debug("Executing post-startup tasks");
            for (PostStartupTask task : postStartupTasks) {
                task.execute();
            }
        } finally {
            if (locked) {
                initializationProcessor.unlock(bootstrapSession);
            }
            if (bootstrapSession != null) {
                bootstrapSession.logout();
            }
        }
    }

    private boolean initializedBefore(final Session systemSession) throws RepositoryException {
        return systemSession.getWorkspace().getNodeTypeManager().hasNodeType(NT_INITIALIZEFOLDER);
    }

    private UpgradeFlag readUpgradeFlag() {
        UpgradeFlag upgradeFlag = UpgradeFlag.TRUE;
        String result = System.getProperty(SYSTEM_UPGRADE_PROPERTY);

        if (result != null) {
            for(String option : result.split(",")) {
                String key = "", value = "";
                if (option.contains("=")) {
                    String[] keyValue = option.split("=");
                    key = keyValue[0];
                    value = keyValue[1];
                }
                if (option.equalsIgnoreCase("abort")) {
                    upgradeFlag = UpgradeFlag.ABORT;
                } else if(key.equalsIgnoreCase("batchsize")) {
                   LocalHippoRepository.batchThreshold  = Integer.parseInt(value);
                } else if(option.equalsIgnoreCase("reindex")) {
                    upgradeReindexFlag = true;
                } else if(option.equalsIgnoreCase("validate")) {
                    upgradeValidateFlag = true;
                } else if(option.equalsIgnoreCase("skipreindex")) {
                    upgradeReindexFlag = false;
                } else if(option.equalsIgnoreCase("skipvalidate")) {
                    upgradeValidateFlag = false;
                } else if(option.equalsIgnoreCase("true")) {
                    upgradeFlag = UpgradeFlag.TRUE;
                } else if(option.equalsIgnoreCase("false")) {
                    upgradeFlag = UpgradeFlag.FALSE;
                } else {
                    log.warn("Unrecognized upgrade option \""+option+"\"");
                }
            }
        }
        switch(upgradeFlag) {
        case FALSE:
            log.info("Automatic upgrade enabled: false");
            break;
        case TRUE:
            log.info("Automatic upgrade enabled: true (reindexing "+(upgradeReindexFlag?"on":"off")+" revalidation "+(upgradeValidateFlag?"on":"off")+")");
            break;
        case ABORT:
            log.info("Automatic upgrade enabled: abort on upgrade required");
        }

        return upgradeFlag;
    }

    private boolean isContentBootstrapEnabled() {
        return Boolean.getBoolean(SYSTEM_BOOTSTRAP_PROPERTY);
    }

    private List<PostStartupTask> contentBootstrap(final InitializationProcessorImpl initializationProcessor, final Session systemSession) throws RepositoryException {
        try {
            initializationProcessor.loadExtensions(systemSession);
        } catch (IOException ex) {
            throw new RepositoryException("Could not obtain initial configuration from classpath", ex);
        }
        final List<PostStartupTask> postStartupTasks = initializationProcessor.processInitializeItems(systemSession);
        if (log.isDebugEnabled()) {
            initializationProcessor.dryRun(systemSession);
        }
        return postStartupTasks;
    }

    /**
     * Migration via the UpdaterEngine is enabled when the UpdaterEngine is on the classpath.
     * Users must explicitly add the dependency to their project in order to use the old
     * style updaters.
     */
    private void migrate(final Session jcrRootSession) throws RepositoryException {
        try {
            final Class<?> updaterEngineClass = Class.forName("org.hippoecm.repository.updater.UpdaterEngine");
            final Method migrate = updaterEngineClass.getMethod("migrate", Session.class, boolean.class);
            final SimpleCredentials credentials = new SimpleCredentials("system", new char[]{});
            final Session migrateSession = DecoratorFactoryImpl.getSessionDecorator(
                    jcrRootSession.impersonate(credentials), credentials);
            needsRestart = (Boolean) migrate.invoke(null, migrateSession, jackrabbitRepository.isClustered());
            migrateSession.logout();
        } catch (ClassNotFoundException ignore) {
            log.debug("UpdaterEngine not found");
        } catch (NoSuchMethodException|InvocationTargetException|IllegalAccessException e) {
            log.error("Unexpected error while trying to invoke UpdaterEngine", e);
        }
    }

    private void initializeSystemNodeTypes(final InitializationProcessorImpl initializationProcessor, final Session systemSession, final FileSystem fileSystem) throws RepositoryException {
        final Session syncSession = systemSession.impersonate(new SimpleCredentials("system", new char[] {}));

        final Properties checksumProperties = new Properties();
        try {
            if (fileSystem.exists("/cnd-checksums")) {
                InputStream in = null;
                try {
                    in = fileSystem.getInputStream("/cnd-checksums");
                    checksumProperties.load(in);
                } catch (IOException e) {
                    log.error("Failed to read cnd checksum file. All system cnds will be reloaded.", e);
                } finally {
                    IOUtils.closeQuietly(in);
                }
            }
        } catch (FileSystemException e) {
            log.error("Failed to read cnd checksum from the file system. All system cnds will be reloaded", e);
        }
        for(String cndName : new String[] { "hippo.cnd", "hipposys.cnd", "hipposysedit.cnd", "hippofacnav.cnd", "hipposched.cnd" }) {
            InputStream cndStream = null;
            try {
                cndStream = getClass().getClassLoader().getResourceAsStream(cndName);
                final String checksum = getChecksum(cndStream);
                cndStream.close();
                if (!checksum.equals(checksumProperties.getProperty(cndName))) {
                    log.info("Initializing nodetypes from: " + cndName);
                    cndStream = getClass().getClassLoader().getResourceAsStream(cndName);
                    initializationProcessor.initializeNodetypes(syncSession.getWorkspace(), cndStream, cndName);
                    syncSession.save();
                    checksumProperties.setProperty(cndName, checksum);
                } else {
                    log.info("No need to reload " + cndName + ": no changes");
                }
            } catch (ConstraintViolationException|InvalidItemStateException|ItemExistsException|LockException|
                    NoSuchNodeTypeException|ParseException|VersionException|AccessDeniedException|
                    NoSuchAlgorithmException|IOException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } finally {
                if (cndStream != null) { try { cndStream.close(); } catch (IOException ignore) {} }
            }

        }

        OutputStream out = null;
        try {
            out = fileSystem.getOutputStream("/cnd-checksums");
            checksumProperties.store(out, null);
        } catch (IOException|FileSystemException e) {
            log.error("Failed to store cnd checksum file.", e);
        } finally {
            IOUtils.closeQuietly(out);
        }

        syncSession.logout();
    }

    private String getChecksum(final InputStream cndStream) throws IOException, NoSuchAlgorithmException {
        final MessageDigest md5 = MessageDigest.getInstance("SHA-256");
        final byte[] buffer = new byte[1024];
        int read;
        do {
            read = cndStream.read(buffer, 0, buffer.length);
            if (read > 0) {
                md5.update(buffer, 0, read);
            }
        } while (read > 0);

        final byte[] bytes = md5.digest();
        //convert the byte to hex format
        final StringBuilder sb = new StringBuilder();
        for (final byte b : bytes) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    @Override
    public synchronized void close() {

        if (moduleManager != null) {
            moduleManager.stop();
            moduleManager = null;
        }

        Session session = null;
        if (dump && repository != null) {
            try {
                session = login();
                java.io.OutputStream out = new java.io.FileOutputStream("dump.xml");
                session.exportSystemView("/hippo:configuration", out, false, false);
            } catch (IOException|RepositoryException ex) {
                log.error("Error while dumping configuration: " + ex.getMessage(), ex);
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        }

        if (jackrabbitRepository != null) {
            try {
                jackrabbitRepository.shutdown();
                jackrabbitRepository = null;
            } catch (Exception ex) {
                log.error("Error while shuting down jackrabbitRepository: " + ex.getMessage(), ex);
            }
        }
        repository = null;

        super.close();
    }

    @Override
    public InitializationProcessor getInitializationProcessor() {
        return new InitializationProcessorImpl();
    }

    @Override
    public ReferenceWorkspace getOrCreateReferenceWorkspace() throws RepositoryException {
        return new ReferenceWorkspaceImpl(jackrabbitRepository);
    }

}
