package org.apache.solr.search.span;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopTermsRewrite;
import org.apache.lucene.search.spans.BoostSpanTermQuery;
import org.apache.lucene.search.spans.BoostedSpanOrQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.solr.search.BoostedTopTermsRewrite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BoostedSpanMultiTermQueryWrapper<Q extends MultiTermQuery> extends SpanQuery {

    protected final Q query;
    private SpanMultiTermQueryWrapper.SpanRewriteMethod rewriteMethod;
    long docCount;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public BoostedSpanMultiTermQueryWrapper(Q query, long docCount) {
        this.query = Objects.requireNonNull(query);
        this.rewriteMethod = selectRewriteMethod(query, docCount);
        this.docCount = docCount;
    }

    private static SpanMultiTermQueryWrapper.SpanRewriteMethod selectRewriteMethod(MultiTermQuery query, long docCount) {
        MultiTermQuery.RewriteMethod method = query.getRewriteMethod();
        if (method instanceof TopTermsRewrite) {
            final int pqsize = ((TopTermsRewrite<?>) method).getSize();
            return new BoostedTopTermsSpanBooleanQueryRewrite(pqsize, docCount);
        } else {
            return SpanMultiTermQueryWrapper.SCORING_SPAN_QUERY_REWRITE;
        }
    }


    /**
     * Expert: returns the rewriteMethod
     */
    public final SpanMultiTermQueryWrapper.SpanRewriteMethod getRewriteMethod() {
        return rewriteMethod;
    }

    /**
     * Expert: sets the rewrite method. This only makes sense
     * to be a span rewrite method.
     */
    public final void setRewriteMethod(SpanMultiTermQueryWrapper.SpanRewriteMethod rewriteMethod) {
        this.rewriteMethod = rewriteMethod;
    }

    @Override
    public String getField() {
        return query.getField();
    }

    @Override
    public SpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        throw new IllegalArgumentException("Rewrite first!");
    }

    /**
     * Returns the wrapped query
     */
    public Query getWrappedQuery() {
        return query;
    }


    @Override
    public String toString(String field) {
        StringBuilder builder = new StringBuilder();
        builder.append("NewSpanMultiTermQueryWrapper(");
        // NOTE: query.toString must be placed in a temp local to avoid compile errors on Java 8u20
        // see https://bugs.openjdk.java.net/browse/JDK-8056984?page=com.atlassian.streams.streams-jira-plugin:activity-stream-issue-tab
        String queryStr = query.toString(field);
        builder.append(queryStr);
        builder.append(")");
        return builder.toString();
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        return rewriteMethod.rewrite(reader, query);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(query.getField())) {
            query.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
        }
    }

    @Override
    public int hashCode() {
        return classHash() * 31 + query.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return query != null  && sameClassAs(other) && other != null &&
                query.equals(((BoostedSpanMultiTermQueryWrapper<?>) other).query);
    }


    /**
     * A rewrite method that first translates each term into a SpanTermQuery in a
     * {@link BooleanClause.Occur#SHOULD} clause in a BooleanQuery, and keeps the
     * scores as computed by the query.
     *
     * <p>
     * This rewrite method only uses the top scoring terms so it will not overflow
     * the boolean max clause count.
     *
     * @see #setRewriteMethod
     */
    public static final class BoostedTopTermsSpanBooleanQueryRewrite extends SpanMultiTermQueryWrapper.SpanRewriteMethod {
        private final TopTermsRewrite<List<SpanQuery>> delegate;
        private int docCount;

        /**
         * Create a TopTermsSpanBooleanQueryRewrite for
         * at most <code>size</code> terms.
         */
        public BoostedTopTermsSpanBooleanQueryRewrite(int size, long docCount) {
            delegate = new BoostedTopTermsRewrite<>(size, docCount) {

                @Override
                protected int getMaxSize() {
                    return Integer.MAX_VALUE;
                }

                @Override
                protected List<SpanQuery> getTopLevelBuilder() {
                    return new ArrayList<>();
                }

                @Override
                protected Query build(List<SpanQuery> queries) {
                    return new BoostedSpanOrQuery(queries.toArray(new SpanQuery[0]));
                }

                @Override
                protected void addClause(List<SpanQuery> topLevel, Term term, int docFreq, float boost, TermStates states) {
                    final BoostSpanTermQuery boostQuery = new BoostSpanTermQuery(term, states, boost);
                    topLevel.add(boostQuery);
                }

                private float leveralPopularity(int docFreq) {
                    return 1f - (0.3f * (float) docCount / (docFreq + docCount));
                }

            };
        }

        /**
         * return the maximum priority queue size
         */
        public int getSize() {
            return delegate.getSize();
        }

        @Override
        public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
            return (SpanQuery) delegate.rewrite(reader, query);
        }

        @Override
        public int hashCode() {
            return 31 * delegate.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final BoostedTopTermsSpanBooleanQueryRewrite other = (BoostedTopTermsSpanBooleanQueryRewrite) obj;
            return delegate.equals(other.delegate);
        }
    }
}

