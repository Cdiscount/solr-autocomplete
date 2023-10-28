package org.apache.solr.suggest.analysis;

import org.apache.solr.util.BaseTestHarness;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class XPathTest {


    @Test
    public void test() throws XPathExpressionException, SAXException {
        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response>" +
                "<lst name=\"responseHeader\"><int name=\"status\">0</int><int name=\"QTime\">253</int><lst name=\"params\"><str name=\"q\">apple</str><str name=\"wt\">xml</str></lst></lst><lst name=\"suggest\"><lst name=\"default\"><lst name=\"response\"><int name=\"numFound\">5</int><arr name=\"hits\"><lst><str name=\"search\">apple ipad</str><long name=\"score\">515</long></lst><lst><str name=\"search\">apple iphone 12</str><long name=\"score\">509</long></lst><lst><str name=\"search\">apple iphone 11</str><long name=\"score\">505</long></lst><lst><str name=\"search\">apple iphone</str><long name=\"score\">503</long></lst><lst><str name=\"search\">apple iphone 13</str><long name=\"score\">503</long></lst></arr></lst></lst></lst>" +
                "</response>";
        String tests = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/int[@name='numFound' and text()='5']";

        String test1 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[1]/str[@name='search' and text()='apple ipad']";
        String test2 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[2]/str[@name='search' and text()='apple iphone 12']";
        String test3 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[3]/str[@name='search' and text()='apple iphone 11']";
        String test4 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[4]/str[@name='search' and text()='apple iphone']";
        String test5 = "//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']//*[5]/str[@name='search' and text()='apple iphone 13']";

        assertNull(BaseTestHarness.validateXPath(response, tests, test1, test2, test3, test4, test5));
    }

    @Test
    public void test2() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response>" +
                "<lst name=\"responseHeader\"><int name=\"status\">0</int><int name=\"QTime\">253</int><lst name=\"params\"><str name=\"q\">apple</str><str name=\"wt\">xml</str></lst></lst><lst name=\"suggest\"><lst name=\"default\"><lst name=\"response\"><int name=\"numFound\">5</int><arr name=\"hits\"><lst><str name=\"search\">apple ipad</str><long name=\"score\">515</long></lst><lst><str name=\"search\"> apple  iphone 12 </str><long name=\"score\">509</long></lst><lst><str name=\"search\"> apple  iphone 11</str><long name=\"score\">505</long></lst><lst><str name=\"search\"> apple  iphone</str><long name=\"score\">503</long></lst><lst><str name=\"search\"> apple  iphone 13</str><long name=\"score\">503</long></lst></arr></lst></lst></lst>" +
                "</response>";

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));// same xml comments as above.

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        Element userElement = (Element) xpath.evaluate("//response/lst[@name='suggest']/lst[@name='default']/lst[@name='response']/arr[@name='hits']/*[1]/str[@name='search' and text()='apple ipad']", document,
                XPathConstants.NODE);
        assertNotNull(userElement);
    }
}
