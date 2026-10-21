package dasniko.copiedcode.plugin;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;

final class Parsers {

	private Parsers() {
	}

	/**
	 * Parser for the newest Java language level JavaParser supports, for local and upstream sources alike.
	 */
	static JavaParser create() {
		return new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
	}
}
