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
package org.apache.solr.handler.component;


import org.apache.lucene.analysis.WordsParser;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrEventListener;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.spelling.suggest.AutocompleteSolrSuggester;
import org.apache.solr.spelling.suggest.CdiscountSuggesterOptionsUtils;
import org.apache.solr.spelling.suggest.LocalSuggesterOptions;
import org.apache.solr.spelling.suggest.SolrSuggester;
import org.apache.solr.spelling.suggest.SuggesterResult;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.json.internal.json_simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SuggestComponent: interacts with multiple {@link AutocompleteSolrSuggester} to serve up suggestions
 * Responsible for routing commands and queries to the appropriate {@link AutocompleteSolrSuggester}
 * and for initializing them as specified by SolrConfig
 */
public class AutocompleteComponent extends SuggestComponent {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Name used to identify whether the user query concerns this component
     */
    public static final String COMPONENT_NAME = "suggest";


    /**
     * SolrConfig label to identify  Config time settings
     */
    private static final String CONFIG_PARAM_LABEL = "suggester";
    private static final String CONFIG_SUGGEST = "suggest.";
    private static final String CONFIG_PARAM_MIN_SIZE = "minSizeQuery";
    public static final String CONFIG_FIRST_CONTEXT_ONLY = "firstContextOnly";
    public static final String AUTOCOMPLETE_CACHE = "cacheName";
    public static final String AUTOCOMPLETE_MAX_QUERY_LENGTH_CACHE = "maxQueryLengthCache";
    public static final String CONTEXT_JSONFY = "contextJsonify";


    private static final Integer DEFAULT_MIN_SIZE_QUERY = 1;
    private static final Integer MAX_CACHE_QUERY_LENGTH = 2;

    private static class SuggesterResultLabels {
        static final String SUGGEST = "suggest";
        static final String HITS = "hits";
        static final String SUGGESTION_NUM_FOUND = "numFound";
        static final String SUGGESTION_SEARCH = "search";
        static final String SUGGESTION_SEARCH_HIGHLIGHT = "searchHighlight";
        static final String SUGGESTION_SCORE = "score";
        static final String SUGGESTION_CAT = "categories";
    }

    private int minSizeQuery = 1;
    private int minSizeQueryConfig = 1;
    private boolean firstContextOnly = true;
    private boolean firstContextOnlyConfig = true;

    private int maxQueryLengthCache;
    private String cacheName;
    private SolrCache<String, SuggesterResult> cache;
    private Boolean jsonifyContext;


    @Override
    @SuppressWarnings("unchecked")
    public void init(@SuppressWarnings({"rawtypes"}) NamedList args) {
        super.init(args);
    }


    @Override
    public void inform(SolrCore core) {
        if (initParams != null) {
            log.info("Initializing SuggestComponent");
            boolean hasDefault = false;
            for (int i = 0; i < initParams.size(); i++) {
                if (initParams.getName(i).equals(CONFIG_PARAM_LABEL)) {
                    @SuppressWarnings({"rawtypes"})
                    NamedList suggesterParams = (NamedList) initParams.getVal(i);
                    AutocompleteSolrSuggester suggester = new AutocompleteSolrSuggester();
                    String dictionary = suggester.init(suggesterParams, core);
                    if (dictionary != null) {
                        boolean isDefault = dictionary.equals(AutocompleteSolrSuggester.DEFAULT_DICT_NAME);
                        if (isDefault && !hasDefault) {
                            hasDefault = true;
                        } else if (isDefault) {
                            throw new RuntimeException("More than one dictionary is missing name.");
                        }
                        suggesters.put(dictionary, suggester);
                    } else {
                        if (!hasDefault) {
                            suggesters.put(AutocompleteSolrSuggester.DEFAULT_DICT_NAME, suggester);
                            hasDefault = true;
                        } else {
                            throw new RuntimeException("More than one dictionary is missing name.");
                        }
                    }
                }
            }

            for (int i = 0; i < initParams.size(); i++) {
                if (initParams.getName(i).equals(CONFIG_PARAM_LABEL)) {
                    @SuppressWarnings({"rawtypes"})
                    NamedList suggesterParams = (NamedList) initParams.getVal(i);
                    minSizeQueryConfig = (Integer) suggesterParams.get(CONFIG_PARAM_MIN_SIZE, DEFAULT_MIN_SIZE_QUERY);
                    firstContextOnlyConfig = suggesterParams.getBooleanArg(CONFIG_FIRST_CONTEXT_ONLY);

                    cacheName = (String) suggesterParams.get(AUTOCOMPLETE_CACHE);
                    maxQueryLengthCache = (Integer) suggesterParams.get(AUTOCOMPLETE_MAX_QUERY_LENGTH_CACHE, MAX_CACHE_QUERY_LENGTH);

                    jsonifyContext = suggesterParams.getBooleanArg(CONTEXT_JSONFY);
                }
            }

            AutocompleteLocalSuggesterListener listener = new AutocompleteLocalSuggesterListener();
            core.registerFirstSearcherListener(listener);
            core.registerNewSearcherListener(listener);
        }
    }

