/**
 * Connection to Firestore, the application's database, and helpers shared by every Firestore adapter.
 *
 * <p>Locally and in tests the application talks to the Firestore emulator; deployed, to the real project. Which one is
 * decided by {@link com.dokiments.platform.firestore.FirestoreProperties}, whose rules make sure a laptop can never
 * reach a real project by mistake.
 */
@NullMarked
package com.dokiments.platform.firestore;

import org.jspecify.annotations.NullMarked;
