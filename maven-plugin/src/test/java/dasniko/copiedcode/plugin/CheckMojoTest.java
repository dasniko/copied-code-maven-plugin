package dasniko.copiedcode.plugin;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CheckMojoTest {

	@Test
	void diffFileNamesAreUniqueAlsoOnCaseInsensitiveFileSystems() {
		Set<String> taken = new HashSet<>();
		assertThat(CheckMojo.diffFileName("a.B#c(Map<String, Integer>)", taken)).isEqualTo("a.B#c(Map_String,_Integer_).diff");
		assertThat(CheckMojo.diffFileName("a.B#c(Map<String, Integer>)", taken)).isEqualTo("a.B#c(Map_String,_Integer_)-2.diff");
		assertThat(CheckMojo.diffFileName("a.B#C(Map<String, Integer>)", taken)).isEqualTo("a.B#C(Map_String,_Integer_)-3.diff");
	}

	@Test
	void longDiffFileNamesAreShortened() {
		String longName = "a.B#c(" + "VeryLongParameterTypeName, ".repeat(20) + "int)";
		String name = CheckMojo.diffFileName(longName, new HashSet<>());
		assertThat(name).hasSizeLessThan(255).startsWith("a.B#c(VeryLongParameterTypeName,").endsWith(".diff");
		assertThat(CheckMojo.diffFileName(longName.replace("int)", "long)"), new HashSet<>())).isNotEqualTo(name);
	}
}
