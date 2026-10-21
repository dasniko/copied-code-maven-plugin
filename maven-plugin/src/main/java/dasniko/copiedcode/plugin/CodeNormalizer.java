package dasniko.copiedcode.plugin;

import com.github.javaparser.ast.Node;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;

import java.util.List;

/**
 * Prints an AST node in a canonical form, so that formatting-only (and optionally comment-only) changes do not count as changes.
 */
class CodeNormalizer {

	private final DefaultPrettyPrinter printer;

	CodeNormalizer(boolean ignoreComments) {
		var configuration = new DefaultPrinterConfiguration()
			.addOption(new DefaultConfigurationOption(ConfigOption.END_OF_LINE_CHARACTER, "\n"));
		if (ignoreComments) {
			configuration.removeOption(new DefaultConfigurationOption(ConfigOption.PRINT_COMMENTS));
			configuration.removeOption(new DefaultConfigurationOption(ConfigOption.PRINT_JAVADOC));
		}
		this.printer = new DefaultPrettyPrinter(configuration);
	}

	List<String> normalize(Node node) {
		return printer.print(node).lines().toList();
	}
}
