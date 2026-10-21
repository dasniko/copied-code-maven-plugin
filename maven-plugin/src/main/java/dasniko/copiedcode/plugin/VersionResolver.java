package dasniko.copiedcode.plugin;

import java.util.List;
import java.util.Optional;

/**
 * Determines the upstream version the project currently builds against. First match wins:
 * <ol>
 *   <li>a configured {@code <upstream>} for the artifact</li>
 *   <li>a configured {@code <upstream>} for the whole groupId</li>
 *   <li>the artifact's version among the project's resolved dependencies</li>
 *   <li>the artifact's version in the project's dependency management</li>
 * </ol>
 */
class VersionResolver {

	private final List<Upstream> upstreams;
	private final List<Gav> dependencies;
	private final List<Gav> managedDependencies;

	VersionResolver(List<Upstream> upstreams, List<Gav> dependencies, List<Gav> managedDependencies) {
		this.upstreams = List.copyOf(upstreams);
		this.dependencies = List.copyOf(dependencies);
		this.managedDependencies = List.copyOf(managedDependencies);
	}

	Optional<String> resolve(String groupId, String artifactId) {
		return upstreams.stream()
			.filter(u -> u.getGroupId().equals(groupId) && !u.isGroupWide() && u.getArtifactId().equals(artifactId))
			.map(Upstream::getVersion)
			.findFirst()
			.or(() -> upstreams.stream()
				.filter(u -> u.getGroupId().equals(groupId) && u.isGroupWide())
				.map(Upstream::getVersion)
				.findFirst())
			.or(() -> find(dependencies, groupId, artifactId))
			.or(() -> find(managedDependencies, groupId, artifactId));
	}

	private static Optional<String> find(List<Gav> gavs, String groupId, String artifactId) {
		return gavs.stream()
			.filter(g -> g.groupId().equals(groupId) && g.artifactId().equals(artifactId))
			.map(Gav::version)
			.findFirst();
	}
}
