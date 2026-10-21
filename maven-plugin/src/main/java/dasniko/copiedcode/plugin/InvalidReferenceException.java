package dasniko.copiedcode.plugin;

/**
 * An annotation or the upstream element it references cannot be interpreted, e.g. a malformed {@code source} or an ambiguous
 * member name.
 */
class InvalidReferenceException extends RuntimeException {

	InvalidReferenceException(String message) {
		super(message);
	}
}
