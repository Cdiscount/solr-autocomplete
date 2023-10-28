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
package org.apache.solr.suggest.analyzing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.NormalAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.solr.spelling.suggest.LocalSuggesterOptions;
import org.apache.solr.spelling.suggest.fst.AutocompleteLookupFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.solr.spelling.suggest.fst.AutocompleteLookupFactory.FIELD_CONTEXT;
import static org.apache.solr.spelling.suggest.fst.AutocompleteLookupFactory.FIELD_TEXT;

/**
 * Analyzes the input text and then suggests matches based
 * on prefix matches to any tokens in the indexed text.
 * This also highlights the tokens that match.
 *
 * <p>This suggester supports payloads.  Matches are sorted only
 * by the suggest weight; it would be nice to support
 * blended score + weight sort in the future.  This means
 * this suggester best applies when there is a strong
 * a-priori ranking of all the suggestions.
 *
 * <p>This suggester supports contexts, including arbitrary binary
 * terms.
 */

public class AutocompleteSuggester extends Lookup implements Closeable {


    protected final Analyzer queryAnalyzer = new NormalAnalyzer();

    private final Directory dir;

    private final boolean allTermsRequired;
    private final boolean highlight;

    private final Map<String, String> fields;
    private final float coeff;
    private final Integer nbWordsForPositionMatch;
    private final boolean onlyFirstContext;

    public SearcherManager searcherMgr;
    protected final Object searcherMgrLock = new Object();

    private Similarity similarity;

    private long docCount;

    /**
     * Create a new instance, loading from a previously built
     * AnalyzingInfixSuggester directory, if it exists.  This directory must be
     * private to the infix suggester (i.e., not an external
     * Lucene index).  Note that {@link #close}
     * will also close the provided directory.
     *
     * @param minPrefixChars   Minimum number of leading characters
     *                         before PrefixQuery is used (default 4).
     *                         Prefixes shorter than this are indexed as character
     *                         ngrams (increasing index size but making lookups
     *                         faster).
     * @param allTermsRequired All terms in the suggest query must be matched.
     * @param highlight        Highlight suggest query in suggestions.
     */
    public AutocompleteSuggester(Directory dir, int minPrefixChars,
                                 boolean allTermsRequired, boolean highlight, Map<String, String> fields,
                                 float coeff, Integer nbWordsForPositionMatch, Similarity similarity, boolean onlyFirstContext) throws IOException {

        if (minPrefixChars < 0) {
            throw new IllegalArgumentException("minPrefixChars must be >= 0; got: " + minPrefixChars);
        }

        this.dir = dir;
        this.allTermsRequired = allTermsRequired;
        this.highlight = highlight;
        this.fields = fields;
        this.coeff = coeff;
        this.nbWordsForPositionMatch = nbWordsForPositionMatch;
        this.similarity = similarity;
        this.onlyFirstContext = onlyFirstContext;

        if (DirectoryReader.indexExists(dir)) {
            searcherMgr = new SearcherManager(dir, null);
        }
    }

    @Override
    public void build(InputIterator iter) throws IOException {
        // Nothing to do
    }

    private void ensureOpen() throws IOException {
        synchronized (searcherMgrLock) {
            if (DirectoryReader.indexExists(dir)) {
                SearcherManager oldSearcherMgr = searcherMgr;
                searcherMgr = new SearcherManager(dir, null);
                if (oldSearcherMgr != null) {
                    oldSearcherMgr.close();
                }
            }
        }
    }


    @Override
    public List<LookupResult> lookup(CharSequence key, Set<BytesRef> contexts, boolean onlyMorePopular, int num) throws IOException {
        return lookup2(key, num, allTermsRequired, highlight);
    }

    /**
     * Lookup, with context but without booleans. Context booleans default to SHOULD,
     * so each suggestion must have at least one of the contexts.
     */
    public List<LookupResult> lookup2(CharSequence key, int num, boolean allTermsRequired, boolean doHighlight) throws IOException {
        return lookup(null, key, num, allTermsRequired, doHighlight, this.onlyFirstContext);
    }

    public List<LookupResult> lookup(LocalSuggesterOptions options, boolean b) throws IOException {
        return lookup(options.getSearcher(), options.getToken(), options.getCount(), allTermsRequired, options.isHighlight(), options.isFirstContextOnly());
    }

