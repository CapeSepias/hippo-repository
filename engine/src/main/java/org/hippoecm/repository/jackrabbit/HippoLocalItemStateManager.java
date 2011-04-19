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
package org.hippoecm.repository.jackrabbit;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;

import javax.jcr.NamespaceException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.core.HierarchyManager;
import org.apache.jackrabbit.core.id.ItemId;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.id.PropertyId;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.observation.EventStateCollectionFactory;
import org.apache.jackrabbit.core.security.AccessManager;
import org.apache.jackrabbit.core.state.ChangeLog;
import org.apache.jackrabbit.core.state.ForkedXAItemStateManager;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.ItemStateCacheFactory;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.ItemStateManager;
import org.apache.jackrabbit.core.state.NoSuchItemStateException;
import org.apache.jackrabbit.core.state.NodeReferences;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.PropertyState;
import org.apache.jackrabbit.core.state.SharedItemStateManager;
import org.apache.jackrabbit.core.state.StaleItemStateException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.MalformedPathException;
import org.hippoecm.repository.FacetedNavigationEngine;
import org.hippoecm.repository.FacetedNavigationEngine.Context;
import org.hippoecm.repository.FacetedNavigationEngine.Query;
import org.hippoecm.repository.Modules;
import org.hippoecm.repository.SessionStateThresholdEnum;
import org.hippoecm.repository.dataprovider.DataProviderContext;
import org.hippoecm.repository.dataprovider.DataProviderModule;
import org.hippoecm.repository.dataprovider.HippoNodeId;
import org.hippoecm.repository.dataprovider.HippoVirtualProvider;
import org.hippoecm.repository.dataprovider.ParameterizedNodeId;
import org.hippoecm.repository.dataprovider.StateProviderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HippoLocalItemStateManager extends ForkedXAItemStateManager implements DataProviderContext {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    protected final Logger log = LoggerFactory.getLogger(HippoLocalItemStateManager.class);

    /** Mask pattern indicating a regular, non-virtual JCR item
     */
    static final int ITEM_TYPE_REGULAR  = 0x00;

    /** Mask pattern indicating an externally defined node, patterns can
     * be OR-ed to indicate both external and virtual nodes.
     */
    static final int ITEM_TYPE_EXTERNAL = 0x01;

    /** Mask pattern indicating a virtual node, patterns can be OR-ed to
     * indicate both external and virtual nodes.
     */
    static final int ITEM_TYPE_VIRTUAL  = 0x02;

    /** Threshold of the number of virtual state deemed to be acceptable to hold in memory.
     */
    static final int VIRTUALSTATE_THRESHOLD = 10000;

    NodeTypeRegistry ntReg;
    protected org.apache.jackrabbit.core.SessionImpl session;
    protected HierarchyManager hierMgr;
    FacetedNavigationEngine<Query, Context> facetedEngine;
    FacetedNavigationEngine.Context facetedContext;
    protected HippoLocalItemStateManager.FilteredChangeLog filteredChangeLog = null;
    protected boolean noUpdateChangeLog = false;
    protected Map<String,HippoVirtualProvider> virtualProviders;
    protected Map<Name,HippoVirtualProvider> virtualNodeNames;
    protected Set<Name> virtualPropertyNames;
    private Set<ItemState> virtualStates = new HashSet<ItemState>();
    private Map<NodeId,ItemState> virtualNodes = new HashMap<NodeId,ItemState>();
    Map<ItemId,Object> deletedExternals = new WeakHashMap<ItemId,Object>();
    private NodeId rootNodeId;
    private final boolean virtualLayerEnabled;
    private int virtualLayerEnabledCount = 0;
    private boolean virtualLayerRefreshing = true;
    private boolean parameterizedView = false;
    private StateProviderContext currentContext = null;

    public HippoLocalItemStateManager(SharedItemStateManager sharedStateMgr, EventStateCollectionFactory factory,
                                      ItemStateCacheFactory cacheFactory, String attributeName, NodeTypeRegistry ntReg, boolean enabled,
                                      NodeId rootNodeId) {
        super(sharedStateMgr, factory, attributeName, cacheFactory);
        this.ntReg = ntReg;
        this.virtualLayerEnabled = enabled;
        this.rootNodeId = rootNodeId;
        virtualProviders = new HashMap<String,HippoVirtualProvider>();
        virtualNodeNames = new HashMap<Name,HippoVirtualProvider>();
        virtualPropertyNames = new HashSet<Name>();
    }

    public boolean isEnabled() {
        return virtualLayerEnabled && virtualLayerEnabledCount == 0;
    }
    public void setEnabled(boolean enabled) {
        if(enabled) {
            --virtualLayerEnabledCount;
        } else {
            ++virtualLayerEnabledCount;
        }
    }
    public void setRefreshing(boolean enabled) {
        virtualLayerRefreshing = enabled;
    }
    
    public NodeTypeRegistry getNodeTypeRegistry() {
        return ntReg;
    }

    public HierarchyManager getHierarchyManager() {
        return hierMgr;
    }

    public FacetedNavigationEngine<FacetedNavigationEngine.Query, Context> getFacetedEngine() {
        return facetedEngine;
    }

    public FacetedNavigationEngine.Context getFacetedContext() {
        return facetedContext;
    }

    public void registerProvider(Name nodeTypeName, HippoVirtualProvider provider) {
        virtualNodeNames.put(nodeTypeName, provider);
    }

    public void registerProviderProperty(Name propName) {
        virtualPropertyNames.add(propName);
    }

    public void registerProvider(String moduleName, HippoVirtualProvider provider) {
        virtualProviders.put(moduleName, provider);
    }

    public HippoVirtualProvider lookupProvider(String moduleName) {
        return virtualProviders.get(moduleName);
    }

    public HippoVirtualProvider lookupProvider(Name nodeTypeName) {
        return virtualNodeNames.get(nodeTypeName);
    }
    
    public Name getQName(String name) throws IllegalNameException, NamespaceException {
        return session.getQName(name);
    }
    
    public Path getQPath(String path) throws MalformedPathException, IllegalNameException, NamespaceException {
        return session.getQPath(path);
    }

    private static Modules<DataProviderModule> dataProviderModules = null;

    private static synchronized Modules<DataProviderModule> getDataProviderModules(ClassLoader loader) {
        if(dataProviderModules == null) {
            dataProviderModules = new Modules<DataProviderModule>(loader, DataProviderModule.class);
        }
        return new Modules(dataProviderModules);
    }

    void initialize(org.apache.jackrabbit.core.SessionImpl session,
                    FacetedNavigationEngine<Query, Context> facetedEngine,
                    FacetedNavigationEngine.Context facetedContext) {
        this.session = session;
        this.hierMgr = session.getHierarchyManager();
        this.facetedEngine = facetedEngine;
        this.facetedContext = facetedContext;

        LinkedHashSet<DataProviderModule> providerInstances = new LinkedHashSet<DataProviderModule>();
        if (virtualLayerEnabled) {
            Modules<DataProviderModule> modules = getDataProviderModules(getClass().getClassLoader());
            for(DataProviderModule module : modules) {
                log.info("Provider module "+module.toString());
                providerInstances.add(module);
            }
        }

        for(DataProviderModule provider : providerInstances) {
            if(provider instanceof HippoVirtualProvider) {
                registerProvider(provider.getClass().getName(), (HippoVirtualProvider)provider);
            }
        }
        for(DataProviderModule provider : providerInstances) {
            try {
                provider.initialize(this);
            } catch(RepositoryException ex) {
                log.error("cannot initialize virtual provider "+provider.getClass().getName()+": "+ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void dispose() {
        facetedEngine.unprepare(facetedContext);
        super.dispose();
    }

    boolean editFakeMode = false;
    boolean editRealMode = false;
    
    @Override
    public synchronized void edit() throws IllegalStateException {
        if (!editFakeMode)
            editRealMode = true;
        boolean editPreviousMode = editFakeMode;
        editFakeMode = false;
        if (super.inEditMode()) {
            editFakeMode = editPreviousMode;
            return;
        }
        editFakeMode = editPreviousMode;
        super.edit();
    }

    @Override
    public boolean inEditMode() {
        if(editFakeMode)
            return false;
        return editRealMode;
    }

    @Override
    protected void update(ChangeLog changeLog) throws ReferentialIntegrityException, StaleItemStateException,
                                                      ItemStateException {
        filteredChangeLog = new FilteredChangeLog(changeLog);

        virtualStates.clear();
        virtualNodes.clear();
        filteredChangeLog.invalidate();
        if(!noUpdateChangeLog) {
            super.update(filteredChangeLog);
        }
        deletedExternals.putAll(filteredChangeLog.deletedExternals);
    }

    @Override
    public void update()
    throws ReferentialIntegrityException, StaleItemStateException, ItemStateException, IllegalStateException {
        super.update();
        editRealMode = false;
        try {
            editFakeMode = true;
            edit();
            FilteredChangeLog tempChangeLog = filteredChangeLog;
            filteredChangeLog = null;
            parameterizedView = false;
            if (tempChangeLog != null) {
                tempChangeLog.repopulate();
            }
        } finally {
            editFakeMode = false;
        }
    }

    void refresh() throws ReferentialIntegrityException, StaleItemStateException, ItemStateException {
        if (!inEditMode()) {
            edit();
        }
        noUpdateChangeLog = true;
        update();
        noUpdateChangeLog = false;
        editRealMode = false;
    }

    public ItemState getCanonicalItemState(ItemId id) throws NoSuchItemStateException, ItemStateException {
        try {
            if (!session.getAccessManager().isGranted(id, AccessManager.READ)) {
                return null;
            }
        } catch (RepositoryException ex) {
            return null;
        }
        return super.getItemState(id);
    }

    @Override
    public ItemState getItemState(ItemId id) throws NoSuchItemStateException, ItemStateException {
        currentContext = null;
        ItemState state;
        boolean editPreviousMode = editFakeMode;
        editFakeMode = true;
        try {
            if (id instanceof ParameterizedNodeId) {
                currentContext = new StateProviderContext(((ParameterizedNodeId)id).getParameterString());
                id = ((ParameterizedNodeId)id).getUnparameterizedNodeId();
                parameterizedView = true;
            }
            state = super.getItemState(id);
            if (deletedExternals.containsKey(id))
                return state;
            if (id instanceof HippoNodeId) {
                if (!virtualNodes.containsKey((NodeId)id)) {
                    edit();
                    NodeState nodeState = (NodeState)state;
                    if (isEnabled()) {
                        nodeState = ((HippoNodeId)id).populate(currentContext, nodeState);
                        Name nodeTypeName = nodeState.getNodeTypeName();
                        if (virtualNodeNames.containsKey(nodeTypeName) && !virtualStates.contains(state)) {
                            int type = isVirtual(nodeState);
                            if ((type & ITEM_TYPE_EXTERNAL) != 0 && (type & ITEM_TYPE_VIRTUAL) != 0) {
                                nodeState.removeAllChildNodeEntries();
                            }
                            nodeState = ((HippoNodeId)id).populate(virtualNodeNames.get(nodeTypeName), nodeState);
                        }
                        virtualNodes.put((HippoNodeId)id, nodeState);
                        store(nodeState);
                    } else {
                        // keep nodestate as is
                    }
                    return nodeState;
                }
            } else if (state instanceof NodeState) {
                NodeState nodeState = (NodeState)state;
                Name nodeTypeName = nodeState.getNodeTypeName();
                if (virtualNodeNames.containsKey(nodeTypeName) && !virtualStates.contains(state)) {
                    edit();
                    int type = isVirtual(nodeState);
                    if ((type & ITEM_TYPE_EXTERNAL) != 0) {
                        nodeState.removeAllChildNodeEntries();
                    }
                    try {
                        if (virtualLayerEnabled) {
                            if (id instanceof ParameterizedNodeId) {
                                if (isEnabled()) {
                                    state = virtualNodeNames.get(nodeTypeName).populate(new StateProviderContext(((ParameterizedNodeId)id).getParameterString()), nodeState);
                                    parameterizedView = true;
                                }
                            } else if (id instanceof HippoNodeId) {
                                if (isEnabled()) {
                                    state = ((HippoNodeId)id).populate(virtualNodeNames.get(nodeTypeName), nodeState);
                                }
                            } else {
                                if (isEnabled()) {
                                    state = virtualNodeNames.get(nodeTypeName).populate(currentContext, nodeState);
                                } else {
                                    state = virtualNodeNames.get(nodeTypeName).populate(currentContext, nodeState);
                                    ((NodeState)state).removeAllChildNodeEntries();
                                }
                            }
                        } else {
                            log.error("Populating while virtual layer disabled", new Exception());
                        }
                        virtualStates.add(state);
                        store(state);
                        return nodeState;
                    } catch (RepositoryException ex) {
                        log.error(ex.getClass().getName() + ": " + ex.getMessage(), ex);
                        throw new ItemStateException("Failed to populate node state", ex);
                    }
                }
            }
        } finally {
            currentContext = null;
            editFakeMode = editPreviousMode;
        }
        return state;
    }

    @Override
    public boolean hasItemState(ItemId id) {
        if(id instanceof HippoNodeId || id instanceof ParameterizedNodeId) {
            return true;
        } else if (id instanceof PropertyId && ((PropertyId) id).getParentId() instanceof HippoNodeId) {
            return true;
        }
        return super.hasItemState(id);
    }

    @Override
    public NodeState getNodeState(NodeId id) throws NoSuchItemStateException, ItemStateException {
        NodeState state = null;
        if (!(id instanceof HippoNodeId)) {
            try {
                state = super.getNodeState(id);
            } catch (NoSuchItemStateException ex) {
                if(!(id instanceof ParameterizedNodeId)) {
                    throw ex;
                }
            }
        }

        if(virtualNodes.containsKey(id)) {
            state = (NodeState) virtualNodes.get(id);
        } else if(state == null && id instanceof HippoNodeId) {
            boolean editPreviousMode = editFakeMode;
            editFakeMode = true;
            NodeState nodeState;
            try {
                edit();
                 if (isEnabled()) {
                    nodeState = ((HippoNodeId)id).populate(currentContext);
                    if (nodeState == null) {
                        throw new NoSuchItemStateException("Populating node failed");
                    }
                } else {
                    nodeState = populate((HippoNodeId)id);
                }

                virtualNodes.put((HippoNodeId)id, nodeState);
                store(nodeState);

                Name nodeTypeName = nodeState.getNodeTypeName();
                if(virtualNodeNames.containsKey(nodeTypeName)) {
                    int type = isVirtual(nodeState);
                    /*
                     * If a node is EXTERNAL && VIRTUAL, we are dealing with an already populated nodestate.
                     * Since the parent EXTERNAL node can impose new constaints, like an inherited filter, we
                     * first need to remove all the childNodeEntries, and then populate it again
                     */
                    if( (type  & ITEM_TYPE_EXTERNAL) != 0  && (type  & ITEM_TYPE_VIRTUAL) != 0) {
                        nodeState.removeAllChildNodeEntries();
                    }
                    state = ((HippoNodeId)id).populate(virtualNodeNames.get(nodeTypeName), nodeState);
                }
            } finally {
                editFakeMode = editPreviousMode;
            }
            return nodeState;
        }
        return state;
    }

    @Override
    public PropertyState getPropertyState(PropertyId id) throws NoSuchItemStateException, ItemStateException {
        if (id.getParentId() instanceof HippoNodeId) {
            throw new NoSuchItemStateException("Property of a virtual node cannot be retrieved from shared ISM");
        }
        return super.getPropertyState(id);
    }
    
    private NodeState populate(HippoNodeId nodeId) throws NoSuchItemStateException, ItemStateException {
        NodeState dereference = getNodeState(rootNodeId);
        NodeState state = createNew(nodeId, dereference.getNodeTypeName(), nodeId.parentId);
        state.setNodeTypeName(dereference.getNodeTypeName());
        return state;
    }

    boolean isPureVirtual(ItemId id) {
        if (id.denotesNode()) {
            if (id instanceof HippoNodeId) {
                return true;
            }
        } else {
            try {
                PropertyState propState = (PropertyState)getItemState(id);
                return (propState.getParentId() instanceof HippoNodeId);
            } catch (NoSuchItemStateException ex) {
                return true;
            } catch (ItemStateException ex) {
                return true;
            }
        }
        return false;
    }

    int isVirtual(ItemState state) {
        if(state.isNode()) {
            int type = ITEM_TYPE_REGULAR;
            if(state.getId() instanceof HippoNodeId) {
                type |= ITEM_TYPE_VIRTUAL;
            }
            if(virtualNodeNames.containsKey(((NodeState)state).getNodeTypeName())) {
                type |= ITEM_TYPE_EXTERNAL;
            }
            return type;
        } else {
            /* it is possible to do a check on type name of the property
             * using Name name = ((PropertyState)state).getName().toString().equals(...)
             * to check and return whether a property is virtual.
             *
             * FIXME: this would be better if these properties would not be
             * named for all node types, but bound to a specific node type
             * for which there is already a provider defined.
             */
            PropertyState propState = (PropertyState) state;
            if(propState.getPropertyId() instanceof HippoPropertyId) {
                return ITEM_TYPE_VIRTUAL;
            } else if(virtualPropertyNames.contains(propState.getName())) {
                return ITEM_TYPE_VIRTUAL;
            } else if(propState.getParentId() instanceof HippoNodeId) {
                return ITEM_TYPE_VIRTUAL;
            } else {
                return ITEM_TYPE_REGULAR;
            }
        }
    }

    public boolean stateThresholdExceeded(EnumSet<SessionStateThresholdEnum> interests) {
        if (interests == null || interests.contains(SessionStateThresholdEnum.PARAMETERIZED) || interests.contains(SessionStateThresholdEnum.MISCELLANEOUS)) {
            if (parameterizedView) {
                return true;
            }
        }
        if (interests == null || interests.contains(SessionStateThresholdEnum.VIEWS)) {
            int count = 0;
            ChangeLog changelog = getChangeLog();
            if (changelog != null) {
                for (Iterator iter = changelog.modifiedStates().iterator(); iter.hasNext(); iter.next()) {
                    ++count;
                }
                for (Iterator iter = changelog.addedStates().iterator(); iter.hasNext(); iter.next()) {
                    ++count;
                }
                for (Iterator iter = changelog.deletedStates().iterator(); iter.hasNext(); iter.next()) {
                    ++count;
                }
            }
            return count > VIRTUALSTATE_THRESHOLD;
        }
        return false;
    }

    class FilteredChangeLog extends ChangeLog {

        private ChangeLog upstream;
        Map<ItemId,Object> deletedExternals = new HashMap<ItemId,Object>();
        Set<ItemId> modifiedExternals = new HashSet<ItemId>();

        FilteredChangeLog(ChangeLog changelog) {
            upstream = changelog;
        }

        void invalidate() {
            if (!virtualLayerRefreshing) {
                for (ItemState state : upstream.modifiedStates()) {
                    if ((isVirtual(state) & ITEM_TYPE_EXTERNAL) != 0) {
                        forceUpdate((NodeState)state);
                    }
                }
                return;
            }
            Set<ItemId> changedParents = new HashSet<ItemId>();
            List<ItemState> deletedStates = new LinkedList<ItemState>();
            for(ItemState state : upstream.deletedStates()) {
                deletedStates.add(state);
                if (!state.isNode()) {
                    changedParents.add(state.getParentId());
                }
            }
            List<ItemState> addedStates = new LinkedList<ItemState>();
            for(ItemState state : upstream.addedStates()) {
                addedStates.add(state);
                if (!state.isNode()) {
                    changedParents.add(state.getParentId());
                }
            }
            List<ItemState> modifiedStates = new LinkedList<ItemState>();
            for(ItemState state : upstream.modifiedStates()) {
                modifiedStates.add(state);
                if (!state.isNode()) {
                    changedParents.add(state.getParentId());
                }
            }
            for(ItemState state : deletedStates) {
                if((isVirtual(state) & ITEM_TYPE_EXTERNAL) != 0) {
                    deletedExternals.put(state.getId(), null);
                    ((NodeState)state).removeAllChildNodeEntries();
                    forceUpdate(state);
                }
            }
            for(ItemState state : addedStates) {
                if((isVirtual(state) & ITEM_TYPE_VIRTUAL) != 0) {
                    if(state.isNode()) {
                        NodeState nodeState = (NodeState) state;
                        try {
                            NodeState parentNodeState = (NodeState) get(nodeState.getParentId());
                            if(parentNodeState != null) {
                                parentNodeState.removeChildNodeEntry(nodeState.getNodeId());
                                forceUpdate(nodeState);
                            }
                        } catch(NoSuchItemStateException ex) {
                        }
                    } else {
                        forceUpdate(state);
                    }
                } else if((isVirtual(state) & ITEM_TYPE_EXTERNAL) != 0) {
                    if(!deletedExternals.containsKey(state.getId()) &&
                       !HippoLocalItemStateManager.this.deletedExternals.containsKey(state.getId())) {
                        ((NodeState)state).removeAllChildNodeEntries();
                        forceUpdate((NodeState)state);
                        //((NodeState)state).removeAllChildNodeEntries();
                        if(changedParents.contains(state.getId())) {
                            modifiedExternals.add(state.getId());
                        }
                        //store(state);
                    }
                    //virtualStates.add(state);
                }
            }
            for (ItemState state : modifiedStates) {
                if ((isVirtual(state) & ITEM_TYPE_EXTERNAL) != 0) {
                    if (!deletedExternals.containsKey(state.getId()) &&
                            !HippoLocalItemStateManager.this.deletedExternals.containsKey(state.getId())) {
                        forceUpdate((NodeState)state);
                        ((NodeState)state).removeAllChildNodeEntries();
                        if(changedParents.contains(state.getId())) {
                            modifiedExternals.add(state.getId());
                        } else if(!(state.hasOverlayedState() && state.getOverlayedState().getParentId().equals(state.getParentId()))) {
                            modifiedExternals.add(state.getId());
                        }
                        //store(state);
                    }
                    //virtualStates.add(state);
                }
            }
        }

        private void repopulate() {
            for(Iterator iter = new HashSet<ItemState>(virtualStates).iterator(); iter.hasNext(); ) {
                ItemState state = (ItemState) iter.next();
                // only repopulate ITEM_TYPE_EXTERNAL, not state that are ITEM_TYPE_EXTERNAL && ITEM_TYPE_VIRTUAL
                if(((isVirtual(state) & ITEM_TYPE_EXTERNAL)) != 0 && ((isVirtual(state) & ITEM_TYPE_VIRTUAL) == 0) &&
                       !deleted(state.getId()) &&
                       !deletedExternals.containsKey(state.getId()) &&
                       !HippoLocalItemStateManager.this.deletedExternals.containsKey(state.getId())) {
                    try {
                        if(state.getId() instanceof ParameterizedNodeId) {
                            virtualNodeNames.get(((NodeState)state).getNodeTypeName()).populate(new StateProviderContext(((ParameterizedNodeId)state.getId()).getParameterString()), (NodeState)state);
                            parameterizedView = true;
                        } else if(state.getId() instanceof HippoNodeId) {
                            ((HippoNodeId)state.getId()).populate(virtualNodeNames.get(((NodeState)state).getNodeTypeName()), (NodeState)state);
                        } else {
                            //((NodeState)state).removeAllChildNodeEntries();
                            virtualNodeNames.get(((NodeState)state).getNodeTypeName()).populate(null, (NodeState)state);
                            //forceUpdate(state);
                            //store(state);
                        }
                    } catch(ItemStateException ex) {
                        log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                    } catch(RepositoryException ex) {
                        log.error(ex.getClass().getName()+": "+ex.getMessage(), ex);
                    }
                }
            }
        }

        @Override
        public void added(ItemState state) {
            upstream.added(state);
        }

        @Override
        public void modified(ItemState state) {
            upstream.modified(state);
        }

        @Override
        public void deleted(ItemState state) {
            upstream.deleted(state);
        }

        @Override
        public void modified(NodeReferences refs) {
            upstream.modified(refs);
        }

        @Override
        public boolean isModified(ItemId id) {
            return upstream.isModified(id);
        }

        @Override public ItemState get(ItemId id) throws NoSuchItemStateException {
            return upstream.get(id);
        }
        @Override public boolean has(ItemId id) {
            return upstream.has(id) && !deletedExternals.containsKey(id);
        }
        @Override public boolean deleted(ItemId id) {
            return upstream.deleted(id) && !deletedExternals.containsKey(id);
        }
        @Override public NodeReferences getReferencesTo(NodeId id) {
            return upstream.getReferencesTo( id);
        }
        @Override public Iterable<ItemState> addedStates() {
            return new FilteredStateIterator(upstream.addedStates(), false);
        }
        @Override public Iterable<ItemState> modifiedStates() {
            return new FilteredStateIterator(upstream.modifiedStates(), true);
        }
        @Override public Iterable<ItemState> deletedStates() {
            return new FilteredStateIterator(upstream.deletedStates(), false);
        }
        @Override public Iterable<NodeReferences> modifiedRefs() {
            return new FilteredReferencesIterator(upstream.modifiedRefs());
        }
        @Override public void merge(ChangeLog other) {
            upstream.merge(other);
        }
        @Override public void push() {
            upstream.push();
        }
        @Override public void persisted() {
            upstream.persisted();
        }
        @Override public void reset() {
            upstream.reset();
        }
        @Override public void disconnect() {
            upstream.disconnect();
        }
        @Override public void undo(ItemStateManager parent) {
            upstream.undo(parent);
        }
        @Override public String toString() {
            return upstream.toString();
        }

        class FilteredStateIterator implements Iterable<ItemState> {
            Iterable<ItemState> actualIterable;
            ItemState current;
            boolean modified;
            FilteredStateIterator(Iterable<ItemState> actualIterable, boolean modified) {
                this.actualIterable = actualIterable;
                current = null;
                this.modified = modified;
            }
            public Iterator<ItemState> iterator() {
                final Iterator<ItemState> actualIterator = actualIterable.iterator();
                return new Iterator<ItemState>() {
            public boolean hasNext() {
                while(current == null) {
                    if(!actualIterator.hasNext())
                        return false;
                    current = (ItemState) actualIterator.next();
                    if(needsSkip(current)) {
                        current = null;
                    }
                }
                return (current != null);
            }
            public boolean needsSkip(ItemState current) {
                if (HippoLocalItemStateManager.this.deletedExternals.containsKey(current.getId())) {
                    return true;
                }
                if ((isVirtual(current) & ITEM_TYPE_VIRTUAL) != 0) {
                    return true;
                }
                if (modified) {
                    if ((isVirtual(current) & ITEM_TYPE_EXTERNAL) != 0) {
                        return !modifiedExternals.contains(current.getId());
                    }
                }
                return false;
            }
            public ItemState next() throws NoSuchElementException {
                ItemState rtValue = null;
                while(current == null) {
                    if(!actualIterator.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    current = (ItemState) actualIterator.next();
                    if (needsSkip(current)) {
                        current = null;
                    }
                }
                rtValue = current;
                current = null;
                if(rtValue == null)
                    throw new NoSuchElementException();
                return rtValue;
            }
            public void remove() throws UnsupportedOperationException, IllegalStateException {
                actualIterator.remove();
            }
            };
            }
        }

        class FilteredReferencesIterator implements Iterable<NodeReferences> {
            Iterable<NodeReferences> actualIterable;
            NodeReferences current;
            FilteredReferencesIterator(Iterable<NodeReferences> actualIterable) {
                this.actualIterable = actualIterable;
                current = null;
            }
            public Iterator<NodeReferences> iterator() {
                final Iterator<NodeReferences> actualIterator = actualIterable.iterator();
                return new Iterator<NodeReferences>() {
            public boolean hasNext() {
                while (current == null) {
                    if (!actualIterator.hasNext())
                        return false;
                    current = (NodeReferences)actualIterator.next();
                    if (needsSkip(current)) {
                        current = null;
                    }
                }
                return (current != null);
            }
            public boolean needsSkip(NodeReferences current) {
                return isPureVirtual(current.getTargetId());
            }
            public NodeReferences next() throws NoSuchElementException {
                NodeReferences rtValue = null;
                while (current == null) {
                    if (!actualIterator.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    current = (NodeReferences)actualIterator.next();
                    if (needsSkip(current)) {
                        current = null;
                    }
                }
                rtValue = new NodeReferences(current.getTargetId());
                for (PropertyId propId : (List<PropertyId>)current.getReferences()) {
                    if (!isPureVirtual(propId)) {
                        rtValue.addReference(propId);
                    }
                }
                current = null;
                if (rtValue == null)
                    throw new NoSuchElementException();
                return rtValue;
            }
            public void remove() throws UnsupportedOperationException, IllegalStateException {
                actualIterator.remove();
            }
                };
            }
        }
    }

    @Override
    public void stateDestroyed(ItemState destroyed) {
        if(destroyed.getContainer() != this) {
            if ((isVirtual(destroyed) & ITEM_TYPE_EXTERNAL) != 0) {
                deletedExternals.put(destroyed.getId(), null);
            }
        }
        super.stateDestroyed(destroyed);
    }

    /*@Override
    public void stateModified(ItemState modified) {
        if(modified.getContainer() != this) {
            if ((isVirtual(modified) & ITEM_TYPE_EXTERNAL) != 0) {
                virtualNodes.remove(((NodeState)modified).getNodeId());
                virtualStates.remove(modified);
            }
        }
        super.stateModified(modified);
    }*/

    private void forceUpdate(ItemState state) {
        /*state.notifyStateUpdated();
        if (state.hasOverlayedState()) {
            state.setModCount(state.getOverlayedState().getModCount());
        }*/
        stateDiscarded(state);
    }

}
