package com.dokiments.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Checks how the {@code dokiments.*} settings are bound: defaults, overrides and invalid values. */
class DokimentsPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void sharingIsOffAndTierLocksAreOnByDefault() {
        contextRunner.run(context -> {
            var features = context.getBean(DokimentsProperties.class).features();
            assertThat(features.documentSharing()).isFalse();
            assertThat(features.tierLocks()).isTrue();
        });
    }

    @Test
    void featuresCanBeSwitched() {
        contextRunner
                .withPropertyValues("dokiments.features.document-sharing=true", "dokiments.features.tier-locks=false")
                .run(context -> {
                    var features = context.getBean(DokimentsProperties.class).features();
                    assertThat(features.documentSharing()).isTrue();
                    assertThat(features.tierLocks()).isFalse();
                });
    }

    @Test
    void aValueThatIsNotABooleanStopsTheApplicationFromStarting() {
        contextRunner
                .withPropertyValues("dokiments.features.document-sharing=yes please")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DokimentsProperties.class)
    static class PropertiesConfiguration {}
}
