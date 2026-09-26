package com.mudassirshahzad.eka.infrastructure.vectorstore.weaviate;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.misc.model.Meta;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only test in this repository that exercises a <em>real</em> Weaviate.
 *
 * <h3>Why it exists</h3>
 * <p>Every other Weaviate test here is mock-based — {@code WeaviateVectorStoreAdapterTest} and
 * {@code WeaviateRetrievalAdapterTest} both stub the client — which means none of them load the
 * client's gRPC stack, and none of them could detect the defect this test was written for.
 *
 * <p>{@code io.weaviate:client} declares its gRPC artifacts under three separate version
 * properties, and they disagree with each other ({@code grpc-netty-shaded} and {@code grpc-stub} at
 * one version, {@code grpc-protobuf} at another). That resolved into a <b>split gRPC stack</b>:
 * {@code grpc-api}/{@code grpc-protobuf} on 1.70.0 while {@code grpc-core}/{@code grpc-netty-shaded}
 * sat on 1.68.2. gRPC requires every {@code io.grpc} artifact to be on the same version; a mixed
 * stack is a latent {@link NoSuchMethodError} at the first call, not merely untidy. {@code build.gradle}
 * now imports {@code io.grpc:grpc-bom} to align them (which also closes GHSA-prj3-ccx8-p6x4).
 *
 * <p>A version pin with no test is a comment. This is what keeps it true: the next dependency bump
 * that re-splits the stack fails here instead of in production.
 *
 * <h3>Why Testcontainers and not the mocked adapters</h3>
 * <p>Link errors only surface when the classes are actually loaded and invoked against a real
 * server. {@code GenericContainer} is used rather than a Weaviate-specific Testcontainers module so
 * that no new dependency is introduced — {@code org.testcontainers:testcontainers} is already on the
 * test classpath, pulled in by the Postgres support the rest of the suite uses.
 */
@Testcontainers
class WeaviateGrpcStackIT {

    private static final int REST_PORT = 8080;
    private static final int GRPC_PORT = 50051;

    // Pinned to the same version docker-compose.yml runs, so this tests what is actually deployed.
    @Container
    @SuppressWarnings("resource") // closed by the Testcontainers JUnit extension
    static final GenericContainer<?> WEAVIATE =
            new GenericContainer<>(DockerImageName.parse("semitechnologies/weaviate:1.25.0"))
                    .withExposedPorts(REST_PORT, GRPC_PORT)
                    .withEnv("QUERY_DEFAULTS_LIMIT", "25")
                    .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
                    .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
                    .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
                    .withEnv("ENABLE_MODULES", "")
                    .withEnv("CLUSTER_HOSTNAME", "node1")
                    .waitingFor(Wait.forHttp("/v1/.well-known/ready").forPort(REST_PORT));

    /**
     * One representative class per {@code io.grpc} artifact, used to locate that artifact's jar on
     * the test classpath and read back the version actually resolved.
     */
    private static final Map<String, String> GRPC_ARTIFACT_PROBES = new LinkedHashMap<>(Map.of(
            "grpc-api",          "io.grpc.ManagedChannelBuilder",
            "grpc-core",         "io.grpc.internal.GrpcUtil",
            "grpc-stub",         "io.grpc.stub.AbstractStub",
            "grpc-protobuf",     "io.grpc.protobuf.ProtoUtils",
            "grpc-util",         "io.grpc.util.MutableHandlerRegistry",
            "grpc-netty-shaded", "io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder"));

    private static final Pattern JAR_VERSION =
            Pattern.compile("-(\\d+\\.\\d+\\.\\d+(?:[.-][A-Za-z0-9]+)*)\\.jar$");

