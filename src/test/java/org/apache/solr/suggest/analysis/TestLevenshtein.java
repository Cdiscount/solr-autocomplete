package org.apache.solr.suggest.analysis;

import org.apache.lucene.query.DamerauLevenshteinAlgorithm;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestLevenshtein {


    @Test
    public void test() {
        DamerauLevenshteinAlgorithm algo = new DamerauLevenshteinAlgorithm(1,1,1,2);
        assertEquals(1,algo.execute("marrio", "mario"));
        assertEquals(1,algo.execute("marrio", "matrio"));
    }
}
