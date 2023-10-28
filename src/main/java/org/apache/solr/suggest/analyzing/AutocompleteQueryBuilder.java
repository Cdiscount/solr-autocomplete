package org.apache.solr.suggest.analyzing;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.query.PositionSpanQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.solr.search.span.BoostedSpanMultiTermQueryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.solr.spelling.suggest.fst.AutocompleteLookupFactory.*;

public class AutocompleteQueryBuilder {
 
    private static final int MIN_LENGTH_FOR_MAXEDITS = 6;
    private static final int MIN_POSITION_FOR_WORDS = 5;

    private static final float[] DEFAULT_COEFF = {0.5f, 0.4f, 0.3f};
    private static final float[][] ALL_COEFF = {
            {15f, 10f, 7f, 5f, 4f, 3f, 2f, 1f},
            {10f, 9f, 8f, 7f, 6f, 5f, 4f, 3f},
            {5f, 3f, 2f, 1f, 0.5f, 0.25f}};

    private static final float MIN_FIRST_COEFF = 0.1f;

    private final int nbWordsForPositionMatch;
    private final Map<String, String> fields;
    private final float coeff;
    private final long docCount;

    public AutocompleteQueryBuilder(Integer nbWordsForPositionMatch, Map<String, String> fields, float coeff, long docCount) {
        this.nbWordsForPositionMatch = nbWordsForPositionMatch == null ?
                AutocompleteQueryBuilder.MIN_LENGTH_FOR_MAXEDITS
                : nbWordsForPositionMatch;

        this.fields = fields;
        this.coeff = coeff;
        this.docCount = docCount;
    }


    public BooleanQuery getQuery(QueryInfos queryInfos, BooleanClause.Occur occ) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();

        int position = 1;

        List<Query> list = new ArrayList<>();
        FieldsAndW field = new FieldsAndW(FIELD, 1f,
                FIELD_PAYLOAD, 0.8f,
                FIELD_NGRAM, 0.9f,
                FIELD_CONCAT_PAYLOAD, 0.8f,
                FIELD_NGRAM_SECOND, 1.3f);

        position = matchedTokens(query, queryInfos, list, field, position, occ);

        // Prefix Token
        list = new ArrayList<>();
        field = new FieldsAndW(
                FIELD_PAYLOAD, 1f,
                FIELD_PAYLOAD, 0.8f,
                FIELD_NGRAM, 1f,
                FIELD_CONCAT_PAYLOAD, 0.8f,
                FIELD_NGRAM_SECOND, 0.3f);
        prefixToken(query, queryInfos, list, field, position, occ);

        // Potential boost
        if (this.fields.get(FIELD_WEIGHT) != null) {
            query.add(new BoostQuery(new FunctionQuery(new FloatFieldSource(this.fields.get(FIELD_WEIGHT))), coeff), occ);
        }

