package com.dokiments.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * The Firebase emulators (Auth and Firestore) from {@code docker/firebase-emulators}, the same image local development
 * runs. Each test context gets a fresh one with no saved data, so tests never see local development data.
 *
 * <p>The ports are mapped to random free ports on the host, so test runs can't clash with a local {@code bootRun}.
 */
public final class FirebaseEmulatorsContainer extends GenericContainer<FirebaseEmulatorsContainer> {

    static final int FIRESTORE_PORT = 8085;
    static final int AUTH_PORT = 9099;

    public FirebaseEmulatorsContainer() {
        super(TestImages.FIREBASE_EMULATORS);
        addEnv("EMULATORS", "auth,firestore");
        addExposedPorts(FIRESTORE_PORT, AUTH_PORT);
        // start.sh reports it once both emulators answer.
        setWaitStrategy(Wait.forLogMessage(".*\\[firebase-emulators\\] Ready:.*", 1));
    }

    /** {@code host:port} of the Auth emulator, as {@code FIREBASE_AUTH_EMULATOR_HOST} expects it. */
    public String authEndpoint() {
        return getHost() + ":" + getMappedPort(AUTH_PORT);
    }

    /** {@code host:port} of the Firestore emulator, as {@code FIRESTORE_EMULATOR_HOST} expects it. */
    public String firestoreEndpoint() {
        return getHost() + ":" + getMappedPort(FIRESTORE_PORT);
    }
}
