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
package org.apache.solr.update.processor;

import org.apache.lucene.analysis.CustomAnalyzer;
import org.apache.lucene.analysis.WordsParser;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import static org.apache.solr.update.processor.FieldValueMutatingUpdateProcessor.valueMutator;

/**
 *
 */
public final class WordConcatFieldUpdateProcessorFactory extends FieldMutatingUpdateProcessorFactory {

    @Override
    public FieldMutatingUpdateProcessor.FieldNameSelector
    getDefaultSelector(final SolrCore core) {
        return FieldMutatingUpdateProcessor.SELECT_ALL_FIELDS;
    }

    @Override
    public UpdateRequestProcessor getInstance(SolrQueryRequest req,
                                              SolrQueryResponse rsp,
                                              UpdateRequestProcessor next) {
        return valueMutator(getSelector(), next, src -> {
            if (src instanceof CharSequence) {
                CharSequence s = (CharSequence) src;
                return WordsParser.recomposeText(
                        WordsParser.concatWords(
                                WordsParser.decomposeText(new CustomAnalyzer(), "", s.toString()),
                                2, true, true),
                        " ", true);
            }

            return src;
        });
    }
}