    /**
     * Asserts every {@code io.grpc} artifact on the classpath resolves to the <em>same</em> version.
     *
     * <p>This is the assertion the {@code grpc-bom} import exists for, and it is deliberately a
     * version comparison rather than a class-loading check. Class loading is <b>not</b> sufficient:
     * verified by temporarily removing the BOM and re-running this class — with the stack split
     * across 1.68.2 and 1.70.0 every class still loaded, a channel still constructed, and a real
     * Weaviate round-trip still succeeded. A mixed gRPC stack fails later and conditionally, on
     * whichever call first crosses a changed internal signature, which is exactly why it needs an
     * explicit check rather than an incidental one.
     */
    @Test
    void every_grpc_artifact_resolves_to_the_same_version() {
        Map<String, String> versions = new LinkedHashMap<>();
        GRPC_ARTIFACT_PROBES.forEach((artifact, probeClass) -> {
            String version = resolveArtifactVersion(probeClass);
            if (version != null) {
                versions.put(artifact, version);
            }
        });

        assertThat(versions)
                .as("no io.grpc artifacts were found on the classpath — the probe classes are wrong")
                .isNotEmpty();

        Set<String> distinct = Set.copyOf(versions.values());
        assertThat(distinct)
                .as("gRPC requires every io.grpc artifact on one version; resolved: %s. "
                        + "Check that build.gradle still imports io.grpc:grpc-bom.", versions)
                .hasSize(1);
    }

    /**
     * Loads and links the gRPC stack against a real server.
     *
     * <p>Complements, rather than duplicates, the version assertion above: this catches an artifact
     * that is missing or genuinely incompatible, where that one catches a silent version split.
     */
    @Test
    void grpc_stack_is_link_compatible() {
        GRPC_ARTIFACT_PROBES.values().forEach(WeaviateGrpcStackIT::assertThatClassLinks);

        // Constructing a channel links NettyChannelBuilder against grpc-api for real.
        io.grpc.ManagedChannel channel = io.grpc.ManagedChannelBuilder
                .forAddress(WEAVIATE.getHost(), WEAVIATE.getMappedPort(GRPC_PORT))
                .usePlaintext()
                .build();
        try {
            assertThat(channel).isNotNull();
        } finally {
            channel.shutdownNow();
        }
    }

    /** Reads the artifact version from the jar manifest, falling back to the jar file name. */
    private static String resolveArtifactVersion(String probeClass) {
        try {
            Class<?> type = Class.forName(probeClass);
            File jar = new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());

            try (JarFile jarFile = new JarFile(jar)) {
                String fromManifest = jarFile.getManifest() == null ? null
                        : jarFile.getManifest().getMainAttributes().getValue("Implementation-Version");
                if (fromManifest != null && !fromManifest.isBlank()) {
                    return fromManifest.trim();
                }
            }

            Matcher matcher = JAR_VERSION.matcher(jar.getName());
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception e) {
            return null;   // artifact absent from this configuration; not this test's concern
        }
    }

    /**
     * A real client round-trip through the same {@link Config} shape {@code WeaviateClientConfig}
     * builds in production, so that a client upgrade breaking that constructor is caught here.
     */
    @Test
    void weaviate_client_round_trips_against_a_real_server() {
        WeaviateClient client = new WeaviateClient(new Config(
                "http",
                WEAVIATE.getHost() + ":" + WEAVIATE.getMappedPort(REST_PORT),
                Map.of(),
                5, 5, 20));

        Result<Meta> meta = client.misc().metaGetter().run();
        assertThat(meta.hasErrors()).as("meta: %s", meta.getError()).isFalse();
        assertThat(meta.getResult().getVersion()).isNotBlank();

        String className = "EkaGrpcStackIT";
        try {
            Result<Boolean> created = client.schema().classCreator()
                    .withClass(WeaviateClass.builder().className(className).vectorizer("none").build())
                    .run();
            assertThat(created.hasErrors()).as("create: %s", created.getError()).isFalse();
            assertThat(created.getResult()).isTrue();

            Result<WeaviateClass> fetched =
                    client.schema().classGetter().withClassName(className).run();
            assertThat(fetched.hasErrors()).as("get: %s", fetched.getError()).isFalse();
            assertThat(fetched.getResult().getClassName()).isEqualTo(className);
        } finally {
            client.schema().classDeleter().withClassName(className).run();
        }
    }

    private static void assertThatClassLinks(String className) {
        try {
            assertThat(Class.forName(className)).isNotNull();
        } catch (Throwable t) {
            throw new AssertionError(
                    "gRPC stack is not link-compatible — check that every io.grpc artifact resolves "
                            + "to the same version (build.gradle imports io.grpc:grpc-bom): "
                            + className + " -> " + t, t);
        }
    }
}
