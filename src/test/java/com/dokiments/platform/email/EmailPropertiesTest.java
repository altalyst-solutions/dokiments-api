package com.dokiments.platform.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Who receives email, depending on the settings. */
class EmailPropertiesTest {

    @Test
    void withoutAKeyEmailIsOff() {
        assertThat(properties("", List.of(), false).mode()).isEqualTo(EmailMode.DISABLED);
    }

    @Test
    void withAKeyAndNoRestrictionEveryoneReceivesEmail() {
        var properties = properties("re_key", List.of(), false);

        assertThat(properties.mode()).isEqualTo(EmailMode.LIVE);
        assertThat(properties.allows("anyone@example.com")).isTrue();
    }

    @Test
    void anAllowListRestrictsRecipientsToIt() {
        var properties = properties("re_key", List.of("Jane@Example.com"), false);

        assertThat(properties.mode()).isEqualTo(EmailMode.RESTRICTED);
        assertThat(properties.allows("jane@example.com")).isTrue();
        assertThat(properties.allows("someone@example.com")).isFalse();
    }

    @Test
    void whereRecipientsAreRestrictedAnEmptyAllowListMeansNobody() {
        var properties = properties("re_key", List.of(), true);

        assertThat(properties.mode()).isEqualTo(EmailMode.RESTRICTED);
        assertThat(properties.allows("anyone@example.com")).isFalse();
    }

    @Test
    void theAllowListIsReadFromACommaSeparatedSetting() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("dokiments.email.allow-list= You@Example.com, ,team@example.com,")
                .run(context -> assertThat(
                                context.getBean(EmailProperties.class).allowList())
                        .containsExactly("you@example.com", "team@example.com"));
    }

    @Test
    void emptySettingsFallBackToTheDefaults() {
        // Unset environment variables usually arrive as empty strings.
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("dokiments.email.from=", "dokiments.email.reply-to=", "dokiments.email.allow-list=")
                .run(context -> {
                    var properties = context.getBean(EmailProperties.class);
                    assertThat(properties.from()).isEqualTo(EmailProperties.DEFAULT_FROM);
                    assertThat(properties.replyTo()).isNull();
                    assertThat(properties.allowList()).isEmpty();
                    assertThat(properties.mode()).isEqualTo(EmailMode.DISABLED);
                });
    }

    @Test
    void aSiteUrlThatIsntAbsoluteIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailProperties(
                        "", "", null, List.of(), false, URI.create("dokiments.com"), URI.create("https://x.test")))
                .withMessageContaining("site-url must be an absolute address");
    }

    private static EmailProperties properties(String key, List<String> allowList, boolean restricted) {
        return new EmailProperties(
                key,
                EmailProperties.DEFAULT_FROM,
                null,
                allowList,
                restricted,
                URI.create("https://dokiments.com"),
                URI.create("https://api.resend.com"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    static class PropertiesConfiguration {}
}
