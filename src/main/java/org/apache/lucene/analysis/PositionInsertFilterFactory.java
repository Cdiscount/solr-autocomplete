package org.apache.lucene.analysis;

import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

public class PositionInsertFilterFactory extends TokenFilterFactory {

    public PositionInsertFilterFactory(Map<String, String> args) {
        super(args);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    public PositionInsertFilter create(TokenStream in) {
        return new PositionInsertFilter(in);
    }
}
