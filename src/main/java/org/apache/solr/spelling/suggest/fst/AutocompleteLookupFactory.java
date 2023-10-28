/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.spelling.suggest.fst;

import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.store.FSDirectory;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.suggest.analyzing.AutocompleteSuggester;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.solr.handler.component.AutocompleteComponent.CONFIG_FIRST_CONTEXT_ONLY;

/**
 * Factory for {@link AutocompleteLookupFactory}
 *
 * @lucene.experimental
 */
public class AutocompleteLookupFactory extends AnalyzingInfixLookupFactory {

    private static final String FILENAME = "bifsta.bin";
    public static final String FIELD = "field";
    public static final String FIELD_PAYLOAD = "payloadField";
    public static final String FIELD_NGRAM = "ngramField";
    public static final String FIELD_NGRAM_SECOND = "ngramSecondField";
    public static final String FIELD_CONCAT_PAYLOAD = "concatPayloadField";
    public static final String FIELD_CONCAT_NGRAM = "concatNgramField";

    public static final String FIELD_WEIGHT = "weightField";
    public static final String WEIGHT_COEFF = "weightCoefficient";

    public static final String FIELD_TEXT = "displayField";
    public static final String FIELD_CONTEXT = "contextField";

    public static final String MAX_NB_WORDS_FOR_POSITION_MATCH = "maxNbWordsForPositionMatch";

    @Override
    public Lookup create(@SuppressWarnings({"rawtypes"}) NamedList params, SolrCore core) {

        // optional parameters
        String indexPath = params.get(INDEX_PATH) != null
                ? params.get(INDEX_PATH).toString() : "index";
        if (!new File(indexPath).isAbsolute()) {
            indexPath = core.getDataDir() + File.separator + "index";
        }

        int minPrefixChars = params.get(MIN_PREFIX_CHARS) != null
                ? Integer.parseInt(params.get(MIN_PREFIX_CHARS).toString())
                : AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS;

        boolean allTermsRequired = params.get(ALL_TERMS_REQUIRED) != null
                ? Boolean.getBoolean(params.get(ALL_TERMS_REQUIRED).toString())
                : AnalyzingInfixSuggester.DEFAULT_ALL_TERMS_REQUIRED;

        boolean highlight = params.get(HIGHLIGHT) != null
                ? Boolean.getBoolean(params.get(HIGHLIGHT).toString())
                : AnalyzingInfixSuggester.DEFAULT_HIGHLIGHT;

        Integer nbWordsForPositionMatch = params.get(MAX_NB_WORDS_FOR_POSITION_MATCH) == null
                ? null
                : Integer.parseInt(params.get(MAX_NB_WORDS_FOR_POSITION_MATCH).toString());

        boolean firstContextOnly = params.get(CONFIG_FIRST_CONTEXT_ONLY) != null && Boolean.parseBoolean(params.get(CONFIG_FIRST_CONTEXT_ONLY).toString());

        // Fieldnames
        Map<String, String> fields = new HashMap<>();
        buildFields(FIELD, params, fields);
        buildFields(FIELD_PAYLOAD, params, fields);
        buildFields(FIELD_NGRAM, params, fields);
        buildFields(FIELD_NGRAM_SECOND, params, fields);
        buildFields(FIELD_CONCAT_PAYLOAD, params, fields);
        buildFields(FIELD_CONCAT_NGRAM, params, fields);

        buildFields(FIELD_WEIGHT, params, fields);
        buildFields(FIELD_TEXT, params, fields);
        buildFields(FIELD_CONTEXT, params, fields);


        float coeff = params.get(WEIGHT_COEFF) != null
                ? Float.parseFloat(params.get(WEIGHT_COEFF).toString())
                : 1f;
        Similarity configSimilarity = core.getLatestSchema() != null ? core.getLatestSchema().getSimilarity() : null;

        try {
            return new AutocompleteSuggester(FSDirectory.open(new File(indexPath).toPath()),
                    minPrefixChars, allTermsRequired, highlight, fields, coeff, nbWordsForPositionMatch, configSimilarity, firstContextOnly);
        } catch (IOException e) {
            throw new AutocompleteRuntimeException(e);
        }
    }

    /**
     * Find fields name in params
     *
     * @param fieldName
     * @param params
     * @param fields
     */
    private void buildFields(String fieldName, NamedList<String> params, Map<String, String> fields) {
        if (fields == null) {
            fields = new HashMap<>();
        }
        String fieldValue = params.get(fieldName) == null ? null : params.get(fieldName);

        if (fieldValue != null) {
            fields.put(fieldName, fieldValue);
        }
    }

    @Override
    public String storeFileName() {
        return FILENAME;
    }

    public static class AutocompleteRuntimeException extends RuntimeException {
        public AutocompleteRuntimeException(Throwable cause) {
            super(cause);
        }
    }

}
