package com.mudassirshahzad.eka.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private static final String ROOT = "com.mudassirshahzad.eka";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void domain_must_not_depend_on_application_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".application..");
        rule.check(classes);
    }

    @Test
    void domain_must_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".infrastructure..");
        rule.check(classes);
    }

    @Test
    void domain_must_not_depend_on_api_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".api..");
        rule.check(classes);
    }

    @Test
    void application_must_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".application..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".infrastructure..");
        rule.check(classes);
    }

    @Test
    void application_must_not_depend_on_api_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".application..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".api..");
        rule.check(classes);
    }

    @Test
    void infrastructure_must_not_depend_on_api_layer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".infrastructure..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".api..");
        rule.check(classes);
    }

    @Test
    void jpa_entities_must_not_leak_into_domain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("jakarta.persistence..");
        rule.check(classes);
    }

    @Test
    void spring_framework_must_not_appear_in_domain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");
        rule.check(classes);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Module-boundary rules.
    //
    // These encode the published-module split documented in
    // docs/analysis/eka-platform-blueprint.md (§6 "Library Boundaries") *before* any
    // code moves, so that a bad move fails the build rather than silently redefining
    // the boundary. Every rule below already holds today; the value is that it stays
    // holding once `domain/` becomes a separately published, JDK-only artifact that
    // external consumers compile against.
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * The single most valuable invariant in this build: {@code domain} is pure Java.
     *
     * <p>Spring and JPA are already covered by the two rules above; this adds the two
     * remaining frameworks that would otherwise leak in unnoticed — Spring AI (via a
     * provider type on a port signature) and Jackson (via a serialization annotation on
     * a value object). A consumer must be able to depend on the domain model and
     * implement a port without inheriting a vector store, an LLM client, or an opinion
     * about JSON.
     */
    @Test
    void domain_must_not_depend_on_spring_ai_or_jackson() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai..", "com.fasterxml.jackson..");
        rule.check(classes);
    }

    /**
     * The REST layer talks to the application layer and the domain, never to an adapter.
     *
     * <p>Already true, previously unenforced. It matters at a module boundary because
     * {@code api} is the one part of this codebase that is <em>not</em> published — if a
     * controller reached into {@code infrastructure} directly, the dependency it created
     * would be invisible to consumers right up until the split, and would then appear as
     * a cycle.
     */
    @Test
    void api_must_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".api..")
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".infrastructure..");
        rule.check(classes);
    }

    /**
     * Every {@code *Adapter} implements a port declared in the domain.
     *
     * <p>This is what makes "provider independence" checkable rather than aspirational: a
     * class named {@code Adapter} that implements nothing from {@code domain} is not
     * adapting a port, it is infrastructure with a misleading name — and it cannot be
     * swapped for another provider, because nothing declares what it is a provider
     * <em>of</em>.
     */
    @Test
    void adapters_must_implement_a_domain_port() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .and().areNotInterfaces()
                .should().dependOnClassesThat()
                .resideInAPackage(ROOT + ".domain..")
                .because("an adapter exists to implement a port declared in the domain; "
                        + "one that implements no domain interface cannot be substituted "
                        + "for a different provider");
        rule.check(classes);
    }
}
