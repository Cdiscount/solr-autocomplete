package org.apache.lucene.analysis;

import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;

import java.util.Map;

public class LetterOrDigitTokenizerFactory extends TokenizerFactory {

    /**
     * Creates a new LetterOrDigitTokenizerFactory
     */
    public LetterOrDigitTokenizerFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public Tokenizer create(AttributeFactory factory) {
        return new LetterOrDigitTokenizer();
    }
}