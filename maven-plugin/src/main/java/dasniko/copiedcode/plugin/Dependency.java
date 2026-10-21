package dasniko.copiedcode.plugin;

import java.nio.file.Path;

/**
 * A resolved project dependency.
 *
 * @param gav  coordinates
 * @param file the resolved jar or classes directory, {@code null} if not available
 */
record Dependency(Gav gav, Path file) {
}
