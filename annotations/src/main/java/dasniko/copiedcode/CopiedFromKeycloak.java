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
 * Marks code copied from Keycloak. Same as {@link CopiedFrom}, with the groupId fixed to {@code org.keycloak}.
 */
@Documented
@Retention(SOURCE)
@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})
public @interface CopiedFromKeycloak {

	/**
	 * Upstream artifactId, e.g. {@code "keycloak-services"}.
	 * If empty, the artifact is searched among the project's {@code org.keycloak} dependencies.
	 */
	String artifact() default "";

	/**
	 * Upstream element, e.g. {@code "org.keycloak.services.resources.LoginActionsService#authenticate(String, String, String, String)"},
	 * see {@link CopiedFrom#source()} for the format.
	 */
	String source();

	/**
	 * Upstream version last reviewed; the baseline for the comparison, e.g. {@code "26.4.0"}.
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
