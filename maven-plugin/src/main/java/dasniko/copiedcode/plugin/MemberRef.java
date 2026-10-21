package dasniko.copiedcode.plugin;

import java.util.List;

/**
 * A member of a type.
 *
 * @param name           member name, {@link #CONSTRUCTOR} for a constructor
 * @param parameterTypes normalized parameter types (see {@link TypeNames}) of a method or constructor; {@code null} if no parameter
 *                       list was given, which means a field or a method without overloads
 */
record MemberRef(String name, List<String> parameterTypes) {

	static final String CONSTRUCTOR = "<init>";

	MemberRef {
		parameterTypes = parameterTypes == null ? null : List.copyOf(parameterTypes);
	}

	boolean isConstructor() {
		return CONSTRUCTOR.equals(name);
	}

	boolean hasParameterList() {
		return parameterTypes != null;
	}

	String describe(String typeSimpleName) {
		String n = isConstructor() ? typeSimpleName : name;
		return hasParameterList() ? n + "(" + String.join(", ", parameterTypes) + ")" : n;
	}
}
