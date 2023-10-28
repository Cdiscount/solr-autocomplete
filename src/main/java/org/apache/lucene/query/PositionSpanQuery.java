/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.query;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.queries.payloads.PayloadFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafSimScorer;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.spans.BoostedFilterSpans;
import org.apache.lucene.search.spans.BoostedSpanOrQuery;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanScorer;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.span.BoostedSpanMultiTermQueryWrapper;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A Query class that uses a {@link PayloadFunction} to modify the score of a wrapped SpanQuery
 */
public class PositionSpanQuery extends SpanQuery {

    private static final float[] DEFAULT_COEFF = {1f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f};
    private static final float MIN_COEFF = 0.1f;

    private final SpanQuery wrappedQuery;
    private final boolean includeSpanScore;
    public int bestPosition;
    public boolean trueSpans;

    public float[][] coefficients;
    public float[] defCoefficients;
    public float defCoefficient;
    public float boost;


    public PositionSpanQuery(SpanQuery wrappedQuery,
                             boolean includeSpanScore,
                             int bestPosition, boolean trueSpans,
                             float[][] coefficients, float[] defaultCoefficients, Float defCoefficient, float boost) {
        this.wrappedQuery = Objects.requireNonNull(wrappedQuery);
        this.includeSpanScore = includeSpanScore;
        this.trueSpans = trueSpans;

        this.bestPosition = bestPosition;
        this.coefficients = coefficients;
        this.defCoefficients = defaultCoefficients != null ? defaultCoefficients : DEFAULT_COEFF;
        this.defCoefficient = defCoefficient != null ? defCoefficient : MIN_COEFF;
        this.boost = boost;

    }

    public PositionSpanQuery(SpanQuery wrappedQuery,
                             int bestPosition, boolean trueSpans, float[][] coefficients, float[] defaultCoefficients, Float defCoefficient, float boost) {
        this(wrappedQuery, true, bestPosition, trueSpans, coefficients, defaultCoefficients, defCoefficient, boost);
    }

    @Override
    public String getField() {
        return wrappedQuery.getField();
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query matchRewritten = wrappedQuery.rewrite(reader);
        if (wrappedQuery != matchRewritten
                && (matchRewritten instanceof SpanTermQuery
                || matchRewritten instanceof SpanMultiTermQueryWrapper
                || matchRewritten instanceof BoostedSpanMultiTermQueryWrapper
                || matchRewritten instanceof SpanOrQuery
                || matchRewritten instanceof BoostedSpanOrQuery)) {
            return new PositionSpanQuery((SpanQuery) matchRewritten, includeSpanScore,
                    bestPosition, trueSpans, coefficients, defCoefficients, defCoefficient, boost);
        }
        return super.rewrite(reader);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        wrappedQuery.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
    }


    @Override
    public String toString(String field) {
        return "PositionSpanQuery(" +
                wrappedQuery.toString(field) +
                ", includeSpanScore: " +
                includeSpanScore +
                ", boost: " +
                boost +
                ")";
    }

