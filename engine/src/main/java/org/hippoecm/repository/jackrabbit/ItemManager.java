/*
 *  Copyright 2008-2010 Hippo.
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

import org.apache.jackrabbit.core.session.SessionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemManager extends org.apache.jackrabbit.core.ItemManager {
    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    private static Logger log = LoggerFactory.getLogger(ItemManager.class);

    protected ItemManager(SessionContext context) {
        super(context);
        context.getItemStateManager().addListener(this);
    }
}
