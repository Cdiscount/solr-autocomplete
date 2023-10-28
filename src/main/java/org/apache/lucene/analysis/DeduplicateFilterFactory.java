package org.apache.lucene.analysis;

import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

public class DeduplicateFilterFactory extends TokenFilterFactory {

    public DeduplicateFilterFactory(Map<String, String> args) {
        super(args);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    public DeduplicateFilter create(TokenStream in) {
        return new DeduplicateFilter(in);
    }
}
