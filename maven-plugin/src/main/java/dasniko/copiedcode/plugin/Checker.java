package dasniko.copiedcode.plugin;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import dasniko.copiedcode.plugin.CheckResult.Status;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compares the upstream element of each reference at its baseline version with the same element at the current version.
 * The local copy is never compared.
 */
class Checker {

	/**
	 * Resolves a sources jar, downloading it if needed; empty if it does not exist.
	 */
	@FunctionalInterface
	interface SourcesResolver {
		Optional<Path> resolve(Gav gav);
	}

	private final SourcesResolver sourcesResolver;
	private final VersionResolver versionResolver;
	private final List<Dependency> dependencies;
	private final UpstreamSources upstreamSources = new UpstreamSources();
	private final CodeNormalizer normalizer;
	private final Map<Gav, Optional<Path>> jars = new HashMap<>();

	/**
	 * @param dependencies the project's resolved dependencies, searched when a reference does not name its artifactId
	 */
	Checker(SourcesResolver sourcesResolver, VersionResolver versionResolver, List<Dependency> dependencies, boolean ignoreComments) {
		this.sourcesResolver = sourcesResolver;
		this.versionResolver = versionResolver;
		this.dependencies = List.copyOf(dependencies);
		this.normalizer = new CodeNormalizer(ignoreComments);
	}

	CheckResult check(AnnotatedElement element) {
		CopiedCodeReference ref;
		try {
			ref = CopiedCodeReference.from(element);
		} catch (InvalidReferenceException e) {
			return new CheckResult(element.file(), element.line(), element.localElement(), "?", Status.INVALID, e.getMessage(), List.of());
		}
		return check(ref);
	}

	private CheckResult check(CopiedCodeReference ref) {
		String artifactId = ref.artifactId();
		if (artifactId == null) {
			Optional<String> detected = detectArtifactId(ref);
			if (detected.isEmpty()) {
				return result(ref, ref.upstreamArtifact() + " " + ref.source(), Status.UNRESOLVABLE, "no " + ref.groupId()
					+ " dependency with sources contains " + ref.source().typeName() + ", set the annotation's artifact", List.of());
			}
			artifactId = detected.get();
		}
		String upstreamArtifact = ref.groupId() + ":" + artifactId;

		Optional<String> currentVersion = versionResolver.resolve(ref.groupId(), artifactId);
		if (currentVersion.isEmpty()) {
			return result(ref, upstreamArtifact + " " + ref.source(), Status.UNRESOLVABLE, "cannot determine the current version of "
				+ upstreamArtifact + ", it is no dependency of the project; configure it in <upstreams>", List.of());
		}
		Gav baseline = new Gav(ref.groupId(), artifactId, ref.version());
		Gav current = new Gav(ref.groupId(), artifactId, currentVersion.get());
		String upstream = upstreamArtifact + " " + ref.source() + " " + baseline.version() + " -> " + current.version();
		try {
			return compare(ref, upstream, baseline, current);
		} catch (InvalidReferenceException e) {
			return result(ref, upstream, Status.INVALID, e.getMessage(), List.of());
		} catch (UpstreamSources.UpstreamException e) {
			return result(ref, upstream, Status.UNRESOLVABLE, e.getMessage(), List.of());
		}
	}

