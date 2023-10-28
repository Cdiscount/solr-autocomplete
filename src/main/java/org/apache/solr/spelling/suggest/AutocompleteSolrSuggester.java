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
package org.apache.solr.spelling.suggest;

import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CloseHook;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.suggest.analyzing.AutocompleteSuggester;
import org.apache.solr.update.SolrCoreState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

import static org.apache.solr.common.params.CommonParams.NAME;

/**
 * Responsible for loading the lookup and dictionary Implementations specified by
 * the SolrConfig.
 * Interacts (query/build/reload) with Lucene Suggesters through {@link Lookup} and
 * {@link Dictionary}
 */
public class AutocompleteSolrSuggester extends SolrSuggester {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private String sourceLocation;
    private File storeDir;
    private Dictionary dictionary;
    private Lookup lookup;
    private String lookupImpl;
    private String dictionaryImpl;
    private String name;

    private LookupFactory factory;
    private DictionaryFactory dictionaryFactory;

    /**
     * Uses the <code>config</code> and the <code>core</code> to initialize the underlying
     * Lucene suggester
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public String init(NamedList<?> config, SolrCore core) {
        super.init(config, core);

        log.info("init: {}", config);

        // read the config
        name = config.get(NAME) != null ? (String) config.get(NAME)
                : DEFAULT_DICT_NAME;
        sourceLocation = (String) config.get(LOCATION);
        lookupImpl = (String) config.get(LOOKUP_IMPL);
        dictionaryImpl = (String) config.get(DICTIONARY_IMPL);
        String store = (String) config.get(STORE_DIR);

        if (lookupImpl == null) {
            lookupImpl = LookupFactory.DEFAULT_FILE_BASED_DICT;
            log.info("No {} parameter was provided falling back to {}", LOOKUP_IMPL, lookupImpl);
        }

        // initialize appropriate lookup instance
        factory = core.getResourceLoader().newInstance(lookupImpl, LookupFactory.class);
        lookup = factory.create(config, core);

        if (lookup != null && lookup instanceof Closeable) {
            core.addCloseHook(new CloseHook() {
                @Override
                public void preClose(SolrCore core) {
                    try {
                        ((Closeable) lookup).close();
                    } catch (IOException e) {
                        log.warn("Could not close the suggester lookup.", e);
                    }
                }

                @Override
                public void postClose(SolrCore core) {
                    throw new UnsupportedOperationException();
                }
            });
        }

        // if store directory is provided make it or load up the lookup with its content
        if (store != null && !store.isEmpty()) {
            storeDir = new File(store);
            if (!storeDir.isAbsolute()) {
                storeDir = new File(core.getDataDir() + File.separator + storeDir);
            }
            if (!storeDir.exists()) {
                storeDir.mkdirs();
            } else if (getStoreFile().exists()) {
                if (log.isDebugEnabled()) {
                    log.debug("attempt reload of the stored lookup from file {}", getStoreFile());
                }
                try {
                    lookup.load(new FileInputStream(getStoreFile()));
                } catch (IOException e) {
                    log.warn("Loading stored lookup data failed, possibly not cached yet");
                }
            }
        }

        // dictionary configuration
        if (dictionaryImpl == null) {
            dictionaryImpl = (sourceLocation == null) ? DictionaryFactory.DEFAULT_INDEX_BASED_DICT :
                    DictionaryFactory.DEFAULT_FILE_BASED_DICT;
            log.info("No {} parameter was provided falling back to {}", DICTIONARY_IMPL, dictionaryImpl);
        }

        dictionaryFactory = core.getResourceLoader().newInstance(dictionaryImpl, DictionaryFactory.class);
        dictionaryFactory.setParams(config);
        log.info("Dictionary loaded with params: {}", config);

        return name;
    }

    /**
     * Build the underlying Lucene Suggester
     */
    @Override
    public void build(SolrCore core, SolrIndexSearcher searcher) throws IOException {
        super.build(core, searcher);

        log.info("AutocompleteSolrSuggester.build({})", name);

        dictionary = dictionaryFactory.create(core, searcher);
        try {
            lookup.build(dictionary);
        } catch (AlreadyClosedException e) {
            RuntimeException e2 = new SolrCoreState.CoreIsClosedException
                    ("Suggester build has been interrupted by a core reload or shutdown.");
            e2.initCause(e);
            throw e2;
        }
        if (storeDir != null) {
            File target = getStoreFile();
            if (!lookup.store(new FileOutputStream(target))) {
                log.error("Store Lookup build failed");
            } else {
                if (log.isInfoEnabled()) {
                    log.info("Stored suggest data to: {}", target.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Reloads the underlying Lucene Suggester
     */
    @Override
    public void reload(SolrCore core, SolrIndexSearcher searcher) throws IOException {
        super.reload(core, searcher);

        log.info("AutocompleteSolrSuggester.reload({})", name);
        if (dictionary == null && storeDir != null) {
            File lookupFile = getStoreFile();
            if (lookupFile.exists()) {
                // this may be a firstSearcher event, try loading it
                FileInputStream is = new FileInputStream(lookupFile);
                try {
                    if (lookup.load(is)) {
                        return;  // loaded ok
                    }
                } finally {
                    IOUtils.closeWhileHandlingException(is);
                }
            } else {
                log.info("lookup file doesn't exist");
            }
        }
    }

    /**
     * @return the file where this suggester is stored.
     * null if no storeDir was configured
     */
    @Override
    public File getStoreFile() {
        if (storeDir == null) {
            return null;
        }
        return new File(storeDir, factory.storeFileName());
    }

    /**
     * Returns suggestions based on the {@link SuggesterOptions} passed
     */
    public SuggesterResult getSuggestions(LocalSuggesterOptions options) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("getSuggestions: {}", options.token);
        }
        if (lookup == null) {
            log.info("Lookup is null - invoke suggest.build first");
            return EMPTY_RESULT;
        }

        SuggesterResult res = new SuggesterResult();
        List<LookupResult> suggestions;
        if (options.contextFilterQuery == null) {
            if (lookup instanceof AutocompleteSuggester) {
                suggestions = ((AutocompleteSuggester) lookup).lookup(options, false);
            } else {
                suggestions = lookup.lookup(options.token, false, options.count);
            }
            res.add(getName(), options.token.toString(), suggestions);
            return res;
        } else {
            return super.getSuggestions(options);
        }

    }

}
