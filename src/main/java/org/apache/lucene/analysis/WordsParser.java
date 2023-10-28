package org.apache.lucene.analysis;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.queries.payloads.PayloadDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class WordsParser {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected static final PayloadDecoder DECODER = (bytes) -> PayloadHelper.decodeFloat(bytes != null && bytes.bytes != null && bytes.bytes.length > 0 ? bytes.bytes : null, bytes != null ? bytes.offset : 0);

    public static final String CLEANER = "(.)\\1{3,}";

    private WordsParser() {
        // Nothing to do
    }

    public static Data parse(String key, Analyzer queryAnalyzer) {
        Data data = new Data();
        try (TokenStream ts = queryAnalyzer.tokenStream("", new StringReader(key))) {
            ts.reset();

            final CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            final OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);
            final PositionIncrementAttribute posAtt = ts.addAttribute(PositionIncrementAttribute.class);
            final PayloadAttribute payAtt = ts.addAttribute(PayloadAttribute.class);

            while (ts.incrementToken()) {
                data.words.add(termAtt.toString());

                data.positions.add(posAtt.getPositionIncrement());

                data.startOffset.add(offsetAtt.startOffset());
                data.endOffset.add(offsetAtt.endOffset());

                if (payAtt.getPayload() != null) {
                    data.payloads.add(DECODER.computePayloadFactor(payAtt.getPayload()));
                }
            }
            ts.end();
        } catch (IOException io) {
            // Nothing to do
        }

        return data;
    }

    public static LinkedList<String> concatWords(LinkedList<String> words, int nbConcat, boolean withPartial, boolean withReverse) {
        if (words == null || words.isEmpty() || nbConcat < 2 || nbConcat > 5) {
            return new LinkedList<>();
        }

        if (words.size() == 1) {
            return words;
        }
        LinkedList<String> result = new LinkedList<>();

        LinkedList<String> currentList = new LinkedList<>();
        int maxRun = words.size() + nbConcat - 1;
        int size = words.size();

        int sizeToKeep = withPartial ? 2 : nbConcat;

        Set<String> reusable = new LinkedHashSet<>();

        for (int i = 0; i < maxRun; i++) {
            for (int j = i; j < i + nbConcat && j < size; j++) {
                currentList.add(words.get(j));
                if (currentList.size() >= sizeToKeep) {
                    result.addAll(recomposeTextWithReverse(currentList, "", reusable, withReverse));
                }
            }

            currentList.clear();
        }

        return result;
    }

    public static Set<String> recomposeTextWithReverse(final LinkedList<String> values, String separator, Set<String> reuse, boolean withReverse) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashSet<>();
        }
        reuse.clear();

        reuse.add(recomposeText(values, separator, withReverse));

        return reuse;
    }

    public static String recomposeText(final LinkedList<String> values, String separator, boolean withReverse) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (String value : values) {
            sb.append(value).append(separator);
        }

        if (withReverse) {
            sb.append(" ");
            Iterator<String> ite = values.descendingIterator();
            while (ite.hasNext()) {
                sb.append(ite.next()).append(separator);
            }
        }

        int lengthToDelete = separator.length();
        if (lengthToDelete > 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb.toString();
    }


    public static String cleanRepetitionText(String text) {
        return text.replaceAll(CLEANER, "$1$1");
    }

    public static LinkedList<String> decomposeText(final Analyzer searcherAnalyzer, final String fieldname, final String text) {
        if (text != null) {
            try {
                final TokenStream stream = searcherAnalyzer.tokenStream(fieldname, new StringReader(text));
                final CharTermAttribute termAttribute = stream.addAttribute(CharTermAttribute.class);
                stream.reset();

                final LinkedList<String> words = new LinkedList<>();
                String term;
                while (stream.incrementToken()) {
                    term = termAttribute.toString();
                    words.add(term);
                }

                stream.end();
                stream.close();
                return words;
            } catch (final Exception ignored) {
                log.error("Cant decompose : {}", text);
            }
        }
        return new LinkedList<>();
    }


    public static class Data {
        public List<String> words = new ArrayList<>();
        public List<Integer> positions = new ArrayList<>();
        public List<Integer> startOffset = new ArrayList<>();
        public List<Integer> endOffset = new ArrayList<>();
        public List<Float> payloads = new ArrayList<>();
    }

}
