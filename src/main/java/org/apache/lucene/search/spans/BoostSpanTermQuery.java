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
package org.apache.lucene.search.spans;


import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Counterpart of {@link BoostQuery} for spans.
 */
public class BoostSpanTermQuery extends SpanTermQuery {

    private final float boost;

    public BoostSpanTermQuery(Term term, TermStates termStates, float boost) {
        super(term, termStates);
        this.boost = boost;
    }

    /**
     * Return the wrapped {@link SpanQuery}.
     */
    public SpanQuery getQuery() {
        return this;
    }


    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(BoostSpanTermQuery other) {
        return this.equals(other) &&
                Float.floatToIntBits(boost) == Float.floatToIntBits(other.boost);
    }

    @Override
    public int hashCode() {
        int h = classHash();
        h = 31 * h + Float.floatToIntBits(boost);
        return h;
    }

    @Override
    public SpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        IndexReaderContext topContext = searcher.getTopReaderContext();
        TermStates context;
        if (this.termStates != null && this.termStates.wasBuiltFor(topContext)) {
            context = this.termStates;
        } else {
            context = TermStates.build(topContext, this.term, scoreMode.needsScores());
        }

        return new BoostSpanTermWeight(context, searcher, scoreMode.needsScores() ? Collections.singletonMap(this.term, context) : null, boost);
    }

    /**
     *
     */
    public class BoostSpanTermWeight extends SpanTermWeight {

        public BoostSpanTermWeight(TermStates termStates, IndexSearcher searcher, Map<Term, TermStates> terms, float boost) throws IOException {
            super(termStates, searcher, terms, boost);
        }

        @Override
        public Spans getSpans(LeafReaderContext context, Postings requiredPostings) throws IOException {
            assert this.termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(context)) : "The top-reader used to create Weight is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);

            TermState state = this.termStates.get(context);
            if (state == null) {
                assert context.reader().docFreq(term) == 0 : "no termstate found but term exists in reader term=" + term;
                return null;
            } else {
                Terms terms = context.reader().terms(term.field());
                if (terms == null) {
                    return null;
                } else if (!terms.hasPositions()) {
                    throw new IllegalStateException("field \"" + term.field() + "\" was indexed without position data; cannot run SpanTermQuery (term=" + term.text() + ")");
                } else {
                    TermsEnum termsEnum = terms.iterator();
                    termsEnum.seekExact(term.bytes(), state);
                    PostingsEnum postings = termsEnum.postings((PostingsEnum) null, requiredPostings.getRequiredPostings());
                    float positionsCost = SpanTermQuery.termPositionsCost(termsEnum) * 4.0F;
                    return new BoostTermSpans(this.getSimScorer(context), postings, term, positionsCost, boost);
                }
            }
        }
    }

}