    /**
     * This is an advanced method providing the capability to send down to the suggester any
     * arbitrary lucene query to be used to filter the result of the suggester
     */
    public List<LookupResult> lookup(IndexSearcher searcher, CharSequence key, int num, boolean allTermsRequired, boolean doHighlight, boolean firstContextOnly) throws IOException {

        SearcherManager mgr = null;
        if (searcher == null) {
            ensureOpen();

            synchronized (searcherMgrLock) {
                mgr = searcherMgr; // acquire & release on same SearcherManager, via local reference
                searcher = mgr.acquire();
            }
        }

        QueryInfos queryInfos = QueryInfos.parse(key, queryAnalyzer);

        final Occur occur;
        if (allTermsRequired) {
            occur = Occur.MUST;
        } else {
            occur = Occur.SHOULD;
        }

        if (similarity != null) {
            searcher.setSimilarity(similarity);
        }
        TopScoreDocCollector c = TopScoreDocCollector.create(num, 1);

        if (docCount == 0) {
            docCount = searcher.collectionStatistics("id").docCount() / 1000;
        }

        try {
            AutocompleteQueryBuilder builder = new AutocompleteQueryBuilder(nbWordsForPositionMatch, fields, coeff, docCount);
            Query q = builder.getQuery(queryInfos, occur);
            searcher.search(q, c);
            TopDocs hits = c.topDocs();

            return createResults(searcher, hits, doHighlight, queryInfos.getMatchedTokens(), queryInfos.getPrefixToken(), firstContextOnly);
        } finally {
            if (mgr != null) {
                mgr.release(searcher);
            }
        }
    }

    /**
     * Create the results based on the search hits.
     * Can be overridden by subclass to add particular behavior (e.g. weight transformation).
     * Note that there is no prefix token (the {@code prefixToken} argument will
     * be null) whenever the final token in the incoming request was in fact finished
     * (had trailing characters, such as white-space).
     *
     * @throws IOException If there are problems reading fields from the underlying Lucene index.
     */

    protected List<LookupResult> createResults(IndexSearcher searcher, TopDocs hits,
                                               boolean doHighlight, Set<String> matchedTokens, String prefixToken,
                                               boolean firstContextOnly)
            throws IOException {

        List<LookupResult> results = new ArrayList<>();
        ScoreDoc fd;
        BytesRef text;
        BytesRef contexts = null;

        List<LeafReaderContext> leaves = searcher.getIndexReader().leaves();
        LeafReaderContext readerContext = null;
        int segment = -1;

        for (int i = 0; i < hits.scoreDocs.length; i++) {
            fd = hits.scoreDocs[i];

            int seq = ReaderUtil.subIndex(fd.doc, leaves);
            if (seq != segment) {
                segment = seq;
                readerContext = leaves.get(segment);
            }

            text = getText(readerContext, fd.doc, fields.get(FIELD_TEXT));

            if (!firstContextOnly || i == 0) {
                contexts = getText(readerContext, fd.doc, fields.get(FIELD_CONTEXT));
            }

            float sc = hits.scoreDocs[i].score;
            long score = (long) (sc * 1000000);

            LookupResult result;
            String t = text.utf8ToString();
            if (doHighlight) {
                result = new LookupResult(t, highlight(t, matchedTokens, prefixToken), score, null, contexts == null ? null : Set.of(contexts));
            } else {
                result = new LookupResult(t, score, null, contexts == null ? null : Set.of(contexts));
            }
            results.add(result);
        }

        return results;
    }

    BytesRef getText(LeafReaderContext readerContext, int docId, String fieldName) throws IOException {
        if (fields.get(FIELD_TEXT) != null) {
            BinaryDocValues textDV = readerContext.reader().getSortedDocValues(fieldName);
            if (textDV != null) {
                textDV.advance(docId - readerContext.docBase);
                return textDV.binaryValue();
            }
        }
        return null;
    }


