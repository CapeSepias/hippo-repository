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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.NamespaceException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.jackrabbit.core.cluster.ClusterException;
import org.apache.jackrabbit.core.cluster.Update;
import org.apache.jackrabbit.core.cluster.UpdateEventChannel;
import org.apache.jackrabbit.core.cluster.UpdateEventListener;
import org.apache.jackrabbit.core.id.ItemId;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.id.PropertyId;
import org.apache.jackrabbit.core.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.observation.EventStateCollection;
import org.apache.jackrabbit.core.observation.EventStateCollectionFactory;
import org.apache.jackrabbit.core.persistence.PersistenceManager;
import org.apache.jackrabbit.core.state.ChangeLog;
import org.apache.jackrabbit.core.state.ISMLocking;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.ItemStateCacheFactory;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.ItemStateListener;
import org.apache.jackrabbit.core.state.NoSuchItemStateException;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.SharedItemStateManager;
import org.apache.jackrabbit.core.state.StaleItemStateException;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.hippoecm.repository.dataprovider.HippoNodeId;
import org.hippoecm.repository.replication.ReplicationUpdateEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HippoSharedItemStateManager extends SharedItemStateManager {

    private static final Logger log = LoggerFactory.getLogger(HippoSharedItemStateManager.class);

    public RepositoryImpl repository;

    private List<ReplicationUpdateEventListener> updateListeners = new ArrayList<ReplicationUpdateEventListener>();
    private Name handleNodeName;
    private Name documentNodeName;
    private NodeTypeRegistry nodeTypeRegistry;

    private Set<HandleListener> handleListeners = Collections.synchronizedSet(new HashSet<HandleListener>());

    public HippoSharedItemStateManager(RepositoryImpl repository, PersistenceManager persistMgr, NodeId rootNodeId, NodeTypeRegistry ntReg, boolean usesReferences, ItemStateCacheFactory cacheFactory, ISMLocking locking) throws ItemStateException {
        super(persistMgr, rootNodeId, ntReg, usesReferences, cacheFactory, locking);
        this.repository = repository;
        this.nodeTypeRegistry = ntReg;
        super.setEventChannel(new DocumentChangeNotifyingEventChannelDecorator());
    }

    @Override
    public void setEventChannel(final UpdateEventChannel upstream) {
        UpdateEventChannel eventChannel = new DocumentChangeNotifyingEventChannelDecorator(upstream);
        super.setEventChannel(eventChannel);
    }

    // FIXME: transactional update?

    @Override
    public void update(ChangeLog local, EventStateCollectionFactory factory) throws ReferentialIntegrityException, StaleItemStateException, ItemStateException {
        super.update(local, factory);
        updateInternalListeners(local, factory);
    }

    @Override
    public void externalUpdate(ChangeLog external, EventStateCollection events) {
        super.externalUpdate(external, events);
        updateExternalListeners(external, events);
        notifyDocumentListeners(external);
    }

    void notifyDocumentListeners(ChangeLog changeLog) {
        if (handleListeners.size() == 0) {
            return;
        }

        Name handleNodeName = getHandleName(repository);
        if (handleNodeName == null) {
            return;
        }
        Name documentNodeName = getDocumentName(repository);
        if (documentNodeName == null) {
            return;
        }

        try {
            Set<NodeId> handles = new HashSet<NodeId>();
            addHandleIds(changeLog.modifiedStates(), changeLog, handles);
            for (NodeId handleId : handles) {
                for (HandleListener listener : new ArrayList<HandleListener>(handleListeners)) {
                    listener.handleModified(handleId);
                }
            }
        } catch (ItemStateException e) {
            log.error("Could not broadcast handle changes", e);
        }
    }

    private void addHandleIds(final Iterable<ItemState> states, ChangeLog changes, final Set<NodeId> handles) throws ItemStateException {
        for (ItemState state : states) {
            try {
                final NodeState nodeState;
                if (state.isNode()) {
                    // REPO-492 node states originating from an external update are incomplete:
                    // they lack the node type name that is needed to identify them as handles
                    nodeState = (NodeState) getItemState(state.getId());
                } else {
                    if (changes.isModified(state.getParentId())) {
                        continue;
                    }
                    nodeState = (NodeState) getItemState(state.getParentId());
                }
                final Name nodeTypeName = nodeState.getNodeTypeName();
                if (nodeTypeName == null) {
                    log.warn("Node type name is null for " + nodeState.getId());
                    continue;
                }
                if (handleNodeName.equals(nodeTypeName)) {
                    handles.add(nodeState.getNodeId());
                } else {
                    final EffectiveNodeType ent = nodeTypeRegistry.getEffectiveNodeType(nodeTypeName);
                    if (ent.includesNodeType(documentNodeName)) {
                        final NodeState parentState = (NodeState) getItemState(nodeState.getParentId());
                        final Name parentNodeTypeName = parentState.getNodeTypeName();
                        if (parentNodeTypeName != null && handleNodeName.equals(parentNodeTypeName)) {
                            handles.add(nodeState.getParentId());
                        } else {
                            log.debug("Skipping {}, Id: '{}'", parentNodeTypeName.toString(), parentState.getNodeId());
                        }
                    }
                }
            } catch (NoSuchItemStateException e) {
                final String message = "Unable to add add handles for modified state " + state.getId() + " because an item could not be found.";
                if (log.isDebugEnabled()) {
                    log.info(message, e);
                } else {
                    log.info(message + " (full stacktrace on debug level)");
                }
            } catch (NoSuchNodeTypeException e) {
                log.error("Could not find node type", e);
            }
        }
    }


    @Override
    public void addListener(final ItemStateListener listener) {
        super.addListener(listener);
        if (listener instanceof HandleListener) {
            handleListeners.add((HandleListener) listener);
        }
    }

    @Override
    public void removeListener(final ItemStateListener listener) {
        if (listener instanceof HandleListener) {
            handleListeners.remove(listener);
        }
        super.removeListener(listener);
    }

    private Name getHandleName(final RepositoryImpl repository) {
        if (handleNodeName == null) {
            try {
                final String hippoUri = repository.getNamespaceRegistry().getURI("hippo");
                handleNodeName = NameFactoryImpl.getInstance().create(hippoUri, "handle");
            } catch (NamespaceException e) {
                log.warn("hippo prefix not yet available");
            }
        }
        return handleNodeName;
    }

    private Name getDocumentName(final RepositoryImpl repository) {
        if (documentNodeName == null) {
            try {
                final String hippoUri = repository.getNamespaceRegistry().getURI("hippo");
                documentNodeName = NameFactoryImpl.getInstance().create(hippoUri, "document");
            } catch (NamespaceException e) {
                log.warn("hippo prefix not yet available");
            }
        }
        return documentNodeName;
    }

    public void updateInternalListeners(ChangeLog changes, EventStateCollectionFactory factory) {
        EventStateCollection events = null;
        try {
            events = factory.createEventStateCollection();
        } catch (RepositoryException e) {
            log.error("Unable to create events for for local changes", e);
        }

        synchronized (updateListeners) {
            for (ReplicationUpdateEventListener listener : updateListeners) {
                try {
                    listener.internalUpdate(changes, events.getEvents());
                } catch (RepositoryException e) {
                    log.error("Error while updating replication update event listener.", e);
                }
            }
        }
    }

    public void updateExternalListeners(ChangeLog changes, EventStateCollection events) {
        synchronized (updateListeners) {
            for (ReplicationUpdateEventListener listener : updateListeners) {
                try {
                    listener.externalUpdate(changes, events.getEvents());
                } catch (RepositoryException e) {
                    log.error("Error while updating replication update event listener.", e);
                }
            }
        }
    }

    /**
     * Register a {@link ReplicationUpdateEventListener}.
     *
     * @param listener
     */
    public void registerUpdateListener(ReplicationUpdateEventListener listener) {
        synchronized (updateListeners) {
            updateListeners.add(listener);
        }
    }

    /**
     * Unregister a {@link ReplicationUpdateEventListener}.
     *
     * @param listener
     */
    public void unRegisterUpdateListener(ReplicationUpdateEventListener listener) {
        synchronized (updateListeners) {
            updateListeners.remove(listener);
        }
    }

    @Override
    public boolean hasItemState(final ItemId id) {
        if (id.denotesNode()) {
            if (id instanceof HippoNodeId) {
                return false;
            }
        } else {
            PropertyId propertyId = (PropertyId) id;
            if (propertyId.getParentId() instanceof HippoNodeId) {
                return false;
            }
        }
        return super.hasItemState(id);
    }

    private class DocumentChangeNotifyingEventChannelDecorator implements UpdateEventChannel {
        private final UpdateEventChannel upstream;

        public DocumentChangeNotifyingEventChannelDecorator() {
            this(null);
        }

        public DocumentChangeNotifyingEventChannelDecorator(final UpdateEventChannel upstream) {
            this.upstream = upstream;
        }

        @Override
        public void updateCreated(final Update update) throws ClusterException {
            if (upstream != null) {
                upstream.updateCreated(update);
            }
        }

        @Override
        public void updatePrepared(final Update update) {
            if (upstream != null) {
                upstream.updatePrepared(update);
            }
        }

        @Override
        public void updateCommitted(final Update update, final String path) {
            if (upstream != null) {
                upstream.updateCommitted(update, path);
            }
            try {
                notifyDocumentListeners(update.getChanges());
            } catch (Throwable t) {
                log.error("Exception thrown when notifying handle listeners", t);
            }
        }

        @Override
        public void updateCancelled(final Update update) {
            if (upstream != null) {
                upstream.updateCancelled(update);
            }
        }

        @Override
        public void setListener(final UpdateEventListener listener) {
            if (upstream != null) {
                upstream.setListener(listener);
            }
        }
    }
}
