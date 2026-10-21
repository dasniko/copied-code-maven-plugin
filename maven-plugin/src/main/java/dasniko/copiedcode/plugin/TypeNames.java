package dasniko.copiedcode.plugin;

import com.github.javaparser.ast.body.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes type names in signatures, so that a parameter type is compared the same way whether it was written in an
 * annotation's {@code source} or taken from parsed code: simple name, no generics, no annotations, arrays and varargs kept.
 * E.g. {@code java.util.Map.Entry<String, List<Integer>>[]} becomes {@code Entry[]}.
 */
final class TypeNames {

	private TypeNames() {
	}

	static String normalize(String type) {
		String t = stripGenerics(type).replaceAll("@[\\w.]+(\\([^)]*\\))?", "").replaceAll("\\bfinal\\b", "").replaceAll("\\s+", "");
		String suffix = "";
		if (t.endsWith("...")) {
			suffix = "...";
			t = t.substring(0, t.length() - 3);
		}
		while (t.endsWith("[]")) {
			suffix = "[]" + suffix;
			t = t.substring(0, t.length() - 2);
		}
		return t.substring(t.lastIndexOf('.') + 1) + suffix;
	}

	static String of(Parameter parameter) {
		return normalize(parameter.getType().asString() + (parameter.isVarArgs() ? "..." : ""));
	}

	static List<String> of(List<Parameter> parameters) {
		return parameters.stream().map(TypeNames::of).toList();
	}

	/**
	 * Splits a comma separated parameter list, ignoring commas inside generics.
	 */
	static List<String> splitParameters(String parameters) {
		List<String> result = new ArrayList<>();
		String stripped = stripGenerics(parameters);
		if (stripped.isBlank()) {
			return result;
		}
		for (String part : stripped.split(",", -1)) {
			if (part.isBlank()) {
				throw new IllegalArgumentException("empty parameter type in '" + parameters + "'");
			}
			result.add(normalize(part));
		}
		return result;
	}

	private static String stripGenerics(String type) {
		StringBuilder sb = new StringBuilder();
		int depth = 0;
		for (char c : type.toCharArray()) {
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				if (depth == 0) {
					throw new IllegalArgumentException("unbalanced '>' in '" + type + "'");
				}
				depth--;
			} else if (depth == 0) {
				sb.append(c);
			}
		}
		if (depth != 0) {
			throw new IllegalArgumentException("unbalanced '<' in '" + type + "'");
		}
		return sb.toString();
	}
}
