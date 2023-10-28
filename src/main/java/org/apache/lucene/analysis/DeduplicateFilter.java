package org.apache.lucene.analysis;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class DeduplicateFilter extends TokenFilter {

	private final CharTermAttribute termAttribute;
	private final Set<String> set;

	public DeduplicateFilter(TokenStream in) {
		super(in);
		termAttribute = (CharTermAttribute) addAttribute(CharTermAttribute.class);
		set = new HashSet<>();
	}

	public final boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			String text = termAttribute.toString();
			if (! set.contains(text)) {
				set.add(text);
				return true;
			}
			termAttribute.setEmpty();
			return true;
		}
		return false;
	}

	@Override
	 public void reset() throws IOException {
		 set.clear();
		 input.reset();
	 }
}