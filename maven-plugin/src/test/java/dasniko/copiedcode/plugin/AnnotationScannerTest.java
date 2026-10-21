package dasniko.copiedcode.plugin;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AnnotationScannerTest {

	private static final Path FILE = Path.of("Local.java");

	private static List<AnnotatedElement> scan(String source) {
		return new AnnotationScanner().scan(FILE, StaticJavaParser.parse(source));
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
