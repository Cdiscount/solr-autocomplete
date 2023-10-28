package org.apache.solr.handler;

import org.apache.lucene.search.DisiWrapper;
import org.apache.lucene.search.spans.Spans;

public class DisiBoostWrapper extends DisiWrapper {
    public final Float boost;

    public DisiBoostWrapper(Spans spans, Float boost) {
        super(spans);
        this.boost = boost;
    }
}
