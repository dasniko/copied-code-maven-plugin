package dasniko.copiedcode.plugin;

record Gav(String groupId, String artifactId, String version) {

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ":" + version;
	}
}
