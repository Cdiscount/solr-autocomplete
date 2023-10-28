package org.apache.lucene.analysis;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;

/**
 *
 * 
 */
public class PositionAnalyzer extends Analyzer {

	public PositionAnalyzer() {
		// Nothing to do
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer source = new LetterOrDigitTokenizer();
		TokenStream filter = new LowerCaseFilter(source);
		filter = new ASCIIFoldingFilter(filter);
	 	filter = new DeduplicateFilter(filter);
		filter = new PositionInsertFilter(filter);
		filter = new CDelimitedPayloadTokenFilter(filter, PositionInsertFilter.DEFAULT_DELIMITER, new FloatEncoder());

		return new TokenStreamComponents(source, filter);
	}
}
