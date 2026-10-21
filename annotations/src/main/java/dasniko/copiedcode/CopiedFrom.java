package dasniko.copiedcode;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Marks code copied from the sources of a Maven artifact.
 * <p>
 * The {@code copied-code-maven-plugin} compares the referenced upstream element at {@link #version()} with the same element at the
 * version the project currently builds against, and reports upstream changes, so that the local copy can be reviewed and updated.
 * The local copy itself is never compared, so a {@link #modified() modified} copy is checked the same way as an unmodified one.
 *
 * @see CopiedFromKeycloak
 */
@Documented
@Retention(SOURCE)
@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})
public @interface CopiedFrom {

	/**
	 * Upstream artifact as {@code groupId:artifactId}, e.g. {@code "org.apache.commons:commons-lang3"}.
	 */
	String artifact();

	/**
	 * Upstream element, in one of these forms:
	 * <ul>
	 *   <li>{@code a.b.Foo} &ndash; the whole type; on a method, constructor or field: the member with the same signature or name
	 *   as the annotated one</li>
	 *   <li>{@code a.b.Foo#bar(String, int)} &ndash; a method; parameter types as simple names without generics</li>
	 *   <li>{@code a.b.Foo#Foo(String)} &ndash; a constructor</li>
	 *   <li>{@code a.b.Foo#bar} &ndash; a field, or a method without overloads</li>
	 * </ul>
	 * Nested types are written as {@code a.b.Foo.Inner}.
	 */
	String source();

	/**
	 * Upstream version last reviewed; the baseline for the comparison.
	 * Bump it after the local copy was reviewed against a newer upstream version.
	 */
	String version();

	/**
	 * Whether the copied code was modified.
	 */
	boolean modified() default false;

	/**
	 * Description of the modifications, if any.
	 */
	String changes() default "";

	/**
	 * Reason why the code was copied instead of reused.
	 */
	String reason() default "";

	/**
	 * Optional date when the code was copied, e.g. {@code "2026-10-21"}.
	 */
	String copiedAt() default "";
}
