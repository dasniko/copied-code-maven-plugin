package dasniko.copiedcode.plugin;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds {@code @CopiedFrom} and {@code @CopiedFromKeycloak} usages in local Java sources.
 * The annotations have source retention, so they are read from the parsed sources, matched by their qualified name.
 */
class AnnotationScanner {

	private static final Set<String> RELEVANT_ATTRIBUTES = Set.of("artifact", "source", "version");

	private final JavaParser parser = Parsers.create();

	record Result(List<AnnotatedElement> elements, Map<Path, String> parseProblems) {
	}

	/**
	 * @param encoding encoding of the source files; undecodable bytes are replaced, as the local code itself is never compared
	 */
	Result scan(List<Path> sourceRoots, Charset encoding) throws IOException {
		List<AnnotatedElement> elements = new ArrayList<>();
		Map<Path, String> problems = new HashMap<>();
		for (Path root : sourceRoots) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			List<Path> files;
			try (Stream<Path> walk = Files.walk(root)) {
				files = walk.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p)).sorted().toList();
			}
			for (Path file : files) {
				String content = new String(Files.readAllBytes(file), encoding);
				if (!content.contains(AnnotatedElement.Kind.GENERIC.simpleName())) {
					continue; // cheap pre-filter, also matches CopiedFromKeycloak
				}
				var parsed = parser.parse(content);
				if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
					problems.put(file, parsed.getProblems().stream().map(Object::toString).collect(Collectors.joining("; ")));
					continue;
				}
				elements.addAll(scan(file, parsed.getResult().get()));
			}
		}
		return new Result(elements, problems);
	}

	List<AnnotatedElement> scan(Path file, CompilationUnit cu) {
		List<AnnotatedElement> elements = new ArrayList<>();
		for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
			Optional<AnnotatedElement.Kind> kind = kindOf(annotation, cu);
			if (kind.isEmpty()) {
				continue;
			}
			Optional<Node> parent = annotation.getParentNode();
			if (parent.isEmpty()) {
				continue;
			}
			elements.add(toElement(kind.get(), file, annotation, parent.get()));
		}
		return elements;
	}

	private static Optional<AnnotatedElement.Kind> kindOf(AnnotationExpr annotation, CompilationUnit cu) {
		String name = annotation.getNameAsString();
		for (AnnotatedElement.Kind kind : AnnotatedElement.Kind.values()) {
			if (name.equals(kind.qualifiedName)) {
				return Optional.of(kind);
			}
			if (name.equals(kind.simpleName()) && isVisible(kind, cu)) {
				return Optional.of(kind);
			}
		}
		return Optional.empty();
	}

	private static boolean isVisible(AnnotatedElement.Kind kind, CompilationUnit cu) {
		if (cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).filter(kind.packageName()::equals).isPresent()) {
			return true;
		}
		for (ImportDeclaration imp : cu.getImports()) {
			if (imp.isStatic()) {
				continue;
			}
			String imported = imp.getNameAsString();
			if (imp.isAsterisk() ? imported.equals(kind.packageName()) : imported.equals(kind.qualifiedName)) {
				return true;
			}
		}
		return false;
	}

	private static AnnotatedElement toElement(AnnotatedElement.Kind kind, Path file, AnnotationExpr annotation, Node annotated) {
		int line = annotation.getBegin().map(p -> p.line).orElse(0);
		List<String> errors = new ArrayList<>();
		Map<String, String> attributes = new HashMap<>();
		if (annotation instanceof NormalAnnotationExpr normal) {
			normal.getPairs().stream()
				.filter(pair -> RELEVANT_ATTRIBUTES.contains(pair.getNameAsString()))
				.forEach(pair -> stringValue(pair.getValue()).ifPresentOrElse(
					value -> attributes.put(pair.getNameAsString(), value),
					() -> errors.add(pair.getNameAsString() + " must be a string literal, but is '" + pair.getValue() + "'")));
		} else {
			errors.add("source and version are missing");
		}

		String localElement;
		MemberRef defaultMember = null;
		switch (annotated) {
			case MethodDeclaration m -> {
				defaultMember = new MemberRef(m.getNameAsString(), TypeNames.of(m.getParameters()));
				localElement = typeName(m) + "#" + defaultMember.describe(null);
			}
			case ConstructorDeclaration c -> {
				defaultMember = new MemberRef(MemberRef.CONSTRUCTOR, TypeNames.of(c.getParameters()));
				localElement = typeName(c) + "#" + defaultMember.describe(c.getNameAsString());
			}
			case FieldDeclaration f -> {
				List<String> names = f.getVariables().stream().map(VariableDeclarator::getNameAsString).toList();
				if (names.size() == 1) {
					defaultMember = new MemberRef(names.getFirst(), null);
				} else if (!attributes.getOrDefault("source", "").contains("#")) {
					errors.add("source must name the member, the field declaration declares several variables " + names);
				}
				localElement = typeName(f) + "#" + String.join(",", names);
			}
			case TypeDeclaration<?> t -> localElement = qualifiedName(t);
			default -> {
				localElement = annotated.getClass().getSimpleName();
				errors.add("unsupported annotation target");
			}
		}
		return new AnnotatedElement(kind, file, line, localElement, defaultMember, Map.copyOf(attributes), List.copyOf(errors));
	}

	private static Optional<String> stringValue(Expression expression) {
		return switch (expression) {
			case StringLiteralExpr s -> Optional.of(s.asString());
			case TextBlockLiteralExpr t -> Optional.of(t.asString());
			case BinaryExpr b when b.getOperator() == BinaryExpr.Operator.PLUS ->
				stringValue(b.getLeft()).flatMap(left -> stringValue(b.getRight()).map(right -> left + right));
			default -> Optional.empty();
		};
	}

	private static String typeName(Node member) {
		return member.findAncestor(TypeDeclaration.class).map(AnnotationScanner::qualifiedName).orElse("?");
	}

	private static String qualifiedName(TypeDeclaration<?> type) {
		return type.getFullyQualifiedName().orElseGet(type::getNameAsString);
	}
}
