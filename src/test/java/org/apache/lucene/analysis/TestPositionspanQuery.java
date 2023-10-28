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
package org.apache.lucene.analysis;

import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.query.PositionSpanQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CheckHits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryUtils;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.English;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;


/**
 *
 **/
public class TestPositionspanQuery extends LuceneTestCase {
    private static IndexSearcher searcher;
    private static IndexReader reader;
    private static Similarity similarity = new BoostingSimilarity();
    private static final byte[] payloadField = new byte[]{1};
    private static final byte[] payloadMultiField1 = new byte[]{2};
    private static final byte[] payloadMultiField2 = new byte[]{4};
    protected static Directory directory;

    public static final String NO_PAYLOAD_FIELD = "noPayloadField";
    public static final String MULTI_FIELD = "multiField";
    public static final String FIELD = "field";

    public static final float[] FIRST_COEFF = {3f, 2f, 1f};
    public static final float[] SECOND_COEFF = {2f, 1.5f, 1f, 0.5f};
    public static final float[] THIRD_COEFF = {1f, 0.5f, 0.4f, 0.3f};
    public static final float[] DEFAULT_COEFF = {0.5f, 0.4f, 0.3f};
    public static final float MIN_FIRST_COEFF = 0.2f;
    public static final float[][] ALL_COEFF = {FIRST_COEFF, SECOND_COEFF, THIRD_COEFF};


    private static class PayloadAnalyzer extends Analyzer {

        private PayloadAnalyzer() {
            super(PER_FIELD_REUSE_STRATEGY);
        }

        @Override
        public TokenStreamComponents createComponents(String fieldName) {
            Tokenizer result = new MockTokenizer(MockTokenizer.SIMPLE, true);
            return new TokenStreamComponents(result, new PayloadFilter(result, fieldName));
        }
    }

    private static class PayloadFilter extends TokenFilter {
        private final String fieldName;
        private int numSeen = 0;

        private final PayloadAttribute payloadAtt;

        public PayloadFilter(TokenStream input, String fieldName) {
            super(input);
            this.fieldName = fieldName;
            payloadAtt = addAttribute(PayloadAttribute.class);
        }

