package com.groceryecom;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture rules enforced on every build.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(GroceryEcomApplication.class);

    @Test
    void detectsExpectedModules() {
        assertThat(modules.stream().map(module -> module.getBasePackage().getName()))
                .contains("com.groceryecom.shared", "com.groceryecom.platform", "com.groceryecom.modules.identity");
    }

    /** A module may use another module only through its api package, and never in a cycle. */
    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    /** shared and platform are foundations: business modules depend on them, never the other way round. */
    @Test
    void foundationsDoNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAnyPackage("com.groceryecom.shared..", "com.groceryecom.platform..")
                .should().dependOnClassesThat().resideInAPackage("com.groceryecom.modules..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.groceryecom"));
    }
}
