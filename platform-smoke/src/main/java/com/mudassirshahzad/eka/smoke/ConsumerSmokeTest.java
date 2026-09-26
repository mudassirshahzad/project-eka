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

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("smoke assertion failed: " + what);
        }
    }
}
