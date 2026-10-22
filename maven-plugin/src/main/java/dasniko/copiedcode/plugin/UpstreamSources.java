package dasniko.copiedcode.plugin;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Looks up upstream elements in sources jars. Parsed compilation units are cached, as several local elements are often copied
 * from the same upstream type.
 */
class UpstreamSources {

	private final JavaParser parser = Parsers.create();
	private final Map<String, Optional<Located>> cache = new HashMap<>();

	/**
	 * A parsed upstream file and the path of nested type names inside it.
	 */
	private record Located(CompilationUnit cu, List<String> typePath) {
	}

	/**
	 * Whether the jar contains the source file of the type.
	 */
	static boolean containsType(Path sourcesJar, String typeName) {
		return containsAny(sourcesJar, candidates(typeName).stream().map(Candidate::entry).toList());
	}

	/**
	 * Whether the binary jar or classes directory contains the class file of the type.
	 */
	static boolean containsClass(Path binary, String typeName) {
		return containsAny(binary, candidates(typeName).stream().map(Candidate::classEntry).toList());
	}

	private static boolean containsAny(Path jarOrDirectory, List<String> entries) {
		if (Files.isDirectory(jarOrDirectory)) {
			return entries.stream().anyMatch(e -> Files.isRegularFile(jarOrDirectory.resolve(e)));
		}
		try (ZipFile zip = new ZipFile(jarOrDirectory.toFile())) {
			return entries.stream().anyMatch(e -> zip.getEntry(e) != null);
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * @return the referenced element, empty if the jar does not contain it
	 * @throws InvalidReferenceException if the member reference is ambiguous
	 * @throws UpstreamException         if the jar cannot be read or the source file cannot be parsed
	 */
	Optional<Node> find(Path sourcesJar, SourceRef source) {
		Optional<TypeDeclaration<?>> type = cache.computeIfAbsent(sourcesJar + "!" + source.typeName(), k -> locate(sourcesJar, source))
			.flatMap(UpstreamSources::resolveType);
		if (type.isEmpty() || source.member() == null) {
			return type.map(Node.class::cast);
		}
		return findMember(type.get(), source.member());
	}

	private Optional<Located> locate(Path sourcesJar, SourceRef source) {
		try (ZipFile zip = new ZipFile(sourcesJar.toFile())) {
			for (Candidate candidate : candidates(source.typeName())) {
				ZipEntry entry = zip.getEntry(candidate.entry());
				if (entry == null) {
					continue;
				}
				String content;
				try (InputStream in = zip.getInputStream(entry)) {
					content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
				var parsed = parser.parse(content);
				if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
					throw new UpstreamException("cannot parse " + candidate.entry() + " in " + sourcesJar.getFileName() + ": "
						+ parsed.getProblems().stream().map(Object::toString).collect(Collectors.joining("; ")));
				}
				return Optional.of(new Located(parsed.getResult().get(), candidate.typePath()));
			}
			return Optional.empty();
		} catch (IOException e) {
			throw new UpstreamException("cannot read " + sourcesJar.getFileName() + ": " + e.getMessage(), e);
		}
	}

	private static Optional<TypeDeclaration<?>> resolveType(Located located) {
		List<String> path = located.typePath();
		Optional<TypeDeclaration<?>> current = located.cu().getTypes().stream()
			.filter(t -> t.getNameAsString().equals(path.getFirst()))
			.<TypeDeclaration<?>>map(t -> t)
			.findFirst();
		for (String nested : path.subList(1, path.size())) {
			current = current.flatMap(t -> t.getMembers().stream()
				.filter(BodyDeclaration::isTypeDeclaration)
				.<TypeDeclaration<?>>map(BodyDeclaration::asTypeDeclaration)
				.filter(n -> n.getNameAsString().equals(nested))
				.findFirst());
		}
		return current;
	}

	private static Optional<Node> findMember(TypeDeclaration<?> type, MemberRef member) {
		if (member.hasParameterList()) {
			List<Node> matching = callables(type, member)
				.filter(c -> c.parameterTypes().equals(member.parameterTypes()))
				.map(Callable::node)
				.toList();
			if (matching.size() > 1) {
				throw new InvalidReferenceException(matching.size() + " overloads of '" + member.describe(type.getNameAsString())
					+ "' in " + type.getNameAsString() + " differ only in packages or generics of their parameter types, which source"
					+ " cannot tell apart; reference the whole type instead");
			}
			return matching.stream().findFirst();
		}

		Optional<Node> field = type.getFields().stream()
			.filter(f -> f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(member.name())))
			.<Node>map(f -> onlyVariable(f, member.name()))
			.findFirst()
			.or(() -> enumConstant(type, member.name()))
			.or(() -> recordComponent(type, member.name()));
		if (field.isPresent()) {
			return field;
		}
		List<Callable> methods = callables(type, member).toList();
		if (methods.size() > 1) {
			throw new InvalidReferenceException("'" + member.name() + "' is overloaded in " + type.getNameAsString()
				+ ", add the parameter types to source, e.g. " + member.name() + "("
				+ String.join(", ", methods.getFirst().parameterTypes()) + ")");
		}
		return methods.stream().map(Callable::node).findFirst();
	}

	/**
	 * A method, constructor or annotation element with its normalized parameter types.
	 */
	private record Callable(Node node, List<String> parameterTypes) {
	}

	private static Stream<Callable> callables(TypeDeclaration<?> type, MemberRef member) {
		if (member.isConstructor()) {
			Stream<Callable> constructors = type.getConstructors().stream().map(c -> new Callable(c, TypeNames.of(c.getParameters())));
			if (type instanceof RecordDeclaration record) {
				// the compact canonical constructor takes the record components
				List<String> components = TypeNames.of(record.getParameters());
				constructors = Stream.concat(constructors, record.getCompactConstructors().stream().map(c -> new Callable(c, components)));
			}
			return constructors;
		}
		Stream<Callable> elements = type.getMembers().stream()
			.filter(BodyDeclaration::isAnnotationMemberDeclaration)
			.map(BodyDeclaration::asAnnotationMemberDeclaration)
			.filter(a -> a.getNameAsString().equals(member.name()))
			.map(a -> new Callable(a, List.of()));
		return Stream.concat(type.getMethodsByName(member.name()).stream().map(m -> new Callable(m, TypeNames.of(m.getParameters()))),
			elements);
	}

	/**
	 * Of a declaration like {@code int a = 1, b = 2;} only the named variable counts, together with the shared modifiers and type.
	 */
	private static FieldDeclaration onlyVariable(FieldDeclaration field, String name) {
		if (field.getVariables().size() == 1) {
			return field;
		}
		FieldDeclaration copy = field.clone();
		List.copyOf(copy.getVariables()).stream().filter(v -> !v.getNameAsString().equals(name)).forEach(Node::remove);
		return copy;
	}

	private static Optional<Node> enumConstant(TypeDeclaration<?> type, String name) {
		if (!(type instanceof EnumDeclaration enumDeclaration)) {
			return Optional.empty();
		}
		return enumDeclaration.getEntries().stream().filter(e -> e.getNameAsString().equals(name)).<Node>map(e -> e).findFirst();
	}

	private static Optional<Node> recordComponent(TypeDeclaration<?> type, String name) {
		if (!(type instanceof RecordDeclaration record)) {
			return Optional.empty();
		}
		return record.getParameters().stream().filter(p -> p.getNameAsString().equals(name)).<Node>map(p -> p).findFirst();
	}

	private record Candidate(String entry, List<String> typePath) {

		/**
		 * Class file of the (possibly nested) type, e.g. {@code a/b/Outer$Inner.class}.
		 */
		String classEntry() {
			return entry.substring(0, entry.length() - ".java".length())
				+ typePath.subList(1, typePath.size()).stream().map(n -> "$" + n).collect(Collectors.joining()) + ".class";
		}
	}

	/**
	 * {@code a.b.Outer.Inner} may be the top-level type {@code Inner} in package {@code a.b.Outer} or the nested type
	 * {@code Outer.Inner} in package {@code a.b}; lists the possible source files, top-level type first.
	 */
	private static List<Candidate> candidates(String typeName) {
		String[] segments = typeName.split("\\.");
		return java.util.stream.IntStream.iterate(segments.length - 1, i -> i >= 0, i -> i - 1)
			.mapToObj(i -> new Candidate(
				String.join("/", Arrays.copyOfRange(segments, 0, i + 1)) + ".java",
				Arrays.asList(segments).subList(i, segments.length)))
			.toList();
	}

	static class UpstreamException extends RuntimeException {
		UpstreamException(String message) {
			super(message);
		}

		UpstreamException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
