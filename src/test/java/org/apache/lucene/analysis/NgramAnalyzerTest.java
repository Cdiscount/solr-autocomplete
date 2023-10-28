package org.apache.lucene.analysis;

import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class NgramAnalyzerTest {

    @Test
    public void test1() {
        Analyzer analyzer = new NgramPositionAnalyzer();
        WordsParser.Data data = WordsParser.parse("Ceci est un test.. !", analyzer);

        assertEquals("c|ce|cec|ceci|e|ec|eci|c|ci|i|e|es|est|s|st|t|u|un|n|t|te|tes|test|e|es|est|s|st|t", String.join("|", data.words));
        assertEquals("1|0|0|0|0|0|0|0|0|0|1|0|0|0|0|0|1|0|0|1|0|0|0|0|0|0|0|0|0",
                data.positions.stream().map(x -> "" + x).collect(Collectors.joining("|")));

        assertEquals("1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|2.0|2.0|2.0|2.0|2.0|2.0|3.0|3.0|3.0|4.0|4.0|4.0|4.0|4.0|4.0|4.0|4.0|4.0|4.0",
                data.payloads.stream().map(x -> "" + x).collect(Collectors.joining("|")));
    }

    @Test
    public void test2() {
        Analyzer analyzer = new NgramPositionAnalyzer();
        WordsParser.Data data = WordsParser.parse("thermomix", analyzer);

        assertEquals("t|th|the|ther|therm|thermo|thermom|thermomi|thermomix|h|he|her|herm|hermo|hermom|hermomi|hermomix|e|er|erm|ermo|ermom|ermomi|ermomix|r|rm|rmo|rmom|rmomi|rmomix|m|mo|mom|momi|momix|o|om|omi|omix|m|mi|mix|i|ix|x", String.join("|", data.words));
        assertEquals("1|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0",
                data.positions.stream().map(x -> "" + x).collect(Collectors.joining("|")));

        assertEquals("1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0|1.0",
                data.payloads.stream().map(x -> "" + x).collect(Collectors.joining("|")));
    }
}
