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
package org.hippoecm.repository;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.LoginException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.hippoecm.checker.Checker;
import org.hippoecm.repository.decorating.checked.CheckedDecoratorFactory;
import org.hippoecm.repository.ext.DaemonModule;
import org.hippoecm.repository.impl.DecoratorFactoryImpl;
import org.hippoecm.repository.impl.SessionDecorator;
import org.hippoecm.repository.jackrabbit.HippoSessionItemStateManager;
import org.hippoecm.repository.jackrabbit.RepositoryImpl;
import org.hippoecm.repository.security.SecurityManager;
import org.hippoecm.repository.updater.UpdaterEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hippoecm.repository.api.HippoNodeType.NT_INITIALIZEFOLDER;

public class LocalHippoRepository extends HippoRepositoryImpl {
    /** SVN id placeholder */
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    /** System property for overriding the repository path */
    public static final String SYSTEM_PATH_PROPERTY = "repo.path";

    /** System property for overriding the repository config file */
    public static final String SYSTEM_CONFIG_PROPERTY = "repo.config";

    /** System property for overriding the repository config file */
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
    private DecoratorFactoryImpl hippoRepositoryFactory;

    /** Whether to generate a dump.xml file of the /hippo:configuration node at shutdown */
    private final boolean dump = false;

    /** Whether to perform an automatic upgrade from previous releases */
    private UpgradeFlag upgradeFlag = UpgradeFlag.TRUE;

    /** Whether to reindex after upgrading */
    private boolean upgradeReindexFlag = false;

    /** Whether to run a derived properties validation after upgrading */
    private boolean upgradeValidateFlag = true;

    public boolean stateThresholdExceeded(Session session, EnumSet<SessionStateThresholdEnum> interests) {
        session = org.hippoecm.repository.decorating.SessionDecorator.unwrap(session);
        session = org.hippoecm.repository.decorating.checked.SessionDecorator.unwrap(session);
        session = org.hippoecm.repository.impl.SessionDecorator.unwrap(session);
        if(session instanceof org.apache.jackrabbit.core.SessionImpl) {
            HippoSessionItemStateManager sessionISM = (HippoSessionItemStateManager) (session instanceof org.hippoecm.repository.jackrabbit.XASessionImpl ? ((org.hippoecm.repository.jackrabbit.XASessionImpl)session).getItemStateManager() : ((org.hippoecm.repository.jackrabbit.SessionImpl)session).getItemStateManager());
            return sessionISM.stateThresholdExceeded(interests);
        }
        return false;
    }

    private static enum UpgradeFlag {
        TRUE, FALSE, ABORT
    }

    /** When during startup a situation is detected that a restart is required, this flag signals this, but only one restart should be appropriate */
    boolean needsRestart = false;

    List<DaemonModule> daemonModules = new LinkedList<DaemonModule>();

    protected LocalHippoRepository() throws RepositoryException {
        super();
    }

    protected LocalHippoRepository(String location) throws RepositoryException {
        super(location);
    }