	private CheckResult compare(CopiedCodeReference ref, String upstream, Gav baseline, Gav current) {
		Optional<Path> baselineJar = sources(baseline);
		if (baselineJar.isEmpty()) {
			return result(ref, upstream, Status.UNRESOLVABLE, "sources of " + baseline + " not found", List.of());
		}
		Optional<Node> baselineNode = upstreamSources.find(baselineJar.get(), ref.source());
		if (baselineNode.isEmpty()) {
			return result(ref, upstream, Status.UNRESOLVABLE, ref.source() + " not found in sources of " + baseline, List.of());
		}
		if (baseline.equals(current)) {
			return result(ref, upstream, Status.UNCHANGED, "", List.of());
		}

		Optional<Path> currentJar = sources(current);
		if (currentJar.isEmpty()) {
			return result(ref, upstream, Status.UNRESOLVABLE, "sources of " + current + " not found", List.of());
		}
		Optional<Node> currentNode;
		try {
			currentNode = upstreamSources.find(currentJar.get(), ref.source());
		} catch (InvalidReferenceException e) {
			// unambiguous at the baseline, so it is upstream drift (a new overload), not a broken annotation
			return newOverload(ref, upstream, baseline, baselineNode.get(), current, currentJar.get(), e);
		}
		if (currentNode.isEmpty()) {
			return result(ref, upstream, Status.REMOVED, ref.source() + " not found in sources of " + current, List.of());
		}

		List<String> diff = diff(baseline, baselineNode.get(), current, currentNode.get());
		if (diff.isEmpty()) {
			return result(ref, upstream, Status.UNCHANGED, "", List.of());
		}
		return result(ref, upstream, Status.CHANGED, "upstream changed, review the local copy and set version = \""
			+ current.version() + "\"", diff);
	}

	/**
	 * The reference is ambiguous at the current version only. Typically a method referenced without parameter list became
	 * overloaded; then the diff is taken against the overload with the baseline's signature, which the source should name from now on.
	 */
	private CheckResult newOverload(CopiedCodeReference ref, String upstream, Gav baseline, Node baselineNode, Gav current,
									Path currentJar, InvalidReferenceException e) {
		if (!(baselineNode instanceof MethodDeclaration method)) {
			return result(ref, upstream, Status.CHANGED, e.getMessage() + " at " + current.version()
				+ "; review the local copy, then fix source and set version = \"" + current.version() + "\"", List.of());
		}
		SourceRef pinned = ref.source().withMember(new MemberRef(method.getNameAsString(), TypeNames.of(method.getParameters())));
		List<String> diff = upstreamSources.find(currentJar, pinned)
			.map(node -> diff(baseline, method, current, node))
			.orElse(List.of());
		return result(ref, upstream, Status.CHANGED, "'" + method.getNameAsString() + "' is overloaded at " + current.version()
			+ ", review the local copy and set source = \"" + pinned + "\" and version = \"" + current.version() + "\"", diff);
	}

	/**
	 * @return the unified diff of the normalized elements, empty if they are equal
	 */
	private List<String> diff(Gav baseline, Node baselineNode, Gav current, Node currentNode) {
		List<String> before = normalizer.normalize(baselineNode);
		List<String> after = normalizer.normalize(currentNode);
		if (before.equals(after)) {
			return List.of();
		}
		return UnifiedDiffUtils.generateUnifiedDiff(baseline.toString(), current.toString(), before, DiffUtils.diff(before, after), 3);
	}

	/**
	 * Looks for the type's class file in the already resolved dependency jars, so that only the matching sources jar has to be
	 * downloaded. Sources are only searched for dependencies without a resolved file.
	 */
	private Optional<String> detectArtifactId(CopiedCodeReference ref) {
		String typeName = ref.source().typeName();
		return dependencies.stream()
			.filter(d -> d.gav().groupId().equals(ref.groupId()))
			.sorted(Comparator.comparing(d -> d.gav().artifactId()))
			.filter(d -> d.file() != null
				? UpstreamSources.containsClass(d.file(), typeName)
				: sources(d.gav()).filter(jar -> UpstreamSources.containsType(jar, typeName)).isPresent())
			.map(d -> d.gav().artifactId())
			.findFirst();
	}

	private Optional<Path> sources(Gav gav) {
		return jars.computeIfAbsent(gav, sourcesResolver::resolve);
	}

	private static CheckResult result(CopiedCodeReference ref, String upstream, Status status, String message, List<String> diff) {
		return new CheckResult(ref.file(), ref.line(), ref.localElement(), upstream, status, message, diff);
	}
}
