package dasniko.copiedcode.plugin;

import java.util.regex.Pattern;

/**
 * Parsed {@code source} attribute: {@code a.b.Type}, {@code a.b.Type#member} or {@code a.b.Type#member(Param, ...)}.
 *
 * @param typeName fully qualified type name, nested types separated by {@code .}
 * @param member   referenced member, {@code null} if the source names a type only
 */
record SourceRef(String typeName, MemberRef member) {

	private static final Pattern QUALIFIED_NAME = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
		+ "(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");
	private static final Pattern IDENTIFIER = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

	static SourceRef parse(String source) {
		String s = source.strip();
		int hash = s.indexOf('#');
		String type = hash < 0 ? s : s.substring(0, hash).strip();
		if (!QUALIFIED_NAME.matcher(type).matches()) {
			throw new IllegalArgumentException("'" + type + "' is not a valid type name");
		}
		if (hash < 0) {
			return new SourceRef(type, null);
		}

		String member = s.substring(hash + 1).strip();
		int paren = member.indexOf('(');
		String name = paren < 0 ? member : member.substring(0, paren).strip();
		if (!IDENTIFIER.matcher(name).matches()) {
			throw new IllegalArgumentException("'" + name + "' is not a valid member name");
		}
		if (paren < 0) {
			return new SourceRef(type, new MemberRef(name, null));
		}
		if (!member.endsWith(")")) {
			throw new IllegalArgumentException("missing ')' in '" + member + "'");
		}
		var parameters = TypeNames.splitParameters(member.substring(paren + 1, member.length() - 1));
		String simpleName = type.substring(type.lastIndexOf('.') + 1);
		return new SourceRef(type, new MemberRef(name.equals(simpleName) ? MemberRef.CONSTRUCTOR : name, parameters));
	}

	String simpleName() {
		return typeName.substring(typeName.lastIndexOf('.') + 1);
	}

	SourceRef withMember(MemberRef member) {
		return new SourceRef(typeName, member);
	}

	@Override
	public String toString() {
		return member == null ? typeName : typeName + "#" + member.describe(simpleName());
	}
}