        return query.build();
    }

    private int matchedTokens(BooleanQuery.Builder query, QueryInfos queryInfos, List<Query> list, FieldsAndW field, int position, BooleanClause.Occur occ) {
        for (String token : queryInfos.getMatchedTokens()) {

            if (queryInfos.getNbWords() <= nbWordsForPositionMatch || position < MIN_POSITION_FOR_WORDS) {
                buildNormalAndFuzzyList(list, field, token, position);
                buildNgramList(list, field, token, position);
                buildCutList(list, field, position, position - 1, queryInfos);
                buildConcatList(list, field, token, position);
            } else {
                buildOtherListWithoutPosition(list, field, token, position - 1, queryInfos);
            }

            query.add(new DisjunctionMaxQuery(list, 0f), occ);
            position++;
        }
        return position;
    }

    private void prefixToken(BooleanQuery.Builder query, QueryInfos queryInfos, List<Query> list, FieldsAndW field, int position, BooleanClause.Occur occ) {
        String token = queryInfos.getPrefixToken();

        if (token != null) {
            String fieldNameNormal = this.fields.get(field.fieldnameNormal) != null
                    ? this.fields.get(field.fieldnameNormal)
                    : FIELD;


            String fieldNameNgram = isNgram(field)
                    ? this.fields.get(field.fieldnameNgram)
                    : fieldNameNormal;

            if (queryInfos.getNbWords() <= nbWordsForPositionMatch) {
                buildPrefix(list, field, fieldNameNormal, fieldNameNgram, token, position);
            } else {
                buildPrefixWithoutPosition(list, field, fieldNameNgram, token);
            }

            query.add(new DisjunctionMaxQuery(list, 0f), occ);
        }
    }

    private void buildNormalAndFuzzyList(List<Query> list, FieldsAndW field, String token, int position) {
        String fieldName = this.fields.get(field.fieldnameNormal);
        if (fieldName != null) {
            // normal
            Query q = getPositionSpanQuery(fieldName, false, token, -1, position, field.weightNormal);
            list.add(q);

            // fuzzy
            if (token.length() > 2) {
                Query fuzz = getPositionSpanQuery(fieldName, true, token, 0, position, field.weightFuz);
                list.add(fuzz);
            }
        }
    }

    private void buildNgramList(List<Query> list, FieldsAndW field, String token, int position) {
        // ngram
        if (isNgram(field)) {
            Query ngram = getPositionSpanQuery(this.fields.get(field.fieldnameNgram), false, token, -1, position, field.weightNgram);
            list.add(ngram);

        } else {
            // normal
            String fieldName = this.fields.get(field.fieldnameFuz) != null ? this.fields.get(field.fieldnameFuz) : this.fields.get(field.fieldnameNormal);
            Query q = getPositionSpanQuery(fieldName, false, token, -1, position, field.weightNormal);
            list.add(q);
        }
    }

    private void buildCutList(List<Query> list, FieldsAndW field, int position, int row, QueryInfos queryInfos) {
        if (isNgram(field)) {
            // Cut Word
            String cuttedToken = row >= queryInfos.getMatchedTokensConcat().size() ? null : queryInfos.getMatchedTokensConcat().get(row);
            String fieldNameCut = isNgram(field) ? this.fields.get(field.fieldnameNgram) : this.fields.get(field.fieldnameNormal);
            if (cuttedToken != null) {
                Query cut = getPositionSpanQuery(fieldNameCut, false, cuttedToken, -1, position, field.weightNgramSecond);
                list.add(cut);
            }
        }
    }

    private void buildConcatList(List<Query> list, FieldsAndW field, String token, int position) {
        // concat
        if (isConcat(field)) {
            Query concat = getPositionSpanQuery(this.fields.get(field.fieldnameConcat), false, token, -1, position, field.weightConcat);
            list.add(concat);
        }

        if (isNgramSecond(field)) {
            Query concat = new TermQuery(new Term(this.fields.get(field.fieldnameNgramSecond), token));
            list.add(new BoostQuery(concat, field.weightConcat));
        }
    }

    private void buildOtherListWithoutPosition(List<Query> list, FieldsAndW field, String token, int row, QueryInfos queryInfos) {
        String fieldNormal = this.fields.get(field.fieldnameNormal);


        if (fieldNormal != null) {
            // Normal
            Query q = new TermQuery(new Term(fieldNormal, token));
            list.add(new BoostQuery(q, field.weightNormal));

            // Fuzzy
            if (token.length() > 4) {
                Query fuzz = new FuzzyQuery(new Term(fieldNormal, token),
                        token.length() > MIN_LENGTH_FOR_MAXEDITS ? 2 : 1, 1);
                list.add(new BoostQuery(fuzz, field.weightFuz));
            }
        }

        // Ngram
        if (isNgram(field)) {
            Query ngram = new TermQuery(new Term(this.fields.get(field.fieldnameNgram), token));
            list.add(new BoostQuery(ngram, field.weightNgram));
        }

        // concat
        if (isConcat(field)) {
            Query concat = new TermQuery(new Term(this.fields.get(field.fieldnameConcat), token));
            list.add(new BoostQuery(concat, field.weightConcat));
        }

        // Cut Word
        String cuttedToken = row >= queryInfos.getMatchedTokensConcat().size() ? null : queryInfos.getMatchedTokensConcat().get(row);
        if (cuttedToken != null && fieldNormal != null) {
            Query cut = new TermQuery(new Term(fieldNormal, cuttedToken));
            list.add(new BoostQuery(cut, field.weightNgramSecond));
        }
    }

    private void buildPrefix(List<Query> list, FieldsAndW field, String fieldNameNormal, String fieldNameNgram, String token, int position) {
        // normal
        Query normal = getPositionSpanQuery(fieldNameNormal, false, token, -1, position, field.weightNormal);
        list.add(normal);

        // ngram
        Query ngram = getPositionSpanQuery(fieldNameNgram, false, token, -1, position, field.weightNgram);
        list.add(ngram);

        // fuzzy Ngram
        if (token.length() > 1) {
            Query fuzz = getPositionSpanQuery(fieldNameNgram, true, token, 1, position, field.weightFuz);
            list.add(fuzz);
        }

        if (token.length() > 2) {
            Query fuzz2 = getPositionSpanQuery(fieldNameNormal, true, token, 0, position, field.weightFuz);
            list.add(fuzz2);
        }
        // Second Ngram
        if (isNgramSecond(field)) {
            Query secondNgram = getPositionSpanQuery(this.fields.get(field.fieldnameNgramSecond), false, token, -1, position > 1 ? position - 1 : position, field.weightNgramSecond);
            list.add(secondNgram);
        }

        // concat
        if (token.length() > 3 && isConcat(field)) {
            Query concat = new TermQuery(new Term(this.fields.get(field.fieldnameConcat), token));
            list.add(new BoostQuery(concat, field.weightConcat));
        }
    }

    private void buildPrefixWithoutPosition(List<Query> list, FieldsAndW field, String fieldName, String token) {
        Query ngram = new TermQuery(new Term(fieldName, token));
        list.add(new BoostQuery(ngram, field.weightNgram));

        // Second Ngram
        if (isNgramSecond(field)) {
            Query secondNgram = new TermQuery(new Term(this.fields.get(field.fieldnameNgramSecond), token));
            list.add(new BoostQuery(secondNgram, field.weightNgramSecond));
        }

        if (token.length() > 2) {
            Query fuzz = new FuzzyQuery(new Term(fieldName, token),
                    token.length() > MIN_LENGTH_FOR_MAXEDITS ? 2 : 1, 1);
            list.add(new BoostQuery(fuzz, field.weightFuz));
        }
        // concat
        if (isConcat(field)) {
            Query concat = new TermQuery(new Term(this.fields.get(field.fieldnameConcat), token));
            list.add(new BoostQuery(concat, field.weightConcat));
        }
    }

    private Query getPositionSpanQuery(String fieldName, boolean fuzzyMode, String text, Integer prefixLength, int position, float boost) {
        SpanQuery span;

        if (fuzzyMode) {
            int maxEdits = text.length() > MIN_LENGTH_FOR_MAXEDITS ? 2 : 1;
            FuzzyQuery fuzzyQuery = new FuzzyQuery(new Term(fieldName, text), maxEdits, prefixLength, 25, true);
            span = new BoostedSpanMultiTermQueryWrapper<>(fuzzyQuery, docCount);
        } else {
            span = new SpanTermQuery(new Term(fieldName, text));
        }
        return new PositionSpanQuery(span, false, position, true,
                ALL_COEFF, DEFAULT_COEFF, MIN_FIRST_COEFF, boost);
    }


    public static class FieldsAndW {
        public String fieldnameNormal;
        public float weightNormal;
        public String fieldnameFuz;
        public float weightFuz;
        public String fieldnameNgram;
        public float weightNgram;
        public String fieldnameNgramSecond;
        public float weightNgramSecond;
        public String fieldnameConcat;
        public float weightConcat;

        private FieldsAndW(String fieldnameNormal, float weightNormal,
                           String fieldnameFuz, float weightFuz,
                           String fieldnameNgram, float weightNgram,
                           String fieldnameConcat, float weightConcat,
                           String fieldnameNgramSecond, float weightNgramSecond
        ) {
            this.fieldnameNormal = fieldnameNormal;
            this.weightNormal = weightNormal;
            this.fieldnameFuz = fieldnameFuz;
            this.weightFuz = weightFuz;
            this.fieldnameNgram = fieldnameNgram;
            this.weightNgram = weightNgram;
            this.fieldnameConcat = fieldnameConcat;
            this.weightConcat = weightConcat;
            this.fieldnameNgramSecond = fieldnameNgramSecond;
            this.weightNgramSecond = weightNgramSecond;
        }
    }

    private boolean isNgram(FieldsAndW field) {
        return field.fieldnameNgram != null && this.fields.get(field.fieldnameNgram) != null;
    }

    private boolean isConcat(FieldsAndW field) {
        return field.fieldnameConcat !=  null && this.fields.get(field.fieldnameConcat) != null;
    }

    private boolean isNgramSecond(FieldsAndW field) {
        return field.fieldnameNgramSecond != null && this.fields.get(field.fieldnameNgramSecond) != null;
    }
}
