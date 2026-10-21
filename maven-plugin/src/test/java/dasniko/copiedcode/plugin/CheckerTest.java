package dasniko.copiedcode.plugin;

import dasniko.copiedcode.plugin.CheckResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CheckerTest {

	private static final String V1 = """
		package up;

		public class Foo {
			public static final int LIMIT = 10;

			/** Adds. */
			public int add(int a, int b) {
				return a + b;
			}

			public int add(int a, int b, int c) {
				return a + b + c;
			}

			public String name() {
				return "foo";
			}

			public void gone() {
			}

			public static class Inner {
				public Inner(String s) {
				}
			}
		}
		""";

	private static final String V2 = """
		package up;

		public class Foo {
			public static final int LIMIT = 20;

			/**
			 * Adds two numbers.
			 */
			public int add(int a,
						   int b) {
				// reformatted, comment added
				return a + b;
			}

			public int add(int a, int b, int c) {
				return Math.addExact(a, Math.addExact(b, c));
			}

			public String name() {
				return "foo";
			}

			public static class Inner {
				public Inner(String s) {
				}
			}
		}
		""";

	@TempDir
	Path tmp;

	private final Map<Gav, Path> jars = new HashMap<>();
	private final List<Upstream> upstreams = new java.util.ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		jars.put(new Gav("g", "a", "1"), jar("a-1", Map.of("up/Foo.java", V1)));
		jars.put(new Gav("g", "a", "2"), jar("a-2", Map.of("up/Foo.java", V2)));
		jars.put(new Gav("org.keycloak", "kc-other", "2"), jar("other-2", Map.of("kc/Other.java", "package kc; class Other {}")));
		jars.put(new Gav("org.keycloak", "kc-core", "1"), jar("kc-1", Map.of("kc/Core.java", "package kc; class Core { int x; }")));
		jars.put(new Gav("org.keycloak", "kc-core", "2"), jar("kc-2", Map.of("kc/Core.java", "package kc; class Core { long x; }")));
	}

	private Path jar(String name, Map<String, String> entries) throws IOException {
		Path jar = tmp.resolve(name + "-sources.jar");
		try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (var entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return jar;
	}

	private CheckResult check(String source, MemberRef defaultMember) {
		return check(AnnotatedElement.Kind.GENERIC, "g:a", source, "1", defaultMember);
	}

	private CheckResult check(AnnotatedElement.Kind kind, String artifact, String source, String version, MemberRef defaultMember) {
		Map<String, String> attributes = new HashMap<>(Map.of("source", source, "version", version));
		if (artifact != null) {
			attributes.put("artifact", artifact);
		}
		var element = new AnnotatedElement(kind, Path.of("Local.java"), 1, "Local", defaultMember, attributes, List.of());
		var dependencies = List.of(new Gav("g", "a", "2"), new Gav("org.keycloak", "kc-other", "2"),
			new Gav("org.keycloak", "kc-core", "2"));
		var checker = new Checker(gav -> Optional.ofNullable(jars.get(gav)), new VersionResolver(upstreams, dependencies, List.of()),
			dependencies, true);
		return checker.check(element);
	}

	@Test
	void ignoresFormattingAndComments() {
		assertThat(check("up.Foo#add(int, int)", null).status()).isEqualTo(Status.UNCHANGED);
	}

	@Test
	void detectsChangedMethodWithDiff() {
		var result = check("up.Foo#add(int, int, int)", null);
		assertThat(result.status()).isEqualTo(Status.CHANGED);
		assertThat(result.upstream()).isEqualTo("g:a up.Foo#add(int, int, int) 1 -> 2");
		assertThat(result.diff()).contains("--- g:a:1", "+++ g:a:2", "-    return a + b + c;",
			"+    return Math.addExact(a, Math.addExact(b, c));");
	}

	@Test
	void usesDefaultMemberOfAnnotatedMethod() {
		assertThat(check("up.Foo", new MemberRef("add", List.of("int", "int", "int"))).status()).isEqualTo(Status.CHANGED);
		assertThat(check("up.Foo", new MemberRef("name", List.of())).status()).isEqualTo(Status.UNCHANGED);
	}

	@Test
	void comparesFields() {
		assertThat(check("up.Foo#LIMIT", null).status()).isEqualTo(Status.CHANGED);
	}

	@Test
	void comparesWholeTypes() {
		assertThat(check("up.Foo", null).status()).isEqualTo(Status.CHANGED);
		assertThat(check("up.Foo.Inner", null).status()).isEqualTo(Status.UNCHANGED);
	}

	@Test
	void comparesNestedConstructors() {
		assertThat(check("up.Foo.Inner#Inner(String)", null).status()).isEqualTo(Status.UNCHANGED);
		assertThat(check("up.Foo.Inner", new MemberRef(MemberRef.CONSTRUCTOR, List.of("String"))).status()).isEqualTo(Status.UNCHANGED);
	}

	@Test
	void detectsRemovedMember() {
		var result = check("up.Foo#gone()", null);
		assertThat(result.status()).isEqualTo(Status.REMOVED);
		assertThat(result.message()).isEqualTo("up.Foo#gone() not found in sources of g:a:2");
	}

	@Test
	void unknownBaselineElementIsUnresolvable() {
		assertThat(check("up.Foo#typo()", null).status()).isEqualTo(Status.UNRESOLVABLE);
		assertThat(check("up.Typo", null).status()).isEqualTo(Status.UNRESOLVABLE);
	}

	@Test
	void unknownBaselineVersionIsUnresolvable() {
		var result = check(AnnotatedElement.Kind.GENERIC, "g:a", "up.Foo", "0.9", null);
		assertThat(result.status()).isEqualTo(Status.UNRESOLVABLE);
		assertThat(result.message()).isEqualTo("sources of g:a:0.9 not found");
	}

	@Test
	void sameVersionStillValidatesTheReference() {
		upstreams.add(upstream("g", "a", "1"));
		assertThat(check("up.Foo#add(int, int, int)", null).status()).isEqualTo(Status.UNCHANGED);
		assertThat(check("up.Foo#typo()", null).status()).isEqualTo(Status.UNRESOLVABLE);
	}

	@Test
	void ambiguousMemberIsInvalid() {
		var result = check("up.Foo#add", null);
		assertThat(result.status()).isEqualTo(Status.INVALID);
		assertThat(result.message()).contains("overloaded", "add(int, int)");
	}

	@Test
	void malformedAnnotationIsInvalid() {
		assertThat(check(AnnotatedElement.Kind.GENERIC, "g", "up.Foo", "1", null).status()).isEqualTo(Status.INVALID);
		assertThat(check(AnnotatedElement.Kind.KEYCLOAK, "org.keycloak:kc-core", "kc.Core", "1", null).status())
			.isEqualTo(Status.INVALID);
		assertThat(check(AnnotatedElement.Kind.GENERIC, "g:a", "up.Foo", " ", null).status()).isEqualTo(Status.INVALID);
	}

	@Test
	void keycloakArtifactIsDetected() {
		var result = check(AnnotatedElement.Kind.KEYCLOAK, null, "kc.Core#x", "1", null);
		assertThat(result.status()).isEqualTo(Status.CHANGED);
		assertThat(result.upstream()).startsWith("org.keycloak:kc-core ");
	}

	@Test
	void undetectableKeycloakArtifactIsUnresolvable() {
		var result = check(AnnotatedElement.Kind.KEYCLOAK, null, "kc.Missing", "1", null);
		assertThat(result.status()).isEqualTo(Status.UNRESOLVABLE);
		assertThat(result.message()).contains("set the annotation's artifact");
	}

	@Test
	void configuredUpstreamWinsOverDependencyVersion() {
		upstreams.add(upstream("org.keycloak", null, "1"));
		assertThat(check(AnnotatedElement.Kind.KEYCLOAK, "kc-core", "kc.Core#x", "1", null).status()).isEqualTo(Status.UNCHANGED);
	}

	@Test
	void unknownCurrentVersionIsUnresolvable() {
		var result = check(AnnotatedElement.Kind.GENERIC, "g:unknown", "up.Foo", "1", null);
		assertThat(result.status()).isEqualTo(Status.UNRESOLVABLE);
		assertThat(result.message()).contains("<upstreams>");
	}

	private static Upstream upstream(String groupId, String artifactId, String version) {
		var upstream = new Upstream();
		upstream.setGroupId(groupId);
		upstream.setArtifactId(artifactId);
		upstream.setVersion(version);
		return upstream;
	}
}
