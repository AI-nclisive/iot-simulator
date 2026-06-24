package com.ainclusive.iotsim.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Enforces the module boundaries from backend-specs/07_MODULE_STRUCTURE.md.
 * Dependencies flow downward only; the protocol-neutral kernel stays pure.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ainclusive.iotsim");

    @Test
    void domainMustNotDependOnApi() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(CLASSES);
    }

    @Test
    void protocolModelMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("..protocolmodel..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(CLASSES);
    }

    @Test
    void protocolModelMustNotDependOnDomain() {
        noClasses()
                .that().resideInAPackage("..protocolmodel..")
                .should().dependOnClassesThat().resideInAPackage("..domain..")
                .check(CLASSES);
    }
}
