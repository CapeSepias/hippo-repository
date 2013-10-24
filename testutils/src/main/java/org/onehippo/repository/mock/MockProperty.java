/*
 *  Copyright 2012-2013 Hippo B.V. (http://www.onehippo.com)
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
package org.onehippo.repository.mock;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.PropertyDefinition;

/**
 * Mock version of a {@link Property}. It only supports string properties.
 * All methods that are not implemented throw an {@link UnsupportedOperationException}.
 */
public class MockProperty extends MockItem implements Property {

    private int type;
    private List<MockValue> values;
    private boolean multiple;

    MockProperty(String name, int type) {
        super(name);
        this.type = type;
        this.values = new ArrayList<MockValue>();
    }

    @Override
    public void setValue(final Value value) throws RepositoryException {
        values.clear();
        values.add(new MockValue(value));
        multiple = false;
    }

    @Override
    public void setValue(final Value[] values) throws RepositoryException {
        this.values.clear();
        for (Value value : values) {
            this.values.add(new MockValue(value));
        }
        multiple = true;
    }

    @Override
    public void setValue(final String value) {
        this.values.clear();
        this.values.add(new MockValue(value));
        multiple = false;
    }

    @Override
    public void setValue(final String[] values) {
        this.values.clear();
        for (String value: values) {
            this.values.add(new MockValue(value));
        }
        multiple = true;
    }

    @Override
    public Value getValue() throws ValueFormatException {
        if (values.isEmpty()) {
            throw new IllegalStateException("Property is not initialized");
        }
        if (values.size() > 1) {
            throw new ValueFormatException("Property is multi-valued");
        }
        return values.get(0);
    }

    @Override
    public Value[] getValues() {
        Value[] result = new Value[values.size()];
        return values.toArray(result);
    }

    @Override
    public String getString() throws RepositoryException {
        return getValue().getString();
    }

    @Override
    public boolean isMultiple() {
        return multiple;
    }

    @Override
    public boolean isNode() {
        return false;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public void remove() throws RepositoryException {
        String name = getName();
        getMockParent().removeProperty(name);
    }

    @Override
    public PropertyDefinition getDefinition() {
        return new MockPropertyDefinition(getName(), multiple);
    }

    // REMAINING METHODS ARE NOT IMPLEMENTED

    @Override
    public void setValue(final InputStream value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final Binary value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final double value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final BigDecimal value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final Calendar value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(final Node value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Binary getBinary() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getDecimal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Calendar getDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBoolean() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Node getNode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Property getProperty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLength() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long[] getLengths() {
        throw new UnsupportedOperationException();
    }

}
