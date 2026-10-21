package dasniko.copiedcode.plugin;

/**
 * Plugin configuration: the version to compare against for an upstream artifact, or for all artifacts of a groupId.
 */
public class Upstream {

	private String groupId;
	private String artifactId;
	private String version;

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	boolean isGroupWide() {
		return artifactId == null || artifactId.isBlank();
	}

	@Override
	public String toString() {
		return groupId + ":" + (isGroupWide() ? "*" : artifactId) + ":" + version;
	}
}
