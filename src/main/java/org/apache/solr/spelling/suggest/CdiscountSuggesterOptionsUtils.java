package org.apache.solr.spelling.suggest;

public class CdiscountSuggesterOptionsUtils {

    public static final String SEPARATOR = "_";

    private CdiscountSuggesterOptionsUtils() {
        // Nothing to do
    }

    public static String keyCache(String suggesterName, SuggesterOptions options) {
        return suggesterName
                + SEPARATOR + options.token
                + SEPARATOR + options.contextFilterQuery
                + SEPARATOR + options.count
                + SEPARATOR + options.allTermsRequired
                + SEPARATOR + options.highlight;
    }
}
