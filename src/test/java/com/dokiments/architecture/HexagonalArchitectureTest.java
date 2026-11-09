package com.dokiments.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal layering inside every module, plus a few project-wide coding rules.
 *
 * <p>Layout of each feature module {@code com.dokiments.<module>}:
 *
 * <ul>
 *   <li>{@code domain}: pure Java business rules; depends on nothing but the JDK, JSpecify and {@code shared}.
 *   <li>{@code application}: use cases ({@code port.in}), required capabilities ({@code port.out}) and services; no
 *       adapters, no web, no Firebase.
 *   <li>{@code adapter.in} / {@code adapter.out}: web controllers, persistence, external services; never call each
 *       other directly, only through the application layer.
 * </ul>
 *
 * <p>Boundaries <em>between</em> modules are verified separately by Spring Modulith ({@link ModularityTest}).
 *
 * <p>Layer rules use {@code allowEmptyShould(true)} because most layers have no classes yet; the rules switch on
 * automatically as code is added.
 */
@AnalyzeClasses(packages = "com.dokiments", importOptions = ImportOption.DoNotIncludeTests.class)
// The rules are found and run by ArchUnit through @ArchTest, so they look unused to the IDE.
@SuppressWarnings("unused")
class HexagonalArchitectureTest {

    private static final String[] FEATURE_MODULES = {
        "com.dokiments.auth..",
        "com.dokiments.users..",
        "com.dokiments.documents..",
        "com.dokiments.sharing..",
        "com.dokiments.clients..",
        "com.dokiments.templates..",
        "com.dokiments.notifications..",
        "com.dokiments.feedback..",
        "com.dokiments.promo..",
        "com.dokiments.newsletter..",
    };

    // Layering inside a module

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("com.dokiments..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "com.google..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "com.dokiments.platform..")
            .because("the domain holds business rules only and must stay testable without any framework")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDependsOnNoOuterLayer = noClasses()
            .that()
            .resideInAPackage("com.dokiments..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.dokiments..application..", "com.dokiments..adapter..")
            .because("dependencies point inwards: adapter -> application -> domain")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDependsOnNoAdapter = noClasses()
            .that()
            .resideInAPackage("com.dokiments..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.dokiments..adapter..")
            .because("use cases talk to the outside world only through their ports")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationIsTransportAndStorageAgnostic = noClasses()
            .that()
            .resideInAPackage("com.dokiments..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.http..",
                    "jakarta.servlet..",
                    "com.google.firebase..",
                    "com.google.cloud..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..")
            .because("HTTP, JSON and Firestore are adapter concerns")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("com.dokiments..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.dokiments..adapter.out..")
            .because("controllers go through use cases, never straight to persistence or external services")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule outboundAdaptersDoNotUseInboundAdapters = noClasses()
            .that()
            .resideInAPackage("com.dokiments..adapter.out..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.dokiments..adapter.in..")
            .because("persistence and external clients know nothing about the web layer")
            .allowEmptyShould(true);

    // Platform and shared kernel

    @ArchTest
    static final ArchRule platformDoesNotDependOnFeatures = noClasses()
            .that()
            .resideInAPackage("com.dokiments.platform..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FEATURE_MODULES)
            .because("platform is technical plumbing that every feature may use, so it cannot depend on any of them");

    @ArchTest
    static final ArchRule sharedKernelDependsOnNothingInternal = noClasses()
            .that()
            .resideInAPackage("com.dokiments.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FEATURE_MODULES)
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("com.dokiments.platform..")
            .because("the shared kernel sits at the very centre and depends on nothing else in the codebase")
            .allowEmptyShould(true);

    // Project-wide coding rules

    // ArchUnit's built-in rules carry their own explanation. Field injection checks fields,
    // and there are none yet, hence allowEmptyShould.

    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true);

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noLombok = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("lombok..")
            .because("the project uses records and plain Java instead of Lombok , which records already cover");
}
