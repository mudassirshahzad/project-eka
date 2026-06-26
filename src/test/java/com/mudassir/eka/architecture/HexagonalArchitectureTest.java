package com.mudassir.eka.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private static final String ROOT = "com.mudassir.eka";

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
}
