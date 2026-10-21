package dasniko.copiedcode.plugin;

import dasniko.copiedcode.plugin.CheckResult.Status;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Checks code annotated with {@code @CopiedFrom} / {@code @CopiedFromKeycloak} for upstream changes: compares the referenced upstream
 * element at the annotation's version with the same element at the version the project currently builds against.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class CheckMojo extends AbstractMojo {

	/**
	 * Versions to compare against, per artifact or per groupId. Artifacts not configured here are compared against their version
	 * among the project's dependencies or dependency management.
	 */
	@Parameter
	private List<Upstream> upstreams = new ArrayList<>();

	/**
	 * Reaction to an upstream element that changed.
	 */
	@Parameter(property = "copied-code.onChanged", defaultValue = "FAIL")
	private Action onChanged;

	/**
	 * Reaction to an upstream element that no longer exists at the current version.
	 */
	@Parameter(property = "copied-code.onRemoved", defaultValue = "FAIL")
	private Action onRemoved;

	/**
	 * Reaction to upstream sources, elements or versions that cannot be resolved.
	 */
	@Parameter(property = "copied-code.onUnresolvable", defaultValue = "FAIL")
	private Action onUnresolvable;

	/**
	 * Whether upstream changes in comments and Javadoc are ignored.
	 */
	@Parameter(property = "copied-code.ignoreComments", defaultValue = "true")
	private boolean ignoreComments;

	/**
	 * Whether test sources are scanned, too.
	 */
	@Parameter(property = "copied-code.includeTestSources", defaultValue = "true")
	private boolean includeTestSources;

	/**
	 * Directory the diffs of changed upstream elements are written to, one file per annotated element.
	 */
	@Parameter(property = "copied-code.diffOutputDirectory", defaultValue = "${project.build.directory}/copied-code")
	private File diffOutputDirectory;

	@Parameter(property = "copied-code.skip", defaultValue = "false")
	private boolean skip;

	private final MavenProject project;
	private final MavenSession session;
	private final RepositorySystem repositorySystem;

	@Inject
	public CheckMojo(MavenProject project, MavenSession session, RepositorySystem repositorySystem) {
		this.project = project;
		this.session = session;
		this.repositorySystem = repositorySystem;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			getLog().info("Skipping copied code check");
			return;
		}
		validateUpstreams();

		List<Path> sourceRoots = new ArrayList<>();
		project.getCompileSourceRoots().stream().map(Path::of).forEach(sourceRoots::add);
		if (includeTestSources) {
			project.getTestCompileSourceRoots().stream().map(Path::of).forEach(sourceRoots::add);
		}

		AnnotationScanner.Result scan;
		try {
			scan = new AnnotationScanner().scan(sourceRoots);
		} catch (IOException e) {
			throw new MojoExecutionException("Cannot read sources", e);
		}
		scan.parseProblems().forEach((file, problem) -> getLog().warn("Cannot parse " + file + ", skipped: " + problem));
		if (scan.elements().isEmpty()) {
			getLog().info("No copied code found");
			return;
		}

		List<Gav> dependencies = project.getArtifacts().stream()
			.map(a -> new Gav(a.getGroupId(), a.getArtifactId(), a.getBaseVersion()))
			.toList();
		List<Gav> managed = Optional.ofNullable(project.getDependencyManagement())
			.map(DependencyManagement::getDependencies)
			.orElse(List.of())
			.stream()
			.map(d -> new Gav(d.getGroupId(), d.getArtifactId(), d.getVersion()))
			.toList();
		Checker checker = new Checker(this::resolveSources, new VersionResolver(upstreams, dependencies, managed), dependencies,
			ignoreComments);

		prepareDiffOutputDirectory();
		Map<Status, Integer> counts = new EnumMap<>(Status.class);
		int failures = 0;
		for (AnnotatedElement element : scan.elements()) {
			CheckResult result = checker.check(element);
			counts.merge(result.status(), 1, Integer::sum);
			if (report(result) == Action.FAIL) {
				failures++;
			}
		}

		getLog().info("Copied code: " + counts);
		if (failures > 0) {
			throw new MojoFailureException(failures + " copied code element(s) need attention, see the log above"
				+ (counts.containsKey(Status.CHANGED) ? " and the diffs in " + diffOutputDirectory : ""));
		}
	}

	private void validateUpstreams() throws MojoExecutionException {
		for (Upstream upstream : upstreams) {
			if (upstream.getGroupId() == null || upstream.getGroupId().isBlank()
				|| upstream.getVersion() == null || upstream.getVersion().isBlank()) {
				throw new MojoExecutionException("<upstream> needs <groupId> and <version>: " + upstream);
			}
		}
	}

	private Action report(CheckResult result) throws MojoExecutionException {
		Action action = switch (result.status()) {
			case UNCHANGED -> null;
			case CHANGED -> onChanged;
			case REMOVED -> onRemoved;
			case UNRESOLVABLE -> onUnresolvable;
			case INVALID -> Action.FAIL;
		};
		if (result.status() == Status.CHANGED) {
			writeDiff(result);
		}

		String headline = result.status() + " " + result.localElement() + " (" + result.location() + ")";
		if (action == null) {
			getLog().info(headline);
			return null;
		}
		StringBuilder text = new StringBuilder(headline)
			.append(System.lineSeparator()).append("    upstream ").append(result.upstream())
			.append(System.lineSeparator()).append("    ").append(result.message());
		result.diff().forEach(line -> text.append(System.lineSeparator()).append("    ").append(line));
		switch (action) {
			case FAIL -> getLog().error(text);
			case WARN -> getLog().warn(text);
			case IGNORE -> getLog().debug(text);
		}
		return action;
	}

	private void prepareDiffOutputDirectory() throws MojoExecutionException {
		Path dir = diffOutputDirectory.toPath();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".diff")).toList()) {
				Files.delete(file);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Cannot clean " + dir, e);
		}
	}

	private void writeDiff(CheckResult result) throws MojoExecutionException {
		Path file = diffOutputDirectory.toPath().resolve(result.localElement().replaceAll("[^\\w.#(),-]", "_") + ".diff");
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, result.diff());
		} catch (IOException e) {
			throw new MojoExecutionException("Cannot write " + file, e);
		}
	}

	private Optional<Path> resolveSources(Gav gav) {
		var artifact = new DefaultArtifact(gav.groupId(), gav.artifactId(), "sources", "jar", gav.version());
		var request = new ArtifactRequest(artifact, project.getRemoteProjectRepositories(), null);
		try {
			File file = repositorySystem.resolveArtifact(session.getRepositorySession(), request).getArtifact().getFile();
			return Optional.ofNullable(file).map(File::toPath);
		} catch (ArtifactResolutionException e) {
			getLog().debug("Cannot resolve " + artifact + ": " + e.getMessage());
			return Optional.empty();
		}
	}
}
