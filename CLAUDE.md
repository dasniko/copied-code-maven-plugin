# CLAUDE.md

Guidance for Claude Code in this repository. Usage, `source` format, parameters and result types are in the README; this file
holds what the code does not tell you.

## Build

```bash
./mvnw clean verify                          # unit tests + invoker integration tests
./mvnw clean install -DskipTests -Dinvoker.skip  # install the plugin locally for manual tests
```

- Modules: `annotations` (Java 17, so projects still on 17 can use them) and `maven-plugin` (Java 21).
- Tabs, 140-char line limit (`.editorconfig`). Keep `<properties>` in `pom.xml` sorted alphabetically.
- Packages are `dasniko.copiedcode[.plugin]`; the groupId stays `com.github.dasniko`.

## Design decisions

- **Upstream is compared with upstream, never with the local copy:** the element at the annotation's `version` (baseline)
  against the same element at the current version. That is what makes modified copies work. A stored hash of the copied code
  was considered and rejected: `version` already pins the reviewed state, is human-readable, and allows printing a diff.
- **Maven plugin, not an annotation processor:** resolving artifacts inside javac would break offline builds and run on every
  IDE incremental compile.
- **`maven.version` is the lowest supported Maven line,** not just a dependency version: the plugin is compiled against that API.
  Dependabot therefore ignores minor/major updates of `maven-plugin-api`/`maven-core`. Raising it is a deliberate change,
  together with `<prerequisites>` and the enforcer rule. CI builds with the wrapper's newer Maven, so it would not catch use of
  newer API.
- **Normalized comparison:** JavaParser re-prints both versions (comments/Javadoc dropped unless `ignoreComments=false`), so
  formatting-only changes do not count. Annotations such as an added `@Deprecated` do count. Diffs show this normalized form.
- **Annotations are read from source** (`SOURCE` retention), matched by qualified name via imports; attribute values must be
  string literals or concatenations of them.
- **Artifact auto-detection** (`@CopiedFromKeycloak` without `artifact`) looks for the `.class` entry in the already resolved
  binary jars and downloads only the matching sources jar. Searching sources jars directly downloaded dozens of them for a real
  Keycloak dependency tree; it is only the fallback for dependencies without a resolved file.
- Same baseline and current version still resolves the element, so a typo in `source` shows up immediately.
- A missing *current* sources jar is `UNRESOLVABLE`, not `REMOVED` (more likely a publishing or network issue).
- `INVALID` always fails the build; it is a broken annotation, not upstream drift.
- A reference that is unambiguous at the baseline but ambiguous at the current version (new overload) is `CHANGED`, not
  `INVALID`: it is upstream drift, so `onChanged` applies. The diff is taken against the overload with the baseline's signature.
- Overloads whose parameter types normalize to the same simple names (`java.util.Date`/`java.sql.Date`) cannot be told apart by
  `source`; that is `INVALID` rather than silently comparing the first match.
- Local sources are decoded leniently: only the annotations are read from them.

## Testing

- Unit tests build fake sources/binary jars in `@TempDir` (`CheckerTest`); no Maven runtime needed.
- Invoker ITs (`maven-plugin/src/it`): `setup-upstream-1.0`/`-2.0` install a fake `org.keycloak:it-fake-upstream` with sources
  into the isolated `target/local-repo`, so `@CopiedFromKeycloak` and auto-detection are covered without real Keycloak.
  `fail-on-drift` also proves that `<upstreams>` wins over the dependency version (dependency 1.0, configured 2.0).
- Manually verified against real Keycloak 26.2.0 → 26.8.1 (2026-10-21), useful as a reference:
  `UsernamePasswordForm#action(AuthenticationFlowContext)` CHANGED (passkey branch), `Time#currentTime()` CHANGED (only
  `@Deprecated` added), `Time#currentTimeMillis()` UNCHANGED (Javadoc only), `Time#toDate` INVALID (overloaded),
  `WebAuthnConditionalUIAuthenticator` at 26.2.0 UNRESOLVABLE (does not exist yet).
