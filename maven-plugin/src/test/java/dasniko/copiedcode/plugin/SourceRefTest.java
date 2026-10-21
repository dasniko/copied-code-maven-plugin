package dasniko.copiedcode.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SourceRefTest {

	@Test
	void typeOnly() {
		assertThat(SourceRef.parse("org.keycloak.Foo")).isEqualTo(new SourceRef("org.keycloak.Foo", null));
	}

	@Test
	void method() {
		assertThat(SourceRef.parse(" org.keycloak.Foo # bar( java.util.Map<String, List<Integer>> , int[], String... ) "))
			.isEqualTo(new SourceRef("org.keycloak.Foo", new MemberRef("bar", List.of("Map", "int[]", "String..."))));
	}

	@Test
	void methodWithoutParameters() {
		assertThat(SourceRef.parse("org.keycloak.Foo#bar()").member()).isEqualTo(new MemberRef("bar", List.of()));
	}

	@Test
	void constructor() {
		assertThat(SourceRef.parse("org.keycloak.Foo.Inner#Inner(String)").member())
			.isEqualTo(new MemberRef(MemberRef.CONSTRUCTOR, List.of("String")));
	}

	@Test
	void fieldOrUnambiguousMethod() {
		assertThat(SourceRef.parse("org.keycloak.Foo#BAR").member()).isEqualTo(new MemberRef("BAR", null));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"org.keycloak.Foo",
		"org.keycloak.Foo#bar(Map, int[], String...)",
		"org.keycloak.Foo#Foo(String)",
		"org.keycloak.Foo#BAR",
	})
	void toStringRoundTrips(String source) {
		assertThat(SourceRef.parse(source)).hasToString(source);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "org..Foo", "org.Foo#", "org.Foo#bar(", "org.Foo#bar(String,)", "org.Foo#bar(List<String)", "1org.Foo"})
	void rejectsMalformed(String source) {
		assertThatIllegalArgumentException().isThrownBy(() -> SourceRef.parse(source));
	}
}
