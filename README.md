<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

Welcome to Peaksys Solr Autocomplete component!
========

Peaksys Solr Autocomplete provides an autocompletion module that helps users save time and effort by suggesting or completing words or phrases as they type. 
This README provides an overview of the key features of our autocompletion component.

## ✨ Features

- Multi Words Matching

Our autocompletion system excels at matching multiple words. It can suggest completions for phrases or sentences,

- Partially Written Word Matching

Typing can be imprecise, and users often start with only a portion of the word they're looking for. Our autocompletion system handles partially written words gracefully.

- Faults Tolerance

Mistakes happen, and our autocompletion system understands that. It has built-in fault tolerance, which means it can recognize and correct common typographical errors. Whether it's a misspelled word, a misplaced letter, or a small error in the input, it strives to provide accurate suggestions.

- Concatenated and Split Words Matching

Sometimes, words are concatenated without spaces, and this component can handle these cases as well. If a user types a phrase without spaces, our system can recognize and suggest completions, ensuring a smooth and seamless experience.
In situations where users want to find results for words that are separated by spaces, our autocompletion system is equally effective. It accommodates split word matching, understanding that spaces may be inserted between words in various contexts.



## ✨ Tricks in configuration

```xml
 <searchComponent name="autocomplete" class="solr.AutocompleteComponent">
    <lst name="suggester">
      <str name="name">default</str>
      <str name="lookupImpl">AutocompleteLookupFactory</str>
      <str name="impl">AutocompleteSuggester</str>
      <str name="dictionaryImpl">DocumentDictionaryFactory</str>
      <str name="cacheName">autocompleteCache</str>
      <str name="suggestAnalyzerFieldType">text</str>

      <!-- field to index -->
      <str name="field">search</str>

      <!-- technical fields to NOT index  -->
      <str name="payloadField">search_payload</str>
      <str name="ngramField">search_ngram</str>
      <str name="ngramSecondField">search_ngram_second</str>
      <str name="concatPayloadField">search_payload_concat</str>
      <str name="concatNgramField">search_ngram_concat</str>
      <str name="displayField">search_text</str>

      <!-- contextField can contain text or json -->
      <str name="contextField">jsonText</str>
      <!-- allow to present contextField as a json if complex structure in it-->
      <bool name="contextJsonify">true</bool>

      <!-- weight field -->
      <str name="weightField">weight</str>
      <str name="weightCoefficient">5</str>

      <!-- min size of q parameter allowed to search -->
      <int name="minSizeQuery">1</int>

      <!-- results cached when on x first caracters of q parameter-->
      <int name="maxQueryLengthCache">3</int>

      <!-- if word number is superior to this threshold, word's position is deactivated for this query -->
      <int name="maxNbWordsForPositionMatch">4</int>

      <!-- if true, only the first doc will have its contextField returned -->
      <bool name="firstContextOnly">false</bool>
    </lst>
  </searchComponent>
```

## ✨ Getting Started

### Install
Required to compile sources : Git, Java 11 and Maven

- Copy source, 
- Compile code,
- and Copy the jar file in your solr repository (to all SolR servers if many).

```bash
  git clone <source>
  mvn clean install
  cp target/*.jar to your solr libs path (/solr/webapp/WEB-INF/lib/)
```

### Configuration

- Push the configuration in your zk cluster (it depends if you are in Standalone or SolRCloud mode)
```bash
zkcli -zkhost <zk_host>:<zk_port> -cmd upconfig -confdir ./config/autocomplete -confname autocomplete
```

- Create a collection with a shard

```bash
http://<server>:<port>/solr/admin/collections?action=CREATE&name=autocomplete&createNodeSet=EMPTY&collection.configName=autocomplete&node=<server>:<port>_solr&numShards=1&maxShardsPerNode=1

http://<server>:<port>/solr/admin/collections?action=ADDREPLICA&collection=autocomplete&type=TLOG&wt=json&dataDir=<dataDirectory>&shard=shard1

```

### Push your data

- Index your data with this model or with any change you want to do in schema.xml

```json
{
  "id":"1", 
  "search": "apple iphone 16",
  "weight": 50961,
  "jsonText" : ""
}
```

### Test it !

```http
http://<server>:<port>/solr/<collection_name>/autocomplete?q=apple
```




## ❓ Troubleshooting
You can ask any question to ct-find[at]cdiscount.com <br/>
We will attempt to respond, but we cannot guarantee addition of any updates or features. This topic is now closed.<br/>
We are confident that we will upgrade this plugin to Solr 9.x soon...

# Happy autocompleting! 🚀

## 📄 License
Apache Software Foundation (ASF) LICENSE-2.0

@Peaksys / CtFind "some search, others find" - 2021-2023