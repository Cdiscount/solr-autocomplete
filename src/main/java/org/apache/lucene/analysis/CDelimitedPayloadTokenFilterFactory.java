package org.apache.lucene.analysis;

import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;
import org.apache.lucene.analysis.payloads.IdentityEncoder;
import org.apache.lucene.analysis.payloads.IntegerEncoder;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

public class CDelimitedPayloadTokenFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {
    public static final String ENCODER_ATTR = "encoder";
    public static final String DELIMITER_ATTR = "delimiter";


    private PayloadEncoder encoder;
    private String encoderClass;
    private char delimiter = '�';
    private Map<String, String> args;

    public CDelimitedPayloadTokenFilterFactory(Map<String, String> args) {
        super(args);
        this.args = args;
        this.encoderClass = this.args.get(ENCODER_ATTR);
        this.delimiter = this.getChar(args, DELIMITER_ATTR, delimiter);
    }

    public DelimitedPayloadTokenFilter create(TokenStream input) {
        return new DelimitedPayloadTokenFilter(input, this.delimiter, this.encoder);
    }


    public void inform(ResourceLoader loader) {
        if (encoderClass == null) {
            throw new IllegalArgumentException("Parameter encoder is mandatory");
        } else {
            if (encoderClass.equals("float")) {
                this.encoder = new FloatEncoder();
            } else if (encoderClass.equals("integer")) {
                this.encoder = new IntegerEncoder();
            } else if (encoderClass.equals("identity")) {
                this.encoder = new IdentityEncoder();
            } else {
                this.encoder = (PayloadEncoder) loader.newInstance(encoderClass, PayloadEncoder.class);
            }

            String delim = this.args.get(DELIMITER_ATTR);
            if (delim != null) {
                if (delim.length() != 1) {
                    throw new IllegalArgumentException("Delimiter must be one character only");
                }

                this.delimiter = delim.charAt(0);
            }

        }
    }

    public PayloadEncoder getEncoder() {
        return encoder;
    }

}
