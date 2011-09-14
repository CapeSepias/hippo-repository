/*
 *  Copyright 2010 Hippo.
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
package org.hippoecm.repository.jackrabbit.xml;

import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.spi.Name;

public class NodeInfo extends org.apache.jackrabbit.core.xml.NodeInfo {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    String mergeBehavior;
    NodeImpl originItem = null;
    String location;

    public NodeInfo(Name name, Name nodeTypeName, Name[] mixinNames,
                    NodeId id, String mergeBehavior, String location) {
        super(name, nodeTypeName, mixinNames, id);
        this.mergeBehavior = mergeBehavior;
        this.location = location;
    }

    public boolean mergeSkip() {
        return "skip".equalsIgnoreCase(mergeBehavior);
    }

    public boolean mergeOverlay() {
        return "overlay".equalsIgnoreCase(mergeBehavior);
    }

    public boolean mergeCombine() {
        return "combine".equalsIgnoreCase(mergeBehavior);
    }

    public String mergeInsertBefore() {
        if ("insert".equalsIgnoreCase(mergeBehavior)) {
            return (location != null ? location : "");
        } else {
            return null;
        }
    }

    public NodeImpl getOrigin() {
        return originItem;
    }
}
