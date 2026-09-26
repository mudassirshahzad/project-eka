package com.mudassirshahzad.eka.smoke;

import com.mudassirshahzad.eka.domain.generation.port.LlmPort;
import com.mudassirshahzad.eka.domain.retrieval.port.RetrievalPort;
import com.mudassirshahzad.eka.domain.shared.TenantId;

import java.util.UUID;

/**
 * Compiles and runs against the <em>published</em> EKA artifact.
 *
 * <p>Deliberately tiny. This is a packaging test, not a functional one: it asserts that an
 * external project can resolve {@code com.mudassirshahzad:project-eka}, that the jar
 * actually contains the domain model and port interfaces, and that a consumer can
 * construct a domain value object and implement a port without EKA source on its
 * classpath. Behaviour is covered by the test suite in the main build.
 *
 * <p>The lambda below is the real assertion: {@link RetrievalPort} is only implementable
 * from outside if every type on its signature — {@code MetadataFilter},
 * {@code RetrievalOptions}, {@code RetrievalResult} — is also published and reachable.
 * A partial jar would fail to compile here rather than at a consumer's site.
 *
 * <p>Exits non-zero on failure so CI fails rather than passing on a green-looking log.
 */
public final class ConsumerSmokeTest {

    public static void main(String[] args) {
        try {
            // A port must be implementable by a consumer — this is "provider
            // independence" seen from the outside.
            RetrievalPort retrieval = (queryText, tenant, filter, options) -> {
                throw new UnsupportedOperationException("not invoked by the smoke test");
            };

            // The domain model must be constructible without any Spring context.
            UUID     raw      = UUID.randomUUID();
            TenantId tenantId = TenantId.of(raw.toString());

            require(retrieval != null, "RetrievalPort implementable");
            require(raw.equals(tenantId.value()), "TenantId round-trips its value");
            require(LlmPort.class.isInterface(), "LlmPort present as an interface");

            assertJarCarriesOnlyWhatALibraryShould();

            System.out.println("RetrievalPort = " + RetrievalPort.class.getName());
            System.out.println("LlmPort       = " + LlmPort.class.getName());
            System.out.println("TenantId      = " + tenantId);
            System.out.println("CONSUMER SMOKE TEST PASSED");
        } catch (Throwable t) {
            System.err.println("CONSUMER SMOKE TEST FAILED: " + t);
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * The library jar must not leak application-owned resources onto a consumer's classpath.
     *
     * <p>This is not hypothetical tidiness. Spring Boot and Flyway both discover these by
     * convention, so a consumer inherits them without asking:
     *
     * <ul>
     *   <li>{@code application.yml} — Spring Boot loads {@code classpath:/application.yml}.
     *       EKA's requires {@code DB_PASSWORD} and {@code JWT_SECRET_KEY}, so the failure mode
     *       is a consumer that will not start, blaming a file it never wrote.</li>
     *   <li>{@code META-INF/build-info.properties} — a consumer's {@code /actuator/info} would
     *       report EKA's version as its own.</li>
     *   <li>{@code db/migration/**} — the dangerous one. {@code spring.flyway.locations} defaults
     *       to {@code classpath:db/migration}, so a consumer running Flyway would apply EKA's 19
     *       migrations to its own database.</li>
     * </ul>
     *
     * <p>The migrations are still shipped, relocated to {@code eka/db/migration} where nothing
     * scans by default, so a consumer can opt in deliberately. This check shipped after the
     * leak was found in a published jar — the earlier smoke test only proved classes resolved.
     */
    private static void assertJarCarriesOnlyWhatALibraryShould() {
        // Absent: discovered-by-convention, application-owned.
        for (String leaked : new String[] {
                "/application.yml",
                "/META-INF/build-info.properties",
                "/db/migration/V001__create_tenants.sql" }) {
            require(ConsumerSmokeTest.class.getResource(leaked) == null,
                    "library jar must NOT ship " + leaked);
        }

        // Present: the library genuinely needs these.
        require(ConsumerSmokeTest.class.getResource("/prompts/qa-system.txt") != null,
                "library jar MUST ship /prompts/qa-system.txt (TemplateBasedPromptBuilderAdapter "
                        + "loads it from the classpath)");
        require(ConsumerSmokeTest.class.getResource("/eka/db/migration/V001__create_tenants.sql") != null,
                "library jar MUST ship migrations relocated under /eka/db/migration for opt-in use");

        System.out.println("PACKAGING CHECK PASSED (no application.yml, no default-path migrations)");
    }

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("smoke assertion failed: " + what);
        }
    }
}
