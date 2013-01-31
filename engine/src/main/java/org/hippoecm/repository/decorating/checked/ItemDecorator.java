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
package org.hippoecm.repository.decorating.checked;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.ItemVisitor;
import javax.jcr.Node;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

/**
 */
public abstract class ItemDecorator extends AbstractDecorator implements Item {

    @SuppressWarnings("unused")
    private static final String SVN_ID = "$Id$";

    /**
     * The underlying item to decorate.
     */
    protected Item item;

    protected ItemDecorator(DecoratorFactory factory, SessionDecorator session, Item item) {
        super(factory, session);
        this.item = item;
    }

    /**
     * Returns the underlying item that <code>this</code>
     * <code>ItemDecorator</code> decorates.
     *
     * @return the underlying item.
     */
    public Item unwrap() {
        return item;
    }

    /**
     * Returns the underlying <code>item</code> of the <code>item</code>
     * that decorates it. Unwrapping <code>null</code> returns <code>null</code>.
     *
     * @param item decorates the underlying item.
     * @return the underlying item.
     * @throws IllegalStateException if <code>item</code> is not of type
     *                               {@link ItemDecorator}.
     */
    public static Item unwrap(Item item) {
        if (item == null) {
            return null;
        }
        if (item instanceof ItemDecorator) {
            item = ((ItemDecorator) item).unwrap();
        } else {
            throw new IllegalStateException("item is not of type ItemDecorator");
        }
        return item;
    }

    /**
     * Returns the decorated session through which this item decorator
     * was acquired.
     *
     * @return decorated session
     */
    public Session getSession() throws RepositoryException {
        return session;
    }

    /** {@inheritDoc} */
    public String getPath() throws RepositoryException {
        check();
        return item.getPath();
    }

    /** {@inheritDoc} */
    public String getName() throws RepositoryException {
        check();
        return item.getName();
    }

    /** {@inheritDoc} */
    public Item getAncestor(int depth) throws ItemNotFoundException, AccessDeniedException, RepositoryException {
        check();
        Item ancestor = item.getAncestor(depth);
        return factory.getItemDecorator(session, ancestor);
    }

    /** {@inheritDoc} */
    public Node getParent() throws ItemNotFoundException, AccessDeniedException, RepositoryException {
        check();
        Node parent = item.getParent();
        return factory.getNodeDecorator(session, parent);
    }

    /** {@inheritDoc} */
    public int getDepth() throws RepositoryException {
        check();
        return item.getDepth();
    }

    /** {@inheritDoc} */
    public boolean isNode() {
        return item.isNode();
    }

    /** {@inheritDoc} */
    public boolean isNew() {
        return item.isNew();
    }

    /** {@inheritDoc} */
    public boolean isModified() {
        return item.isModified();
    }

    /** {@inheritDoc} */
    public boolean isSame(Item otherItem) throws RepositoryException {
        check();
        return item.isSame(unwrap(otherItem));
    }

    /** {@inheritDoc} */
    public void accept(ItemVisitor visitor) throws RepositoryException {
        check();
        item.accept(factory.getItemVisitorDecorator(session, visitor));
    }

    /** {@inheritDoc} */
    public void save() throws AccessDeniedException, ConstraintViolationException, InvalidItemStateException,
            ReferentialIntegrityException, VersionException, LockException, RepositoryException {
        check();
        item.save();
    }

    /** {@inheritDoc} */
    public void refresh(boolean keepChanges) throws InvalidItemStateException, RepositoryException {
        check();
        item.refresh(keepChanges);
    }

    /** {@inheritDoc} */
    public void remove() throws VersionException, LockException, RepositoryException {
        check();
        item.remove();
    }

    public boolean equals(Object obj) {
        if (obj instanceof ItemDecorator) {
            ItemDecorator other = (ItemDecorator) obj;
            return item.equals(other.unwrap());
        }
        return false;
    }

    public int hashCode() {
        return item.hashCode();
    }
}