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
package org.apache.solr.suggest.analysis;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.util.BaseTestHarness;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.xpath.XPathExpressionException;
import java.lang.invoke.MethodHandles;

public class AutocompleteSuggesterNgramTest extends SolrTestCaseJ4 {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final String HANDLER = "/autocomplete";

    @BeforeClass
    public static void beforeClass() throws Exception {
        System.setProperty("enable.update.log", "false");
        System.setProperty("solr.directoryFactory", "solr.StandardDirectoryFactory");
        initCore("solrconfig3.xml", "schema.xml");
    }

    @After
    public void afterClass() throws Exception {
        super.tearDown();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        clearIndex();
        index();
        assertU(commit());
        assertU(optimize());
    }

    public static void index() {
        assertU(adoc("id", "1", "search", "apple iphone", "weight", "100"));
        assertU(adoc("id", "2", "search", "apple iphone 11", "weight", "200"));
        assertU(adoc("id", "3", "search", "apple iphone 12", "weight", "300"));
        assertU(adoc("id", "4", "search", "apple iphone 13", "weight", "100"));
        assertU(adoc("id", "5", "search", "apple ipad", "weight", "500"));
        assertU(adoc("id", "6", "search", "thermomix", "weight", "100"));
        assertU(adoc("id", "7", "search", "thermo mix", "weight", "500"));
        assertU(adoc("id", "8", "search", "red bike", "weight", "100"));
        assertU(adoc("id", "9", "search", "bike red", "weight", "600"));

    }

    @Test
    public void testBasic() {
        SolrQueryRequest request = req("q", "apple");

        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='5']";

        String test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='searchHighlight' and text()='[apple] ipad']";
        String test2 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[2]/str[@name='searchHighlight' and text()='[apple] iphone 12']";
        String test3 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[3]/str[@name='searchHighlight' and text()='[apple] iphone 11']";
        String test4 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[4]/str[@name='searchHighlight' and text()='[apple] iphone']";
        String test5 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[5]/str[@name='searchHighlight' and text()='[apple] iphone 13']";

        assertQ("", HANDLER, request, tests, test1, test2, test3, test4, test5);
    }

    @Test
    public void testEmpty() {
        SolrQueryRequest request = req("q", "applefzefzefzfz");

        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='0']";

        assertQ("", HANDLER, request, tests);
    }

    @Test
    public void testAC1() {
        SolrQueryRequest request = req("q", "apple i");

        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='0']";

        assertQ("", HANDLER, request, tests);
    }

    @Test
    public void testAC2() {
        SolrQueryRequest request = req("q", "apple iph");

        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='0']";


        assertQ("", HANDLER, request, tests);
    }

    @Test
    public void testAC3() {
        SolrQueryRequest request = req("q", "appleip");

        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='5']";

        String test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='searchHighlight' and text()='apple ipad']";
        String test2 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[2]/str[@name='searchHighlight' and text()='apple iphone 12']";
        String test3 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[3]/str[@name='searchHighlight' and text()='apple iphone 11']";
        String test4 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[4]/str[@name='searchHighlight' and text()='apple iphone']";
        String test5 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[5]/str[@name='searchHighlight' and text()='apple iphone 13']";

        assertQ("", HANDLER, request, tests, test1, test2, test3, test4, test5);
    }

    @Test
    public void testConcat() {
        SolrQueryRequest request = req("q", "thermo");
        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='1']";
        String test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='searchHighlight' and text()='[thermo] mix']";
//        String test2 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[2]/str[@name='searchHighlight' and text()='[thermo</strong>mix']";

        assertQ("", HANDLER, request, tests, test1);


        request = req("q", "thermom");
        tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='2']";
        test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='searchHighlight' and text()='thermo mix']";
        String test2 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[2]/str[@name='searchHighlight' and text()='[thermom]ix']";

        assertQ("", HANDLER, request, tests, test1, test2);
    }

    @Test
    public void testFuzzy() {
        SolrQueryRequest request = req("q", "therom");
        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='1']";
        String test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='searchHighlight' and text()='thermo mix']";

        assertQ("", HANDLER, request, tests, test1);

        request = req("q", "apple ipda");
        tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='1']";
        test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='searchHighlight' and text()='[apple] ipad']";

        assertQ("", HANDLER, request, tests, test1);
    }

    @Test
    public void testLengthCache() {
        SolrQueryRequest request = req("q", "th");
        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='0']";
        assertQ("", HANDLER, request, tests  );

    }

    private static void assertQ(String message, String handler, SolrQueryRequest req, String... tests) {
        try {
            if (null == message) {
                String var10000 = "";
            } else {
                (new StringBuilder()).append(message).append(" ").toString();
            }

            ModifiableSolrParams xmlWriterTypeParams = new ModifiableSolrParams(req.getParams());
            xmlWriterTypeParams.set("wt", new String[]{"xml"});
            xmlWriterTypeParams.set("indent", new String[]{xmlWriterTypeParams.get("indent", "off")});
            req.setParams(xmlWriterTypeParams);
            String response = h.query(handler, req);
            if (req.getParams().getBool("facet", false)) {
                String[] allTests = new String[tests.length + 1];
                System.arraycopy(tests, 0, allTests, 1, tests.length);
                allTests[0] = "*[count(//lst[@name='facet_counts']/*[@name='exception'])=0]";
                tests = allTests;
            }

            String results = BaseTestHarness.validateXPath(response, tests);
            if (null != results) {
                String msg = "REQUEST FAILED: xpath=" + results + "\n\txml response was: " + response + "\n\trequest was:" + req.getParamString();
                log.error(msg);
                throw new RuntimeException(msg);
            }
        } catch (XPathExpressionException var8) {
            throw new RuntimeException("XPath is invalid", var8);
        } catch (Exception var9) {
            SolrException.log(log, "REQUEST FAILED: " + req.getParamString(), var9);
            throw new RuntimeException("Exception during query", var9);
        }
    }


}
