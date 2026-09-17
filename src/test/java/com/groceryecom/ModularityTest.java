package com.groceryecom;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails the build when a module reaches into another module's internals or
 * when modules depend on each other in a cycle.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(GroceryEcomApplication.class);

    @Test
    void detectsExpectedModules() {
        assertThat(modules.stream().map(module -> module.getBasePackage().getName()))
                .contains("com.groceryecom.shared", "com.groceryecom.platform", "com.groceryecom.modules.identity");
    }

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
