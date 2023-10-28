package org.apache.lucene.analysis;

import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;

import java.io.IOException;

public class CDelimitedPayloadTokenFilter extends TokenFilter {
    public static final char DEFAULT_DELIMITER = '|';
    private final char delimiter;
    private final CharTermAttribute termAtt = (CharTermAttribute)this.addAttribute(CharTermAttribute.class);
    private final PayloadAttribute payAtt = (PayloadAttribute)this.addAttribute(PayloadAttribute.class);
    private final PayloadEncoder encoder;

    public CDelimitedPayloadTokenFilter(TokenStream input, char delimiter, PayloadEncoder encoder) {
        super(input);
        this.delimiter = delimiter;
        this.encoder = encoder;
    }

    public final boolean incrementToken() throws IOException {
        if (this.input.incrementToken()) {
            String text = this.termAtt.toString();
            char[] buffer = this.termAtt.toString().toCharArray();
            int length = this.termAtt.length();

            int locate = text.indexOf(this.delimiter);
            if(locate != -1) {
                this.payAtt.setPayload(this.encoder.encode(buffer, locate + 1, length - (locate + 1)));
                this.termAtt.setLength(locate);
                return true;
            }

            this.payAtt.setPayload(null);
            return true;
        } else {
            return false;
        }
    }
}
