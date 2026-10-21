# copied-code-maven-plugin

Sometimes code has to be copied from a library instead of being reused: the class is internal, the method is private, or it
needs a small modification. Such a copy silently drifts away from upstream when the library is updated.

This plugin makes the drift visible. Mark copied code with `@CopiedFrom` (any Maven artifact) or `@CopiedFromKeycloak`, and
the build reports when the **upstream** element changed between the version you copied (or last reviewed) and the version you
currently build against &mdash; including a diff of what changed upstream.

Your local copy is never compared. A modified copy is checked exactly like an unmodified one.

## How it works

```
upstream @ annotation's version      (baseline, e.g. 26.2.0)   ──┐
                                                                 ├─ compare → changed? → fail/warn + diff
upstream @ current version           (e.g. 26.4.1)             ──┘
```

1. The plugin scans the module's main and test sources for the annotations (they have `SOURCE` retention, so they are read
   from the parsed sources).
2. It resolves the upstream `-sources.jar` for both versions through Maven, downloading them if they are not in the local
   repository yet. Mirrors and repository settings apply.
3. It extracts the referenced type, method, constructor or field from both, and compares them in a normalized form: formatting
   changes are ignored, and so are comment and Javadoc changes by default.
4. If they differ, you get the unified diff. Review your copy, carry over what matters, then bump the annotation's `version`.

## Usage

```xml
<dependency>
	<groupId>com.github.dasniko</groupId>
	<artifactId>copied-code-annotations</artifactId>
	<version>${copied-code.version}</version>
	<scope>provided</scope>
</dependency>
```

```java
@CopiedFromKeycloak(
	source = "org.keycloak.authentication.authenticators.browser.UsernamePasswordForm",
	version = "26.2.0",
	modified = true,
	changes = "skips the login hint",
	reason = "not extensible")
@Override
public void authenticate(AuthenticationFlowContext context) {
	…
}

@CopiedFrom(artifact = "org.apache.commons:commons-lang3", source = "org.apache.commons.lang3.StringUtils#isBlank(CharSequence)",
	version = "3.17.0")
static boolean isBlank(CharSequence cs) {
	…
}
```

### `source`

| Form | Compared upstream element |
|---|---|
| `a.b.Foo` on a type | the whole type |
| `a.b.Foo` on a method, constructor or field | the member with the same signature (or field name) as the annotated one |
| `a.b.Foo#bar(String, int)` | that method overload; parameter types as simple names, without generics |
| `a.b.Foo#Foo(String)` | that constructor |
| `a.b.Foo#bar` | the field `bar`, or the method `bar` if it is not overloaded |

Nested types are written as `a.b.Foo.Inner`.

### `artifact`

- `@CopiedFrom`: required, `groupId:artifactId`.
- `@CopiedFromKeycloak`: optional, the artifactId only (groupId is `org.keycloak`). If empty, the plugin searches the project's
  `org.keycloak` dependencies for the type.

## Plugin configuration

```xml
<plugin>
	<groupId>com.github.dasniko</groupId>
	<artifactId>copied-code-maven-plugin</artifactId>
	<version>${copied-code.version}</version>
	<executions>
		<execution>
			<goals>
				<goal>check</goal>
			</goals>
		</execution>
	</executions>
	<configuration>
		<upstreams>
			<!-- all org.keycloak artifacts share one version -->
			<upstream>
				<groupId>org.keycloak</groupId>
				<version>${keycloak.version}</version>
			</upstream>
		</upstreams>
	</configuration>
</plugin>
```

The goal `check` binds to `verify` by default.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `upstreams` | | | Current versions per `groupId` (+ optional `artifactId`) |
| `onChanged` | `copied-code.onChanged` | `FAIL` | `FAIL`, `WARN` or `IGNORE` |
| `onRemoved` | `copied-code.onRemoved` | `FAIL` | `FAIL`, `WARN` or `IGNORE` |
| `onUnresolvable` | `copied-code.onUnresolvable` | `FAIL` | `FAIL`, `WARN` or `IGNORE` |
| `ignoreComments` | `copied-code.ignoreComments` | `true` | Ignore comment and Javadoc changes upstream |
| `includeTestSources` | `copied-code.includeTestSources` | `true` | Scan test sources, too |
| `diffOutputDirectory` | `copied-code.diffOutputDirectory` | `${project.build.directory}/copied-code` | One `.diff` file per changed element |
| `skip` | `copied-code.skip` | `false` | Skip the check |

The current version of an upstream artifact is determined in this order; the first match wins:

1. an `<upstream>` with matching `groupId` and `artifactId`
2. an `<upstream>` with matching `groupId` and no `artifactId`
3. the artifact's version among the project's resolved dependencies
4. the artifact's version in the project's dependency management

## Results

| Result | Meaning | Default |
|---|---|---|
| `UNCHANGED` | upstream element is the same at both versions (also reported when both versions are equal, after checking the reference exists) | info |
| `CHANGED` | upstream changed: review your copy, then bump `version` | fail |
| `REMOVED` | element no longer exists at the current version (removed, renamed or moved) | fail |
| `UNRESOLVABLE` | baseline sources/element, current sources, or the current version cannot be determined | fail |
| `INVALID` | malformed annotation or ambiguous reference (e.g. overloaded method without parameter types) | always fail |

Example output:

```
[ERROR] CHANGED de.example.Local#greet(String) (…/Local.java:19)
    upstream org.keycloak:keycloak-services org.keycloak.Foo#greet(String) 26.2.0 -> 26.4.1
    upstream changed, review the local copy and set version = "26.4.1"
    --- org.keycloak:keycloak-services:26.2.0
    +++ org.keycloak:keycloak-services:26.4.1
    @@ -1,3 +1,3 @@
     public String greet(String name) {
    -    return "Hello " + name;
    +    return "Hello, " + name + "!";
     }
```

## Requirements

- Maven 3.9.0+, Java 21+ to run the plugin.
- The annotations are compiled for Java 17.
- The upstream artifact has to publish a `-sources.jar`.
- Annotation attributes have to be string literals (concatenation of literals is fine), as they are read from source.

## Building

```bash
./mvnw clean verify
```

Integration tests (`maven-plugin/src/it`) run against fake upstream artifacts installed into an isolated repository.

## License

[Apache License 2.0](LICENSE)