    /**
     * Responsible for using the specified suggester to get the suggestions
     * for the query and write the results
     */
    @Override
    public void process(ResponseBuilder rb) throws IOException {
        SolrParams params = rb.req.getParams();
        log.info("SuggestComponent process with : {}", params);
        if (!params.getBool(COMPONENT_NAME, false) || suggesters.isEmpty()) {
            return;
        }

        // Get cache from config
        cache = cacheName != null ? rb.req.getSearcher().getCache(cacheName) : null;

        boolean buildAll = params.getBool(SUGGEST_BUILD_ALL, false);
        boolean reloadAll = params.getBool(SUGGEST_RELOAD_ALL, false);
        Set<AutocompleteSolrSuggester> querySuggesters;
        try {
            querySuggesters = getLocalSuggesters(params);
        } catch (SolrException ex) {
            if (!buildAll && !reloadAll) {
                throw ex;
            } else {
                querySuggesters = new HashSet<>();
            }
        }

        String query = params.get(SUGGEST_Q);
        if (query == null) {
            query = rb.getQueryString();
            if (query == null) {
                query = params.get(CommonParams.Q);
            }
        }

        processQuery(rb, params, query, querySuggesters);
    }

    private void processQuery(ResponseBuilder rb, SolrParams params, String query, Set<AutocompleteSolrSuggester> querySuggesters) throws IOException {
        if (query != null) {

            int queryLength = query.length();
            // check Query size
            if (queryLength < minSizeQuery) {
                rb.rsp.add(SuggesterResultLabels.SUGGEST, Collections.emptyMap());
                return;
            }

            query = WordsParser.cleanRepetitionText(query);

            int count = params.getInt(SUGGEST_COUNT, 1);
            boolean highlight = params.getBool(SUGGEST_HIGHLIGHT, true);
            boolean allTermsRequired = params.getBool(SUGGEST_ALL_TERMS_REQUIRED, true);
            String contextFilter = params.get(SUGGEST_CONTEXT_FILTER_QUERY);
            if (contextFilter != null) {
                contextFilter = contextFilter.trim();
                if (contextFilter.length() == 0) {
                    contextFilter = null;
                }
            }

            minSizeQuery = params.getInt(CONFIG_SUGGEST + CONFIG_PARAM_MIN_SIZE, minSizeQueryConfig);
            firstContextOnly = params.getBool(CONFIG_SUGGEST + CONFIG_FIRST_CONTEXT_ONLY, firstContextOnlyConfig);

            LocalSuggesterOptions options = new LocalSuggesterOptions(rb.req.getSearcher(), new CharsRef(query), count, contextFilter, allTermsRequired, highlight, firstContextOnly);

            processOverSuggesters(rb, queryLength, options, querySuggesters);
        }
    }

    private void processOverSuggesters(ResponseBuilder rb, int queryLength, LocalSuggesterOptions options, Set<AutocompleteSolrSuggester> querySuggesters) throws IOException {
        Map<String, SimpleOrderedMap<NamedList<Object>>> namedListResults = new HashMap<>();
        for (AutocompleteSolrSuggester suggester : querySuggesters) {

            // Cache
            SuggesterResult suggesterResult;
            if (cache != null && queryLength <= maxQueryLengthCache) {
                String key = CdiscountSuggesterOptionsUtils.keyCache(suggester.getName(), options);

                suggesterResult = cache.get(key);
                if (suggesterResult == null) {
                    suggesterResult = suggester.getSuggestions(options);
                    cache.put(key, suggesterResult);
                }
            } else {
                suggesterResult = suggester.getSuggestions(options);

            }
            toNamedListLocal(suggesterResult, namedListResults);
        }
        rb.rsp.add(SuggesterResultLabels.SUGGEST, namedListResults);
    }

