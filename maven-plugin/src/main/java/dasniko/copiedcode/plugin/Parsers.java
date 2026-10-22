package dasniko.copiedcode.plugin;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import java.util.stream.Collectors;

final class Parsers {

	private Parsers() {
	}

	/**
	 * Parser for the newest Java language level JavaParser supports, for local and upstream sources alike.
	 */
	static JavaParser create() {
		return new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT));
	}

	/**
	 * @throws UnparseableException with the parser's problems if the code cannot be parsed
	 */
	static CompilationUnit parse(JavaParser parser, String code) {
		var parsed = parser.parse(code);
		if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
			throw new UnparseableException(parsed.getProblems().stream().map(Object::toString).collect(Collectors.joining("; ")));
		}
		return parsed.getResult().get();
	}

	static class UnparseableException extends RuntimeException {
		UnparseableException(String problems) {
			super(problems);
		}
	}
}
