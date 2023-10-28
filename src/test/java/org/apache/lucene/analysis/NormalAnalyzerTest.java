package org.apache.lucene.analysis;

import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class NormalAnalyzerTest {

    @Test
    public void test1() {
        Analyzer analyzer = new NormalAnalyzer();
        WordsParser.Data data = WordsParser.parse("Ceci est un test.. !", analyzer);

        assertEquals("ceci|est|un|test", String.join("|", data.words));
        assertEquals("1|1|1|1",
                data.positions.stream().map(x -> "" + x).collect(Collectors.joining("|")));

        assertEquals("",
                data.payloads.stream().map(x -> "" + x).collect(Collectors.joining("|")));
    }

    @Test
    public void test2() {
        Analyzer analyzer = new PositionAnalyzer();
        WordsParser.Data data = WordsParser.parse("Ceci est un test.. !", analyzer);

        assertEquals("ceci|est|un|test", String.join("|", data.words));
        assertEquals("1|1|1|1",
                data.positions.stream().map(x -> "" + x).collect(Collectors.joining("|")));

        assertEquals("1.0|2.0|3.0|4.0",
                data.payloads.stream().map(x -> "" + x).collect(Collectors.joining("|")));
    }
}