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
package org.hippoecm.repository.decorating.client;

import java.rmi.RemoteException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.rmi.client.ClientObject;
import org.apache.jackrabbit.rmi.client.RemoteRuntimeException;

import org.hippoecm.repository.api.Document;
import org.hippoecm.repository.api.DocumentManager;
import org.hippoecm.repository.decorating.remote.RemoteDocumentManager;

public class ClientDocumentManager extends ClientObject implements DocumentManager {
    @SuppressWarnings("unused")
    private final static String SVN_ID = "$Id$";

    private RemoteDocumentManager remote;
    private Session session;

    protected ClientDocumentManager(Session session, RemoteDocumentManager remote, LocalServicingAdapterFactory factory) {
        super(factory);
        this.remote = remote;
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public Document getDocument(String category, String identifier) throws RepositoryException {
        try {
            return remote.getDocument(category, identifier);
        } catch (RemoteException ex) {
            throw new RemoteRuntimeException(ex);
        }
    }
}