    @Override
    public SpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        SpanWeight innerWeight = wrappedQuery.createWeight(searcher, scoreMode, boost);
        if (!scoreMode.needsScores())
            return innerWeight;
        return new PositionSpanWeight(searcher, innerWeight, boost);
    }

    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(PositionSpanQuery other) {
        return wrappedQuery.equals(other.wrappedQuery) && (includeSpanScore == other.includeSpanScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrappedQuery, includeSpanScore);
    }

    private class PositionSpanWeight extends SpanWeight {

        private final SpanWeight innerWeight;

        public PositionSpanWeight(IndexSearcher searcher, SpanWeight innerWeight, float boost) throws IOException {
            super(PositionSpanQuery.this, searcher, getTermStates(innerWeight), boost);
            this.innerWeight = innerWeight;
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            innerWeight.extractTermStates(contexts);
        }

        @Override
        public Spans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
            return innerWeight.getSpans(ctx, requiredPostings);
        }

        @Override
        public SpanScorer scorer(LeafReaderContext context) throws IOException {
            Spans spans = getSpans(context, Postings.PAYLOADS);
            if (spans == null)
                return null;
            LeafSimScorer docScorer = innerWeight.getSimScorer(context);
            PositionSpans positionSpans = new PositionSpans(spans, docScorer);
            return new PayloadPositionSpanScorer(this, positionSpans, docScorer);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return innerWeight.isCacheable(ctx);
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            innerWeight.extractTerms(terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            PayloadPositionSpanScorer scorer = (PayloadPositionSpanScorer) scorer(context);
            if (scorer == null || scorer.iterator().advance(doc) != doc)
                return Explanation.noMatch("No match");

            scorer.score();  // force freq calculation
            Explanation payloadExpl = scorer.getPayloadExplanation();

            if (includeSpanScore) {
                SpanWeight innerWei = ((PositionSpanWeight) scorer.getWeight()).innerWeight;
                Explanation innerExpl = innerWei.explain(context, doc);
                return Explanation.match(scorer.scoreCurrentDoc(), ", product of:", innerExpl, payloadExpl);
            }

            return scorer.getPayloadExplanation();
        }
    }

    private class PositionSpans extends BoostedFilterSpans implements SpanCollector {
        public int payloadsSeen;
        public float payloadScore;
        LeafSimScorer docScorer;

        private PositionSpans(Spans in, LeafSimScorer docScorer) {
            super(in);
            this.docScorer = docScorer;
        }

        @Override
        protected AcceptStatus accept(Spans candidate) {
            return AcceptStatus.YES;
        }

        @Override
        protected void doStartCurrentDoc() {
            payloadScore = 0;
            payloadsSeen = 0;
        }

        @Override
        public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
            BytesRef payload = postings.getPayload();

            if (payload != null) {
                float distance = distanceScore(bestPosition, in.endPosition());
                payloadScore = payloadsSeen == 0 ? distance : Math.min(distance, payloadScore);

            } else {
                payloadScore = 0f;
            }
            payloadsSeen++;
        }


        /*
         * Retrieve value based on distance based on positions
         */
        public float distanceScore(int queryPosition, int termPosition) {
            return getCoefficient((termPosition - queryPosition), queryPosition);
        }

        /*
         * Retrieve coefficient
         */
        private float getCoefficient(int delta, int indice) {
            // if inversed words, 50% penalty
            boolean neg = delta < 0;
            try {
                if (coefficients != null && coefficients.length > indice - 1 && coefficients[indice - 1].length >= delta) {
                    return coefficients[indice - 1][delta];
                } else if (defCoefficients != null && defCoefficients.length >= delta) {
                    return defCoefficients[delta];
                }
            } catch (Exception e) {
                //  Nothing to do
            }
            return defCoefficient * (neg ? 0.5f : 1);
        }

        @Override
        public void reset() {
            // Nothing to do
        }

        @Override
        protected void doCurrentSpans() throws IOException {
            in.collect(this);
        }
    }

    private class PayloadPositionSpanScorer extends SpanScorer {

        private final PositionSpans positionSpans;

        private PayloadPositionSpanScorer(SpanWeight weight, PositionSpans positionSpans, LeafSimScorer docScorer) {
            super(weight, positionSpans, docScorer);
            this.positionSpans = positionSpans;

        }

        protected float getPayloadScore() {
            return positionSpans.payloadsSeen > 0 ? positionSpans.payloadScore : 1.0F;
        }

        protected Explanation getPayloadExplanation() {
            return Explanation.match(0, "truncated score, max of:", Explanation.match(0f, "minimum score"), Explanation.match(0, "truncated score, max of:", Explanation.match(0f, "minimum score")));
        }

        protected float getSpanScore() throws IOException {
            return super.scoreCurrentDoc();
        }

        @Override
        protected float scoreCurrentDoc() throws IOException {
            return (positionSpans == null ? 1f : positionSpans.boost(docID()))
                    * (includeSpanScore ? getSpanScore() : 1f)
                    * getPayloadScore()
                    * boost;
        }
    }

}
