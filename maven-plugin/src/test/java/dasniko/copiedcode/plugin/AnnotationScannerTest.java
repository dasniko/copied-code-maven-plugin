package dasniko.copiedcode.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AnnotationScannerTest {

	private static final Path FILE = Path.of("Local.java");

	private static List<AnnotatedElement> scan(String source) {
		return new AnnotationScanner().scan(FILE, Parsers.create().parse(source).getResult().orElseThrow());
	}

	@TempDir
	Path tmp;

	@Test
	void readsSourcesInTheGivenEncoding() throws IOException {
		Files.writeString(tmp.resolve("A.java"), """
			import dasniko.copiedcode.CopiedFromKeycloak;
			// Grüße
			class A {
				@CopiedFromKeycloak(source = "org.keycloak.Foo", version = "26.0.0") void a() {}
			}
			""", StandardCharsets.ISO_8859_1);
		assertThat(new AnnotationScanner().scan(List.of(tmp), StandardCharsets.ISO_8859_1).elements()).hasSize(1);
		// undecodable bytes are replaced, the annotations are still found
		assertThat(new AnnotationScanner().scan(List.of(tmp), StandardCharsets.UTF_8).elements()).hasSize(1);
	}

	@Test
	void reportsUnparseableFilesMentioningTheAnnotations() throws IOException {
		Path broken = Files.writeString(tmp.resolve("Broken.java"), "import dasniko.copiedcode.CopiedFrom; class Broken {");
		Files.writeString(tmp.resolve("Other.java"), "class Other {");
		var result = new AnnotationScanner().scan(List.of(tmp), StandardCharsets.UTF_8);
		assertThat(result.elements()).isEmpty();
		assertThat(result.parseProblems()).containsOnlyKeys(broken);
	}

	@Test
	void findsAllTargetsWithDefaultMembers() {
		var elements = scan("""
			package de.example;

			import dasniko.copiedcode.CopiedFrom;
			import dasniko.copiedcode.CopiedFromKeycloak;
			import java.util.List;

			@CopiedFromKeycloak(source = "org.keycloak.Foo", version = "26.0.0")
			class Local {
				@CopiedFrom(artifact = "g:a", source = "a.Up", version = "1.0")
				static final String FIELD = "x";

				@CopiedFrom(artifact = "g:a", source = "a.Up", version = "1.0")
				Local(List<String> values, int... rest) {
				}

				@CopiedFromKeycloak(source = "org.keycloak.Foo#other", version = "26.0.0", modified = true, reason = "because")
				void method(java.util.Map<String, Integer> map) {
				}
			}
			""");

		assertThat(elements)
			.extracting(AnnotatedElement::kind, AnnotatedElement::localElement, AnnotatedElement::defaultMember, AnnotatedElement::line)
			.containsExactly(
				tuple(AnnotatedElement.Kind.KEYCLOAK, "de.example.Local", null, 7),
				tuple(AnnotatedElement.Kind.GENERIC, "de.example.Local#FIELD", new MemberRef("FIELD", null), 9),
				tuple(AnnotatedElement.Kind.GENERIC, "de.example.Local#Local(List, int...)",
					new MemberRef(MemberRef.CONSTRUCTOR, List.of("List", "int...")), 12),
				tuple(AnnotatedElement.Kind.KEYCLOAK, "de.example.Local#method(Map)", new MemberRef("method", List.of("Map")), 16));
		assertThat(elements.getLast().attributes()).isEqualTo(Map.of("source", "org.keycloak.Foo#other", "version", "26.0.0"));
		assertThat(elements).allSatisfy(e -> assertThat(e.errors()).isEmpty());
	}

	@Test
	void findsEnumConstantsRecordMembersAndAnnotationElements() {
		var elements = scan("""
			package de.example;

			import dasniko.copiedcode.CopiedFrom;

			class Local {
				enum Mode {
					@CopiedFrom(artifact = "g:a", source = "a.Up.Mode", version = "1.0") FAST
				}

				record Point(@CopiedFrom(artifact = "g:a", source = "a.Up.Point", version = "1.0") int x, int y) {
					@CopiedFrom(artifact = "g:a", source = "a.Up.Point", version = "1.0")
					Point {
					}
				}

				@interface Marker {
					@CopiedFrom(artifact = "g:a", source = "a.Up.Marker", version = "1.0")
					String value();
				}
			}
			""");

		assertThat(elements)
			.extracting(AnnotatedElement::localElement, AnnotatedElement::defaultMember)
			.containsExactly(
				tuple("de.example.Local.Mode#FAST", new MemberRef("FAST", null)),
				tuple("de.example.Local.Point#x", new MemberRef("x", null)),
				tuple("de.example.Local.Point#Point(int, int)", new MemberRef(MemberRef.CONSTRUCTOR, List.of("int", "int"))),
				tuple("de.example.Local.Marker#value()", new MemberRef("value", List.of())));
		assertThat(elements).allSatisfy(e -> assertThat(e.errors()).isEmpty());
	}

	@Test
	void matchesQualifiedNameAndWildcardImport() {
		assertThat(scan("""
			import dasniko.copiedcode.*;
			class A {
				@CopiedFrom(artifact = "g:a", source = "a.Up", version = "1.0") void a() {}
				@dasniko.copiedcode.CopiedFromKeycloak(source = "a.Up", version = "1.0") void b() {}
			}
			""")).hasSize(2);
	}

	@Test
	void ignoresUnrelatedAnnotationsWithTheSameName() {
		assertThat(scan("""
			import other.CopiedFrom;
			class A {
				@CopiedFrom(source = "x") void a() {}
			}
			""")).isEmpty();
	}

	@Test
	void concatenatesStringLiterals() {
		var element = scan("""
			import dasniko.copiedcode.CopiedFromKeycloak;
			class A {
				@CopiedFromKeycloak(source = "org.keycloak." + "Foo", version = "26.0.0") void a() {}
			}
			""").getFirst();
		assertThat(element.attributes()).containsEntry("source", "org.keycloak.Foo");
	}

	@Test
	void reportsNonLiteralAttributes() {
		var element = scan("""
			import dasniko.copiedcode.CopiedFromKeycloak;
			class A {
				@CopiedFromKeycloak(source = "org.keycloak.Foo", version = Versions.KEYCLOAK) void a() {}
			}
			""").getFirst();
		assertThat(element.errors()).containsExactly("version must be a string literal, but is 'Versions.KEYCLOAK'");
	}

	@Test
	void multiVariableFieldNeedsExplicitMember() {
		var elements = scan("""
			import dasniko.copiedcode.CopiedFromKeycloak;
			class A {
				@CopiedFromKeycloak(source = "org.keycloak.Foo", version = "26.0.0") int a, b;
				@CopiedFromKeycloak(source = "org.keycloak.Foo#a", version = "26.0.0") int c, d;
			}
			""");
		assertThat(elements.get(0).errors()).singleElement().asString().contains("several variables");
		assertThat(elements.get(1).errors()).isEmpty();
	}
}
