package org.apache.lucene.analysis;

import org.apache.lucene.analysis.util.CharTokenizer;

/**
 *
 */
public class LetterOrDigitTokenizer extends CharTokenizer {
    private static final char PIPE = '\u007c';


    public LetterOrDigitTokenizer() {
        super();
    }

    /**
     * @param c a character
     * @return is a letter or a digit
     */
    @Override
    protected boolean isTokenChar(int c) {
        return Character.isLetterOrDigit(c) || c == PIPE;
    }

}