    private Set<AutocompleteSolrSuggester> getLocalSuggesters(SolrParams params) {
        Set<AutocompleteSolrSuggester> autocompleteSolrSuggesters = new HashSet<>();
        for (String suggesterName : getLocalSuggesterNames(params)) {
            SolrSuggester ss = suggesters.get(suggesterName);
            AutocompleteSolrSuggester curSuggester = (AutocompleteSolrSuggester) ss;
            if (curSuggester != null) {
                autocompleteSolrSuggesters.add(curSuggester);
            } else {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "No suggester named " + suggesterName + " was configured");
            }
        }
        if (autocompleteSolrSuggesters.isEmpty()) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "'" + SUGGEST_DICT + "' parameter not specified and no default suggester configured");
        }
        return autocompleteSolrSuggesters;

    }

    private Set<String> getLocalSuggesterNames(SolrParams params) {
        Set<String> suggesterNames = new HashSet<>();
        String[] suggesterNamesFromParams = params.getParams(SUGGEST_DICT);
        if (suggesterNamesFromParams == null) {
            suggesterNames.add(AutocompleteSolrSuggester.DEFAULT_DICT_NAME);
        } else {
            suggesterNames.addAll(Arrays.asList(suggesterNamesFromParams));
        }
        return suggesterNames;
    }

    /**
     * Convert {@link SuggesterResult} to NamedList for constructing responses
     */
    private void toNamedListLocal(SuggesterResult suggesterResult, Map<String, SimpleOrderedMap<NamedList<Object>>> resultObj) {

        for (String suggesterName : suggesterResult.getSuggesterNames()) {
            SimpleOrderedMap<NamedList<Object>> results = new SimpleOrderedMap<>();

            for (String token : suggesterResult.getTokens(suggesterName)) {
                SimpleOrderedMap<Object> suggestionBody = new SimpleOrderedMap<>();
                List<LookupResult> lookupResults = suggesterResult.getLookupResult(suggesterName, token);
                suggestionBody.add(SuggesterResultLabels.SUGGESTION_NUM_FOUND, lookupResults.size());


                List<SimpleOrderedMap<Object>> suggestEntriesNamedList = new ArrayList<>();
                overLookupResults(lookupResults, suggestEntriesNamedList);
                suggestionBody.add(SuggesterResultLabels.HITS, suggestEntriesNamedList);
                results.add("response", suggestionBody);
            }
            resultObj.put(suggesterName, results);
        }
    }

    private void overLookupResults(List<LookupResult> lookupResults, List<SimpleOrderedMap<Object>> suggestEntriesNamedList) {
        SimpleOrderedMap<Object> suggestEntryNamedList;

        JSONParser parser = (jsonifyContext != null && jsonifyContext) ? new JSONParser() : null;

        boolean first = firstContextOnly;
        for (LookupResult lookupResult : lookupResults) {
            suggestEntryNamedList = new SimpleOrderedMap<>();
            suggestEntriesNamedList.add(suggestEntryNamedList);

            if(lookupResult.highlightKey != null) {
                suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_SEARCH_HIGHLIGHT, lookupResult.highlightKey.toString());
            }
            if(lookupResult.key != null) {
                suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_SEARCH, lookupResult.key.toString());
            }
            suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_SCORE, lookupResult.value);

            first = manageContexts(lookupResult, suggestEntryNamedList, first, parser);
        }
    }

    private boolean manageContexts(LookupResult lookupResult, SimpleOrderedMap<Object> suggestEntryNamedList,
                                   boolean first, JSONParser parser) {
        // Categories
        Set<BytesRef> categoriesByt = lookupResult.contexts;
        String categoriesText = null;
        if (firstContextOnly) {
            if (first && categoriesByt != null && !categoriesByt.isEmpty()) {
                categoriesText = categoriesByt.iterator().next().utf8ToString();
                first = false;
            }
        } else if (categoriesByt != null && !categoriesByt.isEmpty()) {
            categoriesText = categoriesByt.iterator().next().utf8ToString();
        }
        if (categoriesText != null) {
            try {
                suggestEntryNamedList.add(SuggesterResultLabels.SUGGESTION_CAT, parser != null ? parser.parse(categoriesText) : categoriesText);
            } catch (ParseException e) {
                log.error("Autocomplete Context parsing : ", e);
            }
        }

        return first;
    }

    /**
     * Listener to build or reload the maintained {@link AutocompleteSolrSuggester} by this component
     */
    private class AutocompleteLocalSuggesterListener implements SolrEventListener {

        public AutocompleteLocalSuggesterListener() {
            // Nothing to do
        }

        @Override
        public void init(@SuppressWarnings({"rawtypes"}) NamedList args) {
            // Nothing to do
        }

        @Override
        public void newSearcher(SolrIndexSearcher newSearcher,
                                SolrIndexSearcher currentSearcher) {
            if (cache != null) {
                cache.clear();
            }
        }

        @Override
        public void postCommit() {
            // Nothing to do
        }

        @Override
        public void postSoftCommit() {
            // Nothing to do
        }

        @Override
        public String toString() {
            return "AutocompleteLocalSuggesterListener";
        }

    }

}
