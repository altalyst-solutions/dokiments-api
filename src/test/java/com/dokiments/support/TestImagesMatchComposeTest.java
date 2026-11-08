package com.dokiments.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Keeps the integration test images and {@code compose.yaml} on the same versions.
 *
 * <p>Dependabot updates image tags in {@code compose.yaml} but can't see the constants in {@link TestImages}. Without
 * this check, a Dependabot pull request would move local development to a new Redis or emulator version while the tests
 * kept running against the old one. When this test fails, copy the new tag from {@code compose.yaml} into
 * {@link TestImages}.
 */
class TestImagesMatchComposeTest {

    private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*image:\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    @Test
    void everyTestImageIsTheOneComposeUses() throws IOException {
        // Gradle runs tests with the project directory as the working directory.
        var compose = Files.readString(Path.of("compose.yaml"));
        Set<String> composeImages = IMAGE_LINE
                .matcher(compose)
                .results()
                .map(match -> match.group(1))
                .collect(Collectors.toSet());

        assertThat(composeImages)
                .contains(
                        TestImages.REDIS.asCanonicalNameString(),
                        TestImages.FIRESTORE_EMULATOR.asCanonicalNameString());
    }
}