    /**
     * Override this method to customize the Object
     * representing a single highlighted suggestions; the
     * result is set on each {@link
     * LookupResult#highlightKey} member.
     */
    protected Object highlight(String text, Set<String> matchedTokens, String prefixToken) throws IOException {
        try (TokenStream ts = queryAnalyzer.tokenStream("text", new StringReader(text))) {
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);
            ts.reset();
            StringBuilder sb = new StringBuilder();
            int upto = 0;
            while (ts.incrementToken()) {
                String token = termAtt.toString();
                int startOffset = offsetAtt.startOffset();
                int endOffset = offsetAtt.endOffset();
                if (upto < startOffset) {
                    addNonMatch(sb, text.substring(upto, startOffset));
                    upto = startOffset;
                } else if (upto > startOffset) {
                    continue;
                }

                if (matchedTokens.contains(token)) {
                    // Token matches.
                    addWholeMatch(sb, text.substring(startOffset, endOffset), token);
                    upto = endOffset;
                } else if (prefixToken != null && token.startsWith(prefixToken)) {
                    addPrefixMatch(sb, text.substring(startOffset, endOffset), token, prefixToken);
                    upto = endOffset;
                }
            }
            ts.end();
            int endOffset = offsetAtt.endOffset();
            if (upto < endOffset) {
                addNonMatch(sb, text.substring(upto));
            }
            return sb.toString();
        }
    }

    /**
     * Called while highlighting a single result, to append a
     * non-matching chunk of text from the suggestion to the
     * provided fragments list.
     *
     * @param sb   The {@code StringBuilder} to append to
     * @param text The text chunk to add
     */
    protected void addNonMatch(StringBuilder sb, String text) {
        sb.append(text);
    }

    /**
     * Called while highlighting a single result, to append
     * the whole matched token to the provided fragments list.
     *
     * @param sb       The {@code StringBuilder} to append to
     * @param surface  The surface form (original) text
     * @param analyzed The analyzed token corresponding to the surface form text
     */
    protected void addWholeMatch(StringBuilder sb, String surface, String analyzed) {
        sb.append("[");
        sb.append(surface);
        sb.append("]");
    }

    /**
     * Called while highlighting a single result, to append a
     * matched prefix token, to the provided fragments list.
     *
     * @param sb          The {@code StringBuilder} to append to
     * @param surface     The fragment of the surface form
     *                    (indexed during {@link #build}, corresponding to
     *                    this match
     * @param analyzed    The analyzed token that matched
     * @param prefixToken The prefix of the token that matched
     */
    protected void addPrefixMatch(StringBuilder sb, String surface, String analyzed, String prefixToken) {
        // TODO: apps can try to invert their analysis logic
        // here, e.g. downcase the two before checking prefix:
        if (prefixToken.length() >= surface.length()) {
            addWholeMatch(sb, surface, analyzed);
            return;
        }
        sb.append("[");
        sb.append(surface.substring(0, prefixToken.length()));
        sb.append("]");
        sb.append(surface.substring(prefixToken.length()));
    }

    @Override
    public boolean store(DataOutput in) throws IOException {
        return false;
    }

    @Override
    public boolean load(DataInput out) throws IOException {
        return false;
    }

    @Override
    public void close() throws IOException {
        if (searcherMgr != null) {
            searcherMgr.close();
            searcherMgr = null;
        }
        if (dir != null) {
            dir.close();
        }
    }

    @Override
    public long ramBytesUsed() {
        long mem = RamUsageEstimator.shallowSizeOf(this);
        try {
            if (searcherMgr != null) {
                SearcherManager mgr;
                IndexSearcher searcher;
                synchronized (searcherMgrLock) {
                    mgr = searcherMgr; // acquire & release on same SearcherManager, via local reference
                    searcher = mgr.acquire();
                }
                try {
                    for (LeafReaderContext context : searcher.getIndexReader().leaves()) {
                        LeafReader reader = FilterLeafReader.unwrap(context.reader());
                        if (reader instanceof SegmentReader) {
                            mem += ((SegmentReader) context.reader()).ramBytesUsed();
                        }
                    }
                } finally {
                    mgr.release(searcher);
                }
            }
            return mem;
        } catch (IOException ioe) {
            throw new AutocompleteLookupFactory.AutocompleteRuntimeException(ioe);
        }
    }

    @Override
    public Collection<Accountable> getChildResources() {
        List<Accountable> resources = new ArrayList<>();
        try {
            if (searcherMgr != null) {
                SearcherManager mgr;
                IndexSearcher searcher;
                synchronized (searcherMgrLock) {
                    mgr = searcherMgr; // acquire & release on same SearcherManager, via local reference
                    searcher = mgr.acquire();
                }
                try {
                    for (LeafReaderContext context : searcher.getIndexReader().leaves()) {
                        LeafReader reader = FilterLeafReader.unwrap(context.reader());
                        if (reader instanceof SegmentReader) {
                            resources.add(Accountables.namedAccountable("segment", (SegmentReader) reader));
                        }
                    }
                } finally {
                    mgr.release(searcher);
                }
            }
            return Collections.unmodifiableList(resources);
        } catch (IOException ioe) {
            throw new AutocompleteLookupFactory.AutocompleteRuntimeException(ioe);
        }
    }

    @Override
    public long getCount() throws IOException {
        if (searcherMgr == null) {
            return 0;
        }
        SearcherManager mgr;
        IndexSearcher searcher;
        synchronized (searcherMgrLock) {
            mgr = searcherMgr; // acquire & release on same SearcherManager, via local reference
            searcher = mgr.acquire();
        }
        try {
            return searcher.getIndexReader().numDocs();
        } finally {
            mgr.release(searcher);
        }
    }


}
