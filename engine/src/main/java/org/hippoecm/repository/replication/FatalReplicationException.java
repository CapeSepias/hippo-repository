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
package org.hippoecm.repository.replication;

/**
 * This exception is thrown by a {@link Replicator} when there is no use in trying
 * the same operation again. For example when a node is referenced which is not going 
 * to be replicated.
 */
public class FatalReplicationException extends RuntimeException {

    /**
     * Serial
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this class with <code>null</code> as its
     * detail message.
     */
    public FatalReplicationException() {
        super();
    }

    /**
     * Constructs a new instance of this class with the specified detail
     * message.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public FatalReplicationException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance of this class with the specified detail
     * message and root cause.
     *
     * @param message   the detail message. The detail message is saved for
     *                  later retrieval by the {@link #getMessage()} method.
     * @param rootCause root failure cause
     */
    public FatalReplicationException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

    /**
     * Constructs a new instance of this class with the specified root cause.
     *
     * @param rootCause root failure cause
     */
    public FatalReplicationException(Throwable rootCause) {
        super(rootCause);
    }
}
