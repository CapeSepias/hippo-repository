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
package org.hippoecm.repository;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.hippoecm.repository.api.InitializationProcessor;
import org.hippoecm.repository.impl.InitializationProcessorImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onehippo.repository.testutils.RepositoryTestCase;
import org.slf4j.helpers.NOPLogger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationTest extends RepositoryTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        while (session.getRootNode().hasNode("test")) {
            session.getRootNode().getNode("test").remove();
        }
        session.getRootNode().addNode("test", "nt:unstructured");
        session.save();
        session.refresh(false);
    }

    @After
    public void tearDown() throws Exception {
        while (session.getRootNode().hasNode("hippo:configuration/hippo:initialize/testnode")) {
            session.getRootNode().getNode("hippo:configuration/hippo:initialize/testnode").remove();
            session.save();
        }
        super.tearDown();
    }

    private void check(String expected) throws RepositoryException {
        Node node = session.getRootNode().getNode("test/propnode");
        assertFalse(node.hasProperty("hippo:multi"));
        assertTrue(node.hasProperty("hippo:single"));
        assertEquals(expected, node.getProperty("hippo:single").getString());
    }

    private void check(String[] expected) throws RepositoryException {
        Node node = session.getRootNode().getNode("test/propnode");
        assertTrue(node.hasProperty("hippo:multi"));
        assertFalse(node.hasProperty("hippo:single"));
        Value[] values = node.getProperty("hippo:multi").getValues();
        assertEquals(expected.length, values.length);
        int count = 0;
        for (Value value : values) {
            assertEquals(expected[count++], value.getString());
        }
    }

    @Test
    public void testConfiguration() throws Exception {
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test");
        node.setProperty("hippo:content", "<sv:node xmlns:sv=\"http://www.jcp.org/jcr/sv/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\" xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" sv:name=\"testnode\"><sv:property sv:name=\"jcr:primaryType\" sv:type=\"Name\"><sv:value>nt:unstructured</sv:value></sv:property></sv:node>");
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        assertTrue(session.getRootNode().getNode("test").hasNode("testnode"));
        assertEquals("done", node.getProperty("hippo:status").getString());
    }

    @Test
    public void testPropertyInitializationNoParent() throws Exception {
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"a"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        assertEquals("pending", node.getProperty("hippo:status").getString());
    }

    @Test
    public void testPropertyInitializationNewSingleSetSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"b"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check("b");
    }

    @Test
    public void testPropertyInitializationNewSingleSetMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"c", "d"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        assertEquals("pending", node.getProperty("hippo:status").getString());
    }

    @Test
    public void testPropertyInitializationNewSingleSetNone() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        assertFalse(session.getRootNode().getNode("test/propnode").hasProperty("hippo:single"));
        assertFalse(session.getRootNode().getNode("test/propnode").hasProperty("hippo:multi"));
    }

    @Test
    public void testPropertyInitializationNewSingleAddSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropadd", new String[] {"e"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        assertEquals("pending", node.getProperty("hippo:status").getString());
    }

    @Test
    public void testPropertyInitializationNewSingleAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropadd", new String[] {"f", "g"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        assertEquals("pending", node.getProperty("hippo:status").getString());
    }

    @Test
    public void testPropertyInitializationNewSingleSetAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"h", "i"});
        node.setProperty("hippo:contentpropadd", new String[] {"j", "k"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        assertEquals("pending", node.getProperty("hippo:status").getString());
    }

    // Test for managing a non-existing multi value property
    @Test
    public void testPropertyInitializationNewMultiSetSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {"l"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        check(new String[] {"l"});
    }

    @Test
    public void testPropertyInitializationNewMultiSetMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {"m", "n"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"m", "n"});
    }

    @Test
    public void testPropertyInitializationNewMultiSetNone() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {});
    }

    @Test
    public void testPropertyInitializationNewMultiAddSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropadd", new String[] {"o"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"o"});
    }

    @Test
    public void testPropertyInitializationNewMultiAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropadd", new String[] {"p", "q"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"p", "q"});
    }

    @Test
    public void testPropertyInitializationNewMultiSetAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {"r", "s"});
        node.setProperty("hippo:contentpropadd", new String[] {"t", "u"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"r", "s", "t", "u"});
    }

    // Test for managing a existing single value property
    @Test
    public void testPropertyInitializationExistingSingleSetSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:single", "z");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"B"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check("B");
    }

    @Test
    public void testPropertyInitializationExistingSingleSetMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:single", "z");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"C", "D"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        check("z");
    }

    @Test
    public void testPropertyInitializationExistingSingleSetNone() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:single", "z");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        assertFalse(session.getRootNode().getNode("test/propnode").hasProperty("hippo:single"));
        assertFalse(session.getRootNode().getNode("test/propnode").hasProperty("hippo:multi"));
    }

    @Test
    public void testPropertyInitializationExistingSingleAddSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:single", "z");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropadd", new String[] {"E"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        check("z");
    }

    @Test
    public void testPropertyInitializationExistingSingleAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:single", "z");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropadd", new String[] {"F", "G"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        check("z");
    }

    @Test
    public void testPropertyInitializationExistingSingleSetAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:single", "z");
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:single");
        node.setProperty("hippo:contentpropset", new String[] {"H", "I"});
        node.setProperty("hippo:contentpropadd", new String[] {"J", "K"});
        node.setProperty("hippo:status", "pending");
        session.save();
        // expecting error output: set noplogger
        InitializationProcessor processor = new InitializationProcessorImpl(new NOPLogger() {});
        processor.processInitializeItems(session);
        check("z");
    }

    // Test for managing a existing multi value property
    @Test
    public void testPropertyInitializationExistingMultiSetSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:multi", new String[] {"x", "y"});
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {"L"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"L"});
    }

    @Test
    public void testPropertyInitializationExistingMultiSetMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:multi", new String[] {"x", "y"});
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {"M", "N"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"M", "N"});
    }

    @Test
    public void testPropertyInitializationExistingMultiSetNone() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:multi", new String[] {"x", "y"});
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {});
    }

    @Test
    public void testPropertyInitializationExistingMultiAddSingle() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:multi", new String[] {"x", "y"});
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropadd", new String[] {"O"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"x", "y", "O"});
    }

    @Test
    public void testPropertyInitializationExistingMultiAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:multi", new String[] {"x", "y"});
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropadd", new String[] {"P", "Q"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[] {"x", "y", "P", "Q"});
    }

    @Test
    public void testPropertyInitializationExistingMultiSetAddMulti() throws Exception {
        session.getRootNode().getNode("test").addNode("propnode", "hippo:testproplvlinit").setProperty("hippo:multi", new String[] {"x", "y"});
        Node node = session.getRootNode().addNode("hippo:configuration/hippo:initialize/testnode", "hipposys:initializeitem");
        node.setProperty("hippo:contentroot", "/test/propnode/hippo:multi");
        node.setProperty("hippo:contentpropset", new String[] {"R", "S"});
        node.setProperty("hippo:contentpropadd", new String[] {"T", "U"});
        node.setProperty("hippo:status", "pending");
        session.save();
        InitializationProcessor processor = new InitializationProcessorImpl();
        processor.processInitializeItems(session);
        check(new String[]{"R", "S", "T", "U"});
    }

    private class IgnoreErrorLogger extends NOPLogger {
    }
}
