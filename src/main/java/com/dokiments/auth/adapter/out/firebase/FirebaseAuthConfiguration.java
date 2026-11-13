package com.dokiments.auth.adapter.out.firebase;

import com.dokiments.auth.adapter.out.google.GoogleOAuthProperties;
import com.dokiments.platform.email.EmailProperties;
import com.dokiments.platform.firestore.FirestoreProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Creates the Firebase Auth client, after checking it can't reach a real project by mistake. */
@Configuration(proxyBeanMethods = false)
class FirebaseAuthConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthConfiguration.class);

    @Bean
    FirebaseIdentityToolkit firebaseIdentityToolkit(
            FirebaseAuthProperties properties,
            FirestoreProperties firestore,
            EmailProperties email,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        // The emulator only knows projects whose ID starts with "demo-", and those only exist on the emulators: the
        // same
        // rule as for Firestore, so local accounts and real ones can never mix.
        if (properties.usesEmulator()
                && !firestore.projectId().startsWith(FirestoreProperties.EMULATOR_PROJECT_PREFIX)) {
            throw new IllegalStateException("The Firebase Auth emulator must be used with a project ID starting with \""
                    + FirestoreProperties.EMULATOR_PROJECT_PREFIX + "\", but FIREBASE_PROJECT_ID is "
                    + firestore.projectId() + ".");
        }
        if (!properties.usesEmulator() && firestore.usesEmulator()) {
            throw new IllegalStateException("Firestore uses its emulator but Firebase Auth doesn't. Set"
                    + " FIREBASE_AUTH_EMULATOR_HOST too, so accounts and data come from the same place.");
        }
        log.info(
                "Using Firebase Auth {}",
                properties.usesEmulator() ? "on the emulator at " + properties.emulatorHost() : "in Google Cloud");
        // Where Firebase's verification page sends the user once the address is confirmed.
        String continueUrl = email.siteUrl().resolve("/dashboard").toString();
        return new FirebaseIdentityToolkit(
                properties, restClientBuilder, clock, continueUrl, GoogleOAuthProperties.callbackUrl(email.siteUrl()));
    }
}
