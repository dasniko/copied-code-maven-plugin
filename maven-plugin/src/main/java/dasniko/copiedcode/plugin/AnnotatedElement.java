package dasniko.copiedcode.plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A raw annotation usage as found by the {@link AnnotationScanner}, before validation.
 *
 * @param kind          which annotation
 * @param file          local source file
 * @param line          line of the annotation
 * @param localElement  description of the annotated local element
 * @param defaultMember upstream member to compare if {@code source} names a type only; {@code null} for an annotated type
 * @param attributes    the string attributes relevant for the check ({@code artifact}, {@code source}, {@code version})
 * @param errors        problems found while reading the attributes
 */
record AnnotatedElement(Kind kind, Path file, int line, String localElement, MemberRef defaultMember, Map<String, String> attributes,
						List<String> errors) {

	enum Kind {
		GENERIC("dasniko.copiedcode.CopiedFrom"),
		KEYCLOAK("dasniko.copiedcode.CopiedFromKeycloak");

		final String qualifiedName;

		Kind(String qualifiedName) {
			this.qualifiedName = qualifiedName;
		}

		String simpleName() {
			return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
		}

		String packageName() {
			return qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
		}
	}
}
