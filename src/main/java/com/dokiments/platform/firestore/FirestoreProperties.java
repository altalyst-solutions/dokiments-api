package com.dokiments.platform.firestore;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which Firestore the application uses, under {@code dokiments.firestore}.
 *
 * <p>The combination is checked as soon as the settings are read. A wrong one stops the application at start-up, with a
 * message saying what to fix. Otherwise, it would only fail on the first request or, worse, write to the wrong
 * database:
 *
 * <ul>
 *   <li>The emulator only ever runs with a {@code demo-} project ID. Google reserves that prefix for emulators, so no
 *       real project can have it, and nothing written to it can reach a real database.
 *   <li>A {@code demo-} project ID therefore needs the emulator: the real Firestore would just reject it.
 *   <li>Where the emulator is required ({@code dev} and tests), running without one is refused. Without this rule, a
 *       laptop that happened to have Google credentials and a real project ID set could read and write real data.
 * </ul>
 *
 * @param projectId the Google Cloud project that holds the database
 * @param emulatorHost {@code host:port} of the Firestore emulator, or empty to use the real Firestore
 * @param requireEmulator refuse to start unless the emulator is used
 */
@ConfigurationProperties("dokiments.firestore")
public record FirestoreProperties(
        String projectId,
        @Nullable String emulatorHost,
        @DefaultValue("false") boolean requireEmulator) {

    /** Prefix Google reserves for emulator projects. No real project can have it. */
    public static final String EMULATOR_PROJECT_PREFIX = "demo-";

    public FirestoreProperties {
        // Never null: application.yml always supplies a value, empty when FIREBASE_PROJECT_ID isn't set.
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("dokiments.firestore.project-id must be set (FIREBASE_PROJECT_ID).");
        }
        // The settings come from environment variables, where "not set" usually arrives as an empty string.
        if (emulatorHost != null && emulatorHost.isBlank()) {
            emulatorHost = null;
        }

        // Checked first: where the emulator is required, its absence is the clearest thing to report.
        if (requireEmulator && emulatorHost == null) {
            throw new IllegalArgumentException("This profile only allows the Firestore emulator, but no emulator is"
                    + " set. Set FIRESTORE_EMULATOR_HOST (in dev it comes from compose.yaml).");
        }

        boolean demoProject = projectId.startsWith(EMULATOR_PROJECT_PREFIX);
        if (emulatorHost != null && !demoProject) {
            throw new IllegalArgumentException("The Firestore emulator must use a project ID starting with \""
                    + EMULATOR_PROJECT_PREFIX + "\", so nothing can reach a real project, but it is \"" + projectId
                    + "\". Check FIREBASE_PROJECT_ID.");
        }
        if (emulatorHost == null && demoProject) {
            throw new IllegalArgumentException("The project ID \"" + projectId + "\" only exists in the emulator, but"
                    + " no emulator is set. Set FIRESTORE_EMULATOR_HOST, or use the real project ID.");
        }
    }

    /** Whether the emulator is used instead of the real Firestore. */
    public boolean usesEmulator() {
        return emulatorHost != null;
    }
}
