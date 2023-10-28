package org.apache.lucene.analysis;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 */
public class CustomAnalyzer extends Analyzer {

    public CustomAnalyzer() {
        super();
    }

    @Override
    protected TokenStreamComponents createComponents(String s) {
        Tokenizer source = new StandardTokenizer();
        TokenStream filter = new org.apache.lucene.analysis.LowerCaseFilter(source);
        filter = new ASCIIFoldingFilter(filter);
        return new TokenStreamComponents(source, filter);
    }
}