        @Override
        public boolean incrementToken() throws IOException {
            boolean hasNext = input.incrementToken();
            if (hasNext) {
                if (fieldName.equals("field")) {
                    payloadAtt.setPayload(new BytesRef(payloadField));
                } else if (fieldName.equals("multiField")) {
                    if (numSeen % 2 == 0) {
                        payloadAtt.setPayload(new BytesRef(payloadMultiField1));
                    } else {
                        payloadAtt.setPayload(new BytesRef(payloadMultiField2));
                    }
                    numSeen++;
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void reset() throws IOException {
            super.reset();
            this.numSeen = 0;
        }
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        directory = newDirectory();
        RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
                newIndexWriterConfig(new PayloadAnalyzer())
                        .setSimilarity(similarity).setMergePolicy(newLogMergePolicy()));
        //writer.infoStream = System.out;
        for (int i = 0; i < 1000; i++) {
            Document doc = new Document();
            Field noPayloadField = newTextField(NO_PAYLOAD_FIELD, English.intToEnglish(i), Field.Store.YES);
            //noPayloadField.setBoost(0);
            doc.add(noPayloadField);
            doc.add(newTextField("field", English.intToEnglish(i), Field.Store.YES));
            doc.add(newTextField("multiField", English.intToEnglish(i) + "  " + English.intToEnglish(i), Field.Store.YES));
            writer.addDocument(doc);
        }
        writer.forceMerge(1);
        reader = writer.getReader();
        writer.close();

        searcher = newSearcher(getOnlyLeafReader(reader));
        searcher.setSimilarity(similarity);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        searcher = null;
        reader.close();
        reader = null;
        directory.close();
        directory = null;
    }

    public void test() throws IOException {
        SpanQuery query = new PositionSpanQuery(new SpanTermQuery(new Term("field", "seventy")),
                0, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, 1f);

        TopDocs hits = searcher.search(query, 100);
        assertNotNull("hits is null and it shouldn't be", hits);
        assertEquals("hits Size: " + hits.totalHits.value + " is not: " + 100, 100, hits.totalHits.value);

        //they should all have the exact same score, because they all contain seventy once, and we set
        //all the other similarity factors to be 1

        for (int i = 0; i < hits.scoreDocs.length; i++) {
            ScoreDoc doc = hits.scoreDocs[i];
            assertEquals(0.2f, doc.score, 0f);
        }

    }

    public void testQuery() {
        SpanQuery boostingFuncTermQuery = new PositionSpanQuery(new SpanTermQuery(new Term(MULTI_FIELD, "seventy")),
                0, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, 1f);
        QueryUtils.check(boostingFuncTermQuery);

        SpanTermQuery spanTermQuery = new SpanTermQuery(new Term(MULTI_FIELD, "seventy"));

        assertEquals(boostingFuncTermQuery.equals(spanTermQuery), spanTermQuery.equals(boostingFuncTermQuery));

    }

    public void testMultipleMatchesPerDoc() throws Exception {
        SpanQuery query = new PositionSpanQuery(new SpanTermQuery(new Term(MULTI_FIELD, "seventy")),
                0, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, 1f);
        TopDocs hits = searcher.search(query, 100);
        assertNotNull("hits is null and it shouldn't be", hits);
        assertEquals("hits Size: " + hits.totalHits.value + " is not: " + 100, 100, hits.totalHits.value);

        //they should all have the exact same score, because they all contain seventy once, and we set
        //all the other similarity factors to be 1

        //System.out.println("Hash: " + seventyHash + " Twice Hash: " + 2*seventyHash);
        //there should be exactly 10 items that score a 4, all the rest should score a 2
        //The 10 items are: 70 + i*100 where i in [0-9]
        for (int i = 0; i < hits.scoreDocs.length; i++) {
            ScoreDoc doc = hits.scoreDocs[i];
            assertEquals(doc.score + " does not equal: " + 0.2, 0.2, doc.score, 0.0001);
        }
      //  CheckHits.checkExplanations(query, "field", searcher, true);
        Spans spans = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f).getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
        assertNotNull("spans is null and it shouldn't be", spans);
        //should be two matches per document
        int count = 0;
        //100 hits times 2 matches per hit, we should have 200 in count
        while (spans.nextDoc() != Spans.NO_MORE_DOCS) {
            while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                count++;
            }
        }
        assertEquals(count + " does not equal: " + 200, 200, count);
    }

    public void testNoMatch() throws Exception {
        SpanQuery query = new PositionSpanQuery(new SpanTermQuery(new Term(FIELD, "junk")),
                0, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, 1f);
        TopDocs hits = searcher.search(query, 100);
        assertNotNull("hits is null and it shouldn't be", hits);
        assertEquals("hits Size: " + hits.totalHits.value + " is not: " + 0, 0, hits.totalHits.value);

    }

    public void testNoPayload() throws Exception {
        SpanQuery q1 = new PositionSpanQuery(new SpanTermQuery(new Term(NO_PAYLOAD_FIELD, "zero")),
                0, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, 1f);
        SpanQuery q2 = new PositionSpanQuery(new SpanTermQuery(new Term(NO_PAYLOAD_FIELD, "foo")),
                0, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, 1f);
        BooleanClause c1 = new BooleanClause(q1, BooleanClause.Occur.MUST);
        BooleanClause c2 = new BooleanClause(q2, BooleanClause.Occur.MUST_NOT);
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        query.add(c1);
        query.add(c2);
        TopDocs hits = searcher.search(query.build(), 100);
        assertNotNull("hits is null and it shouldn't be", hits);
        assertEquals("hits Size: " + hits.totalHits.value + " is not: " + 1, 1, hits.totalHits.value);
        int[] results = new int[1];
        results[0] = 0;//hits.scoreDocs[0].doc;
        CheckHits.checkHitCollector(random(), query.build(), NO_PAYLOAD_FIELD, searcher, results);
    }

    static class BoostingSimilarity extends ClassicSimilarity {

        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //Make everything else 1 so we see the effect of the payload
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        @Override
        public float lengthNorm(int length) {
            return 1;
        }

        @Override
        public float idf(long docFreq, long docCount) {
            return 1;
        }

        @Override
        public float tf(float freq) {
            return freq == 0 ? 0 : 1;
        }
    }

}
