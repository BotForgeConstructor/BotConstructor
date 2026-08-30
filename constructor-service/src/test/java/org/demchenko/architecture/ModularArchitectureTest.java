package org.demchenko.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

class ModularArchitectureTest {
    private static final Set<String> MODULES = Set.of(
            "identity", "workspace", "bot", "flow", "runtime", "telegram", "audit"
    );
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.demchenko");

    @Test
    void requiredModulePackagesExist() {
        Set<String> packages = classes.stream()
                .map(javaClass -> javaClass.getPackageName().split("\\."))
                .filter(parts -> parts.length > 2)
                .map(parts -> parts[2])
                .collect(java.util.stream.Collectors.toSet());

        assertThat(packages).containsAll(MODULES);
    }

    @Test
    void modulesHaveNoCycles() {
        slices().matching("org.demchenko.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    void coreModulesDoNotDependOnTelegram() {
        noClasses().that().resideInAnyPackage(
                        "org.demchenko.identity..", "org.demchenko.workspace..",
                        "org.demchenko.bot..", "org.demchenko.flow..",
                        "org.demchenko.runtime..", "org.demchenko.audit..")
                .should().dependOnClassesThat().resideInAnyPackage("org.telegram..", "org.demchenko.telegram..")
                .check(classes);
    }

    @Test
    void coreDependencyDirectionsAreRestricted() {
        noClasses().that().resideInAnyPackage(
                        "org.demchenko.identity..", "org.demchenko.workspace..",
                        "org.demchenko.bot..", "org.demchenko.flow..", "org.demchenko.audit..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.demchenko.runtime..", "org.demchenko.configuration..")
                .check(classes);

        noClasses().that().resideInAPackage("org.demchenko.runtime..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.demchenko.identity..", "org.demchenko.workspace..", "org.demchenko.audit..")
                .check(classes);
    }

    @Test
    void springBootEntryPointStaysAtApplicationRoot() {
        classes().that().areAnnotatedWith(org.springframework.boot.autoconfigure.SpringBootApplication.class)
                .should().resideInAPackage("org.demchenko")
                .andShould().haveSimpleName("ConstructorApplication")
                .check(classes);
    }

    @Test
    void legacyPackageIsGone() {
        noClasses().should().resideInAPackage("org.demchenko.tg_bot..").check(classes);
    }

    @Test
    void webAndGeneratedApiDoNotDependOnPersistence() {
        noClasses().that().resideInAnyPackage(
                        "org.demchenko.api.web..", "org.demchenko.api.generated..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.demchenko.identity.data..", "org.demchenko.workspace.data..",
                        "org.demchenko.bot.data..", "org.demchenko.identity.model..",
                        "org.demchenko.workspace.model..", "org.demchenko.bot.model..",
                        "jakarta.persistence..", "org.springframework.data..")
                .check(classes);
    }

    @Test
    void adaptersDoNotUseAnotherModulesPersistenceTypesAsContracts() {
        noClasses().that().resideInAnyPackage(
                        "org.demchenko.telegram..", "org.demchenko.api.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.demchenko.identity.model..", "org.demchenko.identity.data.repo..",
                        "org.demchenko.workspace.model..", "org.demchenko.workspace.data..",
                        "org.demchenko.bot.model..", "org.demchenko.bot.data..")
                .check(classes);
    }
}
