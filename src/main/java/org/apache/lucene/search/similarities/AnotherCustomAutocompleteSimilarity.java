package org.apache.lucene.search.similarities;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.util.SmallFloat;

import java.util.ArrayList;
import java.util.List;

public class AnotherCustomAutocompleteSimilarity extends Similarity {

    static final float[] NORM_TABLE = new float[256];

    static {
        for (int i = 1; i < 256; ++i) {
            int length = SmallFloat.byte4ToInt((byte) i);
            NORM_TABLE[i] = 1;
        }
        NORM_TABLE[0] = 1f / NORM_TABLE[255];
    }

    protected boolean discountOverlaps = true;

    public AnotherCustomAutocompleteSimilarity() {
        // Nothing to do
    }



    public boolean getDiscountOverlaps() {
        return discountOverlaps;
    }

    public float tf(float freq) {
        return 1f;
    }

    public float idf(long docFreq, long numDocs) {
        return 1f;
    }

    public float lengthNorm(int numTerms) {
        return 1f;
    }

    @Override
    public final long computeNorm(FieldInvertState state) {
        final int numTerms;
        if (state.getIndexOptions() == IndexOptions.DOCS && state.getIndexCreatedVersionMajor() >= 8) {
            numTerms = state.getUniqueTermCount();
        } else if (discountOverlaps) {
            numTerms = state.getLength() - state.getNumOverlap();
        } else {
            numTerms = state.getLength();
        }
        return SmallFloat.intToByte4(numTerms);
    }


    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
        float idf = 1f;
        return Explanation.match(idf, "idf, computed as 1 from:", Explanation.match(1f, "docFreq = 1, "), Explanation.match(1f, "docCount = 1 "));
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
        double idf = 0d;
        List<Explanation> subs = new ArrayList<>();
        for (final TermStatistics stat : termStats) {
            Explanation idfExplain = idfExplain(collectionStats, stat);
            subs.add(idfExplain);
            idf += idfExplain.getValue().floatValue();
        }
        return Explanation.match((float) idf, "idf(), sum of:", subs);
    }

    @Override
    public final SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        final Explanation idf = termStats.length == 1
                ? idfExplain(collectionStats, termStats[0])
                : idfExplain(collectionStats, termStats);
        return new AnotherTFIDFScorer(boost, idf, NORM_TABLE);
    }


    /**
     * Collection statistics for the TF-IDF model. The only statistic of interest
     * to this model is idf.
     */
    class AnotherTFIDFScorer extends SimScorer {
        /**
         * The idf and its explanation
         */
        private final Explanation idf;
        private final float boost;
        private final float queryWeight;
        final float[] normTable;

        public AnotherTFIDFScorer(float boost, Explanation idf, float[] normTable) {
            // TODO: Validate?
            this.idf = idf;
            this.boost = boost;
            this.queryWeight = boost * idf.getValue().floatValue();
            this.normTable = normTable;
        }

        @Override
        public float score(float freq, long norm) {
            float normValue = normTable[(int) (norm & 0xFF)];
            return queryWeight * normValue;  // normalize for field
        }

        @Override
        public Explanation explain(Explanation freq, long norm) {
            return explainScore(freq, norm, normTable);
        }

        private Explanation explainScore(Explanation freq, long encodedNorm, float[] normTable) {
            List<Explanation> subs = new ArrayList<Explanation>();
            if (boost != 1F) {
                subs.add(Explanation.match(boost, "boost"));
            }
            subs.add(idf);
            Explanation tf = Explanation.match(tf(freq.getValue().floatValue()), "tf(freq=" + freq.getValue() + "), with freq of:", freq);
            subs.add(tf);

            float norm = normTable[(int) (encodedNorm & 0xFF)];

            Explanation fieldNorm = Explanation.match(norm, "fieldNorm");
            subs.add(fieldNorm);

            return Explanation.match(
                    queryWeight * tf.getValue().floatValue() * norm,
                    "score(freq=" + freq.getValue() + "), product of:",
                    subs);
        }
    }
}
