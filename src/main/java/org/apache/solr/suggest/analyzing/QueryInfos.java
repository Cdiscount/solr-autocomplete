package org.apache.solr.suggest.analyzing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class QueryInfos {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public String prefixToken;
    public Set<String> matchedTokens = new LinkedHashSet<>();
    public List<String> matchedTokensConcat = new ArrayList<>();

    public int nbLetters;
    public int nbWords;
    public boolean lastWordFinished;

    private QueryInfos() {
    }

    public static QueryInfos parse(CharSequence text, Analyzer analyzer) {
        QueryInfos queryInfos = new QueryInfos();

        queryInfos.setLastWordFinished(text.charAt(text.length() - 1) == ' ');

        try (TokenStream ts = analyzer.tokenStream("", new StringReader(text.toString()))) {
            ts.reset();

            final CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            final OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);

            String lastToken = null;
            int maxEndOffset = -1;
            while (ts.incrementToken()) {
                if (lastToken != null) {
                    queryInfos.addMatchedTokens(lastToken);
                    queryInfos.addNbLetters(lastToken.length());
                    queryInfos.addOneWord();
                }
                lastToken = termAtt.toString();
                if (lastToken != null) {
                    maxEndOffset = Math.max(maxEndOffset, offsetAtt.endOffset());
                }
            }
            ts.end();

            if (lastToken != null) {
                if (maxEndOffset == offsetAtt.endOffset()) {
                    queryInfos.setPrefixToken(lastToken);
                } else {
                    queryInfos.addMatchedTokens(lastToken);
                }
                queryInfos.addNbLetters(lastToken.length());
                queryInfos.addOneWord();
            }
        } catch (NullPointerException | IOException e) {
            log.warn("failed to parse : {} ", text);
        }

        buildConcat(queryInfos);

        return queryInfos;
    }

    private static void buildConcat(QueryInfos queryInfos) {
        String lastWord = null;
        for (String w : queryInfos.getMatchedTokens()) {
            if(lastWord != null) {
                queryInfos.addMatchedTokensConcat(lastWord+ w);
            }
            lastWord = w;
        }

        if(queryInfos.getPrefixToken() != null && queryInfos.getPrefixToken().trim().length() > 0) {
            queryInfos.addMatchedTokensConcat(lastWord+ queryInfos.getPrefixToken());
        }
    }

    public String getPrefixToken() {
        return prefixToken;
    }

    public void setPrefixToken(String prefixToken) {
        this.prefixToken = prefixToken;
    }

    public Set<String> getMatchedTokens() {
        return matchedTokens;
    }

    public void addMatchedTokens(String matchedToken) {
        this.matchedTokens.add(matchedToken);
    }

    public List<String> getMatchedTokensConcat() {
        return matchedTokensConcat;
    }

    public void addMatchedTokensConcat(String m) {
        this.matchedTokensConcat.add(m);
    }

    public int getNbLetters() {
        return nbLetters;
    }

    public void addNbLetters(int nbLetter) {
        this.nbLetters += nbLetter;
    }

    public int getNbWords() {
        return nbWords;
    }

    public void addOneWord() {
        this.nbWords += 1;
    }

    public boolean isLastWordFinished() {
        return lastWordFinished;
    }

    public void setLastWordFinished(boolean lastWordFinished) {
        this.lastWordFinished = lastWordFinished;
    }
}
