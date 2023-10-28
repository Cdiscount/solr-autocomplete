package org.apache.solr.spelling.suggest;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.CharsRef;

public class LocalSuggesterOptions extends SuggesterOptions{

    boolean firstContextOnly;
    IndexSearcher searcher;

    public LocalSuggesterOptions(SuggesterOptions opts) {
        super(opts.token, opts.count, opts.contextFilterQuery, opts.allTermsRequired, opts.highlight);
    }

    public LocalSuggesterOptions(IndexSearcher searcher, CharsRef token, int count, String contextFilterQuery, boolean allTermsRequired, boolean highlight, boolean firstContextOnly) {
        super(token, count, contextFilterQuery, allTermsRequired, highlight);
        this.searcher = searcher;
        this.firstContextOnly = firstContextOnly;
    }

    public CharsRef getToken() {
        return super.token;
    }

    public int getCount() {
        return super.count;
    }

    public String getContextFilterQuery() {
        return super.contextFilterQuery;
    }

    public boolean isAllTermsRequired() {
        return super.allTermsRequired;
    }

    public boolean isHighlight() {
        return super.highlight;
    }

    public boolean isFirstContextOnly() {
        return firstContextOnly;
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }
}
