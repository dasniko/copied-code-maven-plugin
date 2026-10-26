package dasniko.copiedcode.plugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A validated {@code @CopiedFrom} / {@code @CopiedFromKeycloak} usage.
 *
 * @param file          local source file
 * @param line          line of the annotation
 * @param localElement  description of the annotated local element
 * @param groupId       upstream groupId
 * @param artifactId    upstream artifactId, {@code null} to auto-detect it among the project's dependencies of {@code groupId}
 * @param source        upstream element; for a member annotation without explicit member, the member derived from the local element
 * @param version       baseline upstream version
 * @param derivedMember whether the member of {@code source} was derived from the local element, as the annotation names a type only
 */
record CopiedCodeReference(Path file, int line, String localElement, String groupId, String artifactId, SourceRef source,
						   String version, boolean derivedMember) {

	static final String KEYCLOAK_GROUP_ID = "org.keycloak";

	private static final Pattern MAVEN_ID = Pattern.compile("[A-Za-z0-9_.-]+");

	/**
	 * @throws InvalidReferenceException if the annotation's attributes are missing or malformed
	 */
	static CopiedCodeReference from(AnnotatedElement element) {
		if (!element.errors().isEmpty()) {
			throw new InvalidReferenceException(String.join("; ", element.errors()));
		}
		Map<String, String> attributes = element.attributes();

		String groupId;
		String artifactId = attributes.getOrDefault("artifact", "").strip();
		if (element.kind() == AnnotatedElement.Kind.KEYCLOAK) {
			groupId = KEYCLOAK_GROUP_ID;
			if (artifactId.isEmpty()) {
				artifactId = null;
			} else if (!MAVEN_ID.matcher(artifactId).matches()) {
				throw new InvalidReferenceException("artifact must be a plain artifactId, but is '" + artifactId + "'");
			}
		} else {
			String[] ga = artifactId.split(":", -1);
			if (ga.length != 2 || !MAVEN_ID.matcher(ga[0]).matches() || !MAVEN_ID.matcher(ga[1]).matches()) {
				throw new InvalidReferenceException("artifact must be 'groupId:artifactId', but is '" + artifactId + "'");
			}
			groupId = ga[0];
			artifactId = ga[1];
		}

		String version = attributes.getOrDefault("version", "").strip();
		if (version.isEmpty()) {
			throw new InvalidReferenceException("version is missing");
		}

		String rawSource = attributes.getOrDefault("source", "");
		if (rawSource.isBlank()) {
			throw new InvalidReferenceException("source is missing");
		}
		SourceRef source;
		try {
			source = SourceRef.parse(rawSource);
		} catch (IllegalArgumentException e) {
			throw new InvalidReferenceException("invalid source '" + rawSource + "': " + e.getMessage());
		}
		boolean derivedMember = source.member() == null && element.defaultMember() != null;
		if (derivedMember) {
			source = source.withMember(element.defaultMember());
		}

		return new CopiedCodeReference(element.file(), element.line(), element.localElement(), groupId, artifactId, source, version,
			derivedMember);
	}
}