    public static HippoRepository create(String location) throws RepositoryException {
        LocalHippoRepository localHippoRepository;
        if(location == null)
            localHippoRepository = new LocalHippoRepository();
        else
            localHippoRepository = new LocalHippoRepository(location);
        localHippoRepository.initialize();
        VMHippoRepository.register(location, localHippoRepository);
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
     * If the system property repo.path can be used to override the default.
     * If repo.path starts with a '.' then the path is taken relative to the
     * getWorkingDirectory().
     * @return The absolute path to the file repository
     */
    private String getRepositoryPath() {
        String path = System.getProperty(LocalHippoRepository.SYSTEM_PATH_PROPERTY);

        if (path == null || "".equals(path)) {
            path = getWorkingDirectory();
        } else if (path.charAt(0) == '.') {
            // relative path
            path = getWorkingDirectory() + System.getProperty("file.separator") + path;
        } else if (path.startsWith("file://")) {
            path = path.substring(6);
        } else if (path.startsWith("file:/")) {
            path = path.substring(5);
        } else if (path.startsWith("file:")) {
            path = "/" + path.substring(5);
        }
        log.info("Using repository path: " + path);
        return path;
    }

    /**
     * If the "file://" protocol is used, the path MUST be absolute.
     * In all other cases the config file is used as a class resource.
     * @return InputStream to the repository config
     * @throws RepositoryException
     */
    private static InputStream getRepositoryConfigAsStream() throws RepositoryException {
        // get config from system prop
        String configName = System.getProperty(SYSTEM_CONFIG_PROPERTY);

        // if not set try to use the servletconfig
        if (configName == null || "".equals(configName)) {
            configName = System.getProperty(SYSTEM_SERVLETCONFIG_PROPERTY);
        }

        // if still not set use default
        if (configName == null || "".equals(configName)) {
            log.info("Using default repository config: " + DEFAULT_REPOSITORY_CONFIG);
            return LocalHippoRepository.class.getResourceAsStream(DEFAULT_REPOSITORY_CONFIG);
        }

        // resource
        if (!configName.startsWith("file:")) {
            log.info("Using resource repository config: " + configName);
            return LocalHippoRepository.class.getResourceAsStream(configName);
        }

        // parse file name
        if (configName.startsWith("file://")) {
            configName = configName.substring(6);
        } else if (configName.startsWith("file:/")) {
            configName = configName.substring(5);
        } else if (configName.startsWith("file:")) {
            configName = "/" + configName.substring(5);
        }
        log.info("Using file repository config: file:/" + configName);

        // get the bufferedinputstream
        File configFile = new File(configName);
        try {
            FileInputStream fis = new FileInputStream(configFile);
            return new BufferedInputStream(fis);
        } catch (FileNotFoundException e) {
            throw new RepositoryException("Repository config not found: file:/" + configName);
        }
    }
    
    public static boolean check(String location, boolean fix) throws RepositoryException {
        LocalHippoRepository repository= (location != null ? new LocalHippoRepository(location) : new LocalHippoRepository());
        RepositoryConfig repConfig = RepositoryConfig.create(repository.getRepositoryConfigAsStream(), repository.getRepositoryPath());
        Checker checker = new Checker(repConfig);
        return checker.check(fix);
    }

    public boolean check(boolean fix) throws RepositoryException {
        RepositoryConfig repConfig = RepositoryConfig.create(getRepositoryConfigAsStream(), getRepositoryPath());
        Checker checker = new Checker(repConfig);
        return checker.check(fix);
    }

    public static void check(String location, String[] arguments) throws RepositoryException {
        LocalHippoRepository repository= (location != null ? new LocalHippoRepository(location) : new LocalHippoRepository());
        RepositoryConfig repConfig = RepositoryConfig.create(repository.getRepositoryConfigAsStream(), repository.getRepositoryPath());
        Checker checker = new Checker(repConfig);
        checker.checkRepository(arguments);
    }

    private class LocalRepositoryImpl extends RepositoryImpl {
        LocalRepositoryImpl(RepositoryConfig repConfig) throws RepositoryException {
            super(repConfig);
        }
        @Override
        protected Session getRootSession(String workspaceName) throws RepositoryException {
            return super.getRootSession(workspaceName);
        }
        void enableVirtualLayer(boolean enabled) throws RepositoryException {
            isStarted = enabled;
        }

        boolean isClustered() {
            return getRepositoryConfig().getClusterConfig() != null;
        }
    }

    static private void delete(File path) {
        if(path.exists()) {
            if(path.isDirectory()) {
                File[] files = path.listFiles();
                for(int i=0; i<files.length; i++)
                    delete(files[i]);
            }
            if (!path.delete()) {
                log.warn("Unable to delete path: {}", path);
            }
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
            ((SecurityManager) jackrabbitRepository.getSecurityManager()).init();
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
            ((SecurityManager) jackrabbitRepository.getSecurityManager()).init();
        }
    }

    private void initializeReindex() {
        File basedir = new File(getRepositoryPath());
        delete(new File(basedir, "repository/index"));
        delete(new File(basedir, "workspaces/default/index"));
    }

    private void initializeStartup() throws RepositoryException {

        Modules.setModules(new Modules(Thread.currentThread().getContextClassLoader()));

        jackrabbitRepository = new LocalRepositoryImpl(RepositoryConfig.create(getRepositoryConfigAsStream(),
                getRepositoryPath()));
        repository = jackrabbitRepository;

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

        hippoRepositoryFactory = new DecoratorFactoryImpl();
        repository = hippoRepositoryFactory.getRepositoryDecorator(repository);
        repository = new CheckedDecoratorFactory().getRepositoryDecorator(repository);

        try {
            // get the current root/system session for the default workspace for namespace and nodetypes init
            Session jcrRootSession =  jackrabbitRepository.getRootSession(null);

            if(!jcrRootSession.getRootNode().isNodeType("mix:referenceable")) {
                jcrRootSession.getRootNode().addMixin("mix:referenceable");
                jcrRootSession.save();
            }

            boolean initializedBefore = initializedBefore(jcrRootSession);

            final SimpleCredentials systemCredentials = new SimpleCredentials("system", new char[]{});
            if (initializedBefore) {
                switch(upgradeFlag) {
                case TRUE:
                    jackrabbitRepository.enableVirtualLayer(false);
                    Session migrateSession = DecoratorFactoryImpl.getSessionDecorator(jcrRootSession.impersonate(systemCredentials), systemCredentials);
                    needsRestart = UpdaterEngine.migrate(migrateSession, jackrabbitRepository.isClustered());
                    migrateSession.logout();
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

            if (!initializedBefore || isContentBootstrapEnabled()) {
                final SessionDecorator bootstrapSession = DecoratorFactoryImpl.getSessionDecorator(jcrRootSession.impersonate(systemCredentials), systemCredentials);
                initializeSystemNodeTypes(bootstrapSession);
                bootstrapContent(bootstrapSession);
                bootstrapSession.logout();
            }

            jackrabbitRepository.enableVirtualLayer(true);

            for(DaemonModule module : new Modules<DaemonModule>(Modules.getModules(), DaemonModule.class)) {
                Session moduleSession = jcrRootSession.impersonate(systemCredentials);
                moduleSession = DecoratorFactoryImpl.getSessionDecorator(moduleSession, systemCredentials);
                try {
                    module.initialize(moduleSession);
                    daemonModules.add(module);
                } catch(RepositoryException ex) {
                    log.error("Module "+module.toString()+" failed to initialize", ex);
                }
            }

        } catch (LoginException ex) {
            log.error("no access to repository by repository itself", ex);
        }
    }

    private void bootstrapContent(final Session systemSession) throws RepositoryException {

        if (!systemSession.getRootNode().hasNode("hippo:configuration")) {
            log.info("Initializing configuration content");
            InputStream configuration = getClass().getResourceAsStream("configuration.xml");
            if (configuration != null) {
                LoadInitializationModule.initializeNodecontent(systemSession, "/", configuration, getClass().getPackage().getName() + ".configuration.xml");
            } else {
                log.error("Could not initialize configuration content: ResourceAsStream not found: configuration.xml");
            }
            systemSession.save();
        } else {
            log.info("Initial configuration content already present");
        }

        // load all extension resources
        try {
            LoadInitializationModule.locateExtensionResources(systemSession, systemSession.getRootNode().getNode("hippo:configuration/hippo:initialize"));
        } catch (IOException ex) {
            throw new RepositoryException("Could not obtain initial configuration from classpath", ex);
        }
        LoadInitializationModule.refresh(systemSession);
        if (log.isDebugEnabled()) {
            LoadInitializationModule.query(systemSession);
        }

    }

    private void initializeSystemNodeTypes(final Session systemSession) throws RepositoryException {
        Session syncSession = systemSession.impersonate(new SimpleCredentials("system", new char[] {}));

        for(String cndName : new String[] { "hippo.cnd", "hipposys.cnd", "hipposysedit.cnd", "hippofacnav.cnd" }) {
            try {
                log.info("Initializing nodetypes from: " + cndName);
                LoadInitializationModule.initializeNodetypes(syncSession.getWorkspace(), getClass().getClassLoader().getResourceAsStream(cndName), cndName);
                syncSession.save();
            } catch (ConstraintViolationException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (InvalidItemStateException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (ItemExistsException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (LockException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (NoSuchNodeTypeException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (ParseException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (VersionException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            } catch (AccessDeniedException ex) {
                throw new RepositoryException("Could not initialize repository with hippo node types", ex);
            }
        }

        syncSession.logout();
    }

    private boolean initializedBefore(final Session systemSession) throws RepositoryException {
        return systemSession.getWorkspace().getNodeTypeManager().hasNodeType(NT_INITIALIZEFOLDER);
    }

    private boolean isContentBootstrapEnabled() {
        return Boolean.getBoolean(SYSTEM_BOOTSTRAP_PROPERTY);
    }

    @Override
    public synchronized void close() {
        for(DaemonModule module : daemonModules) {
            module.shutdown();
        }
        daemonModules.clear();

        Session session;
        if (dump && repository != null) {
            try {
                session = login();
                java.io.OutputStream out = new java.io.FileOutputStream("dump.xml");
                session.exportSystemView("/hippo:configuration", out, false, false);
            } catch (IOException ex) {
                log.error("Error while dumping comfiguration: " + ex.getMessage(), ex);
            } catch (RepositoryException ex) {
                log.error("Error while dumping comfiguration: " + ex.getMessage(), ex);
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
}
