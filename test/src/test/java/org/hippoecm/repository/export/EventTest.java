/*
 *  Copyright 2011 Hippo.
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
package org.hippoecm.repository.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.ObservationManager;

import org.apache.jackrabbit.core.observation.SynchronousEventListener;
import org.hippoecm.repository.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test is a sanity check for verifying jackrabbit
 * generates the events we rely on for automatic export.
 */
public class EventTest extends TestCase {
    
    private static final Logger log = LoggerFactory.getLogger("org.hippoecm.repository.export.test");

    private static final String[] content = new String[] {
        "/test", "nt:unstructured",
        "/test/foo", "nt:unstructured",
        "/test/foo/foo", "nt:unstructured",
        "foo", "foo",
        "/test/bar", "nt:unstructured",
        "/test/bar/bar", "nt:unstructured",
        "bar", "bar",
        "/test/baz", "nt:unstructured",
        "/test/baz/baz", "nt:unstructured",
        "baz", "baz",
        "/test/qux", "nt:unstructured",
        "qux", "qux",
        "/test/qux/qux", "nt:unstructured",
        "qux", "qux"
    };

    private ObservationManager manager;
    
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        build(session, content);
        session.save();
        manager = session.getWorkspace().getObservationManager();
    }
    
    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testEventsOnNodeRemoved() throws Exception {
        TestListener listener = null;
        try {
            listener = addListener();
            session.removeItem("/test/qux");
            session.save();
            Collection<Event> events = listener.getEvents();
            // NOTE: no removed events are generated for the properties on the nodes
            assertEquals(2, events.size());
            assertTrue(events.contains(new ExportEvent(Event.NODE_REMOVED, "/test/qux")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_REMOVED, "/test/qux/qux")));
        }
        finally {
            if (listener != null) {
                removeListener(listener);
            }
        }
    }
    
    @Test
    public void testEventsOnNodeAdded() throws Exception {
        String[] add = new String[] {
            "/test/quux", "nt:unstructured",
            "quux", "quux",
            "/test/quux/quux", "nt:unstructured",
            "quux", "quux"
        };
        TestListener listener = null;
        try {
            listener = addListener();
            build(session, add);
            session.save();
            Collection<Event> events = listener.getEvents();
            assertEquals(6, events.size());
            assertTrue(events.contains(new ExportEvent(Event.NODE_ADDED, "/test/quux")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_ADDED, "/test/quux/quux")));
            assertTrue(events.contains(new ExportEvent(Event.PROPERTY_ADDED, "/test/quux/quux")));
            assertTrue(events.contains(new ExportEvent(Event.PROPERTY_ADDED, "/test/quux/quux/quux")));
        }
        finally {
            if (listener != null) {
                removeListener(listener);
            }
        }
    }

    @Test
    public void testEventsOnNodeMove() throws Exception {
        TestListener listener = null;
        try {
            listener = addListener();
            session.move("/test/foo", "/test/foobar");
            session.save();
            Collection<Event> events = listener.getEvents();
            assertEquals(3, events.size());
            assertTrue(events.contains(new ExportEvent(Event.NODE_ADDED, "/test/foobar")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_REMOVED, "/test/foo")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_MOVED, "/test/foobar")));
        }
        finally {
            if (listener != null) {
                removeListener(listener);
            }
        }
    }

    @Test
    public void testEventsOnNodeMoveOverRemoved() throws Exception {
        TestListener listener = null;
        try {
            listener = addListener();
            session.removeItem("/test/baz");
            session.move("/test/bar", "/test/baz");
            session.save();
            Collection<Event> events = listener.getEvents();
            assertEquals(5, events.size());
            assertTrue(events.contains(new ExportEvent(Event.NODE_REMOVED, "/test/baz")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_REMOVED, "/test/baz/baz")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_ADDED, "/test/baz")));
            assertTrue(events.contains(new ExportEvent(Event.NODE_REMOVED, "/test/bar")));
        }
        finally {
            if (listener != null) {
                removeListener(listener);
            }
        }
    }

    private TestListener addListener() throws Exception {
        TestListener listener = new TestListener();
        int eventTypes = Event.NODE_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED
                | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED;
        manager.addEventListener(listener, eventTypes, "/", true, null, null, false);
        return listener;
    }
    
    private void removeListener(TestListener listener) throws Exception {
        manager.removeEventListener(listener);
    }
    
    private static class TestListener implements SynchronousEventListener {

        private final Collection<Event> events = new ArrayList<Event>();
        
        @Override
        public void onEvent(EventIterator events) {
            while (events.hasNext()) {
                Event evt = events.nextEvent();
                try {
                    this.events.add(new ExportEvent(evt));
                    if (log.isDebugEnabled()) {
                        log.debug("Received event " + evt);
                    }
                } catch (RepositoryException e) {
                    log.error("Failed to add new event ", e);
                }
            }
        }
        
        private Collection<Event> getEvents() {
            return events;
        }
    }
}
