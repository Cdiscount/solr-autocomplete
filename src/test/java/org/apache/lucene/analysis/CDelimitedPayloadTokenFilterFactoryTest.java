package org.apache.lucene.analysis;

import org.apache.lucene.analysis.payloads.FloatEncoder;
import org.apache.lucene.analysis.payloads.IdentityEncoder;
import org.apache.lucene.analysis.payloads.IntegerEncoder;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CDelimitedPayloadTokenFilterFactoryTest {

    @Test
    public void testEncoder() {
        Map<String, String> args = new HashMap<>();
        args.put(CDelimitedPayloadTokenFilterFactory.DELIMITER_ATTR, "?");

        CDelimitedPayloadTokenFilterFactory factory = new CDelimitedPayloadTokenFilterFactory(args);

        boolean foundError = false;
        try {
            factory.inform(null);
        } catch (IllegalArgumentException e) {
            foundError = true;
        }
        assertTrue(foundError);

        // Float
        args.put(CDelimitedPayloadTokenFilterFactory.ENCODER_ATTR, "float");
        factory = new CDelimitedPayloadTokenFilterFactory(args);
        factory.inform(null);

        assertNotNull(factory.getEncoder());
        assertTrue(factory.getEncoder() instanceof FloatEncoder);

        // Integer
        args.put(CDelimitedPayloadTokenFilterFactory.ENCODER_ATTR, "integer");
        factory = new CDelimitedPayloadTokenFilterFactory(args);
        factory.inform(null);

        assertNotNull(factory.getEncoder());
        assertTrue(factory.getEncoder() instanceof IntegerEncoder);

        // Identity
        args.put(CDelimitedPayloadTokenFilterFactory.ENCODER_ATTR, "identity");
        factory = new CDelimitedPayloadTokenFilterFactory(args);
        factory.inform(null);

        assertNotNull(factory.getEncoder());
        assertTrue(factory.getEncoder() instanceof IdentityEncoder);
    }


    @Test
    public void testDelimiter() {
        Map<String, String> args = new HashMap<>();
        args.put(CDelimitedPayloadTokenFilterFactory.DELIMITER_ATTR, null);
        args.put(CDelimitedPayloadTokenFilterFactory.ENCODER_ATTR, "integer");

        CDelimitedPayloadTokenFilterFactory factory = new CDelimitedPayloadTokenFilterFactory(args);

        boolean foundNoError = true;
        try {
            factory.inform(null);
        } catch (IllegalArgumentException e) {
            foundNoError = false;
        }
        assertTrue(foundNoError);


        // bad delimiter
        args.put(CDelimitedPayloadTokenFilterFactory.DELIMITER_ATTR, "xx");
        args.put(CDelimitedPayloadTokenFilterFactory.ENCODER_ATTR, "integer");


        boolean foundError = false;
        try {
            factory = new CDelimitedPayloadTokenFilterFactory(args);
           // factory.inform(null);
        } catch (IllegalArgumentException e) {
            foundError = true;
        }
        assertTrue(foundError);



    }
}