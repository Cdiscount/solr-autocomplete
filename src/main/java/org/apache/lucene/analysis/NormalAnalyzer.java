package org.apache.lucene.analysis;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

/**
 *
 * 
 */
public class NormalAnalyzer extends Analyzer {

	public NormalAnalyzer() {
		// Nothing to do
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer source = new LetterOrDigitTokenizer();
		TokenStream filter = new LowerCaseFilter(source);
		filter = new ASCIIFoldingFilter(filter);
		return new TokenStreamComponents(source, filter);
	}
}
