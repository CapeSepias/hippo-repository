package org.apache.jackrabbit.core.query.lucene;

import java.io.IOException;
import java.util.BitSet;

import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.query.lucene.ForeignSegmentDocId;
import org.apache.jackrabbit.core.query.lucene.HierarchyResolver;
import org.apache.jackrabbit.core.query.lucene.MultiIndexReader;
import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.Filter;

/**
 * <code>HippoIndexReader</code> wraps an index reader and
 * {@link ReleaseableIndexReader#release() releases} the underlying reader
 * when a client calls {@link #close()} on this reader. This allows reusing
 * of the underlying index reader instance.
 * Also filters out hits that hits that are not authorized.
 */
public class HippoIndexReader extends FilterIndexReader implements HierarchyResolver, MultiIndexReader {

    /**
     * The hierarchy resolver.
     */
    private final HierarchyResolver resolver;

    /**
     * The underlying index reader exposed as a {@link MultiIndexReader}.
     */
    private final MultiIndexReader reader;

    private final BitSet filter;

    /**
     * Creates a new <code>HippoIndexReader</code>. The passed index reader
     * must also implement the interfaces {@link HierarchyResolver} and
     * {@link MultiIndexReader}.
     *
     * @param in the underlying index reader.
     * @throws IllegalArgumentException if <code>in</code> does not implement
     *                                  {@link HierarchyResolver} and
     *                                  {@link MultiIndexReader}.
     */
    public HippoIndexReader(IndexReader in, BitSet filter) {
        super(in);
        if (!(in instanceof MultiIndexReader)) {
            throw new IllegalArgumentException("IndexReader must also implement MultiIndexReader");
        }
        if (!(in instanceof HierarchyResolver)) {
            throw new IllegalArgumentException("IndexReader must also implement HierarchyResolver");
        }
        this.resolver = (HierarchyResolver) in;
        this.reader = (MultiIndexReader) in;
        this.filter = filter;
    }



    /**
     * Overwrite <code>termDocs(Term)</code> and forward the call to the
     * wrapped reader.
     */
    @Override
    public TermDocs termDocs(Term term) throws IOException {
        return in.termDocs(term);
    }

    //--------------------------< FilterIndexReader >---------------------------

    /**
     * Calls release on the underlying {@link MultiIndexReader} instead of
     * closing it.
     *
     * @throws IOException if an error occurs while releaseing the underlying
     *                     index reader.
     */
    protected void doClose() throws IOException {
        reader.release();
    }

    //------------------------< HierarchyResolver >-----------------------------

    /**
     * {@inheritDoc}
     */
    public int[] getParents(int n, int[] docNumbers) throws IOException {
        return resolver.getParents(n, docNumbers);
    }

    //-------------------------< MultiIndexReader >-----------------------------

    /**
     * {@inheritDoc}
     */
    public IndexReader[] getIndexReaders() {
        return reader.getIndexReaders();
    }

    /**
     * {@inheritDoc}
     */
    public ForeignSegmentDocId createDocId(NodeId id) throws IOException {
        return reader.createDocId(id);
    }

    /**
     * {@inheritDoc}
     */
    public int getDocumentNumber(ForeignSegmentDocId docId) throws IOException {
        return reader.getDocumentNumber(docId);
    }

    /**
     * {@inheritDoc}
     */
    public void release() throws IOException {
        reader.release();
    }

    @Override
    public boolean isDeleted(final int n) {
        return super.isDeleted(n) || !filter.get(n);
    }
}
