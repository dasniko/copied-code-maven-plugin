package dasniko.copiedcode.plugin;

/**
 * How the build reacts to a check result.
 */
public enum Action {
	/** Log an error and fail the build. */
	FAIL,
	/** Log a warning. */
	WARN,
	/** Log at debug level only. */
	IGNORE
}
