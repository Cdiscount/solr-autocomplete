package org.apache.lucene.analysis;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * Insert in payloads current word position
 * 
 * @author egosse
 * 
 */
public class PositionInsertFilter extends TokenFilter {
	
	public static final char DEFAULT_DELIMITER = '�';
	private final CharTermAttribute termAttribute;
	private final PositionIncrementAttribute posAttribute;

	float position = 0f;
	
	public PositionInsertFilter(TokenStream input) {
		super(input);
		termAttribute = addAttribute(CharTermAttribute.class);
		posAttribute =  addAttribute(PositionIncrementAttribute.class);
	}

	@Override
	public final boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			position += posAttribute.getPositionIncrement();

			String text = termAttribute.toString();
			text = text.replace(""+DEFAULT_DELIMITER, "");
			termAttribute.setEmpty();
			termAttribute.append(text).append(String.valueOf(DEFAULT_DELIMITER)).append(String.valueOf(position));

			return true;
		} 
		return false;
	}

	@Override
	public void reset() throws IOException {
		position = 0f;
		this.input.reset();
	}

}
