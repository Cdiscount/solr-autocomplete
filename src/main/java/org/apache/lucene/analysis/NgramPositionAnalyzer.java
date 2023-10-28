package org.apache.lucene.analysis;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;

/**
 *
 */
public class NgramPositionAnalyzer extends Analyzer {

    public static final int NGRAM_MAX_LIMIT = 15;

    public NgramPositionAnalyzer() {
        // Nothing to do
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new LetterOrDigitTokenizer();
        TokenStream filter = new LowerCaseFilter(source);
        filter = new ASCIIFoldingFilter(filter);
        filter = new PositionInsertFilter(filter);
        filter = new DeduplicateFilter(filter);
        filter = new CDelimitedPayloadTokenFilter(filter, PositionInsertFilter.DEFAULT_DELIMITER, new FloatEncoder());
        filter = new NGramTokenFilter(filter, 1, NGRAM_MAX_LIMIT, false);
        return new TokenStreamComponents(source, filter);
    }
}
