package com.dokiments.architecture;

import com.dokiments.DokimentsApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the boundaries between application modules (bounded contexts) with Spring Modulith.
 *
 * <p>Each direct sub-package of {@code com.dokiments} is a module. Its <em>base package</em> is its public API; every
 * sub-package ({@code domain}, {@code application}, {@code adapter}, …) is internal and invisible to other modules.
 * Modules collaborate through types in a base package, a {@code @NamedInterface}, or application events. Cycles between
 * modules are rejected.
 *
 * <p>Layering <em>inside</em> a module is checked by {@link HexagonalArchitectureTest}.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(DokimentsApiApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    /** Writes module diagrams (PlantUML) and a module canvas to {@code build/spring-modulith-docs}. */
    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
