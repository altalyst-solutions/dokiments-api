package com.dokiments.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marks a full-application integration test: the whole Spring context, the {@code test} profile and real infrastructure
 * from {@link TestcontainersConfiguration}.
 *
 * <p>Tagged {@code integration}, so it runs in {@code ./gradlew integrationTest} rather than the fast {@code ./gradlew
 * test}. Docker must be running.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Tag(IntegrationTest.TAG)
public @interface IntegrationTest {

    /** JUnit tag shared by every integration test, including those that don't start Spring. */
    String TAG = "integration";
}
