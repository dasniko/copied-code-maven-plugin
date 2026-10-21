package dasniko.copiedcode.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of checking one annotated local element.
 *
 * @param file         local source file
 * @param line         line of the annotation
 * @param localElement description of the annotated local element
 * @param upstream     description of the upstream element, including the compared versions where known
 * @param status       result
 * @param message      explanation for anything but {@link Status#UNCHANGED}
 * @param diff         unified diff of the upstream element between the versions, empty unless {@link Status#CHANGED}
 */
record CheckResult(Path file, int line, String localElement, String upstream, Status status, String message, List<String> diff) {

	enum Status {
		/** Upstream element is the same at the baseline and the current version. */
		UNCHANGED,
		/** Upstream element changed; review the local copy, then bump the annotation's version. */
		CHANGED,
		/** Upstream element no longer exists at the current version (removed, renamed or moved). */
		REMOVED,
		/** Baseline or current upstream sources, or the version to compare against, cannot be determined. */
		UNRESOLVABLE,
		/** Malformed annotation or ambiguous reference. */
		INVALID
	}

	CheckResult {
		diff = List.copyOf(diff);
	}

	String location() {
		return file + ":" + line;
	}
}
