package com.dokiments.templates.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.templates.application.port.out.SavedTemplateLibrary;
import com.dokiments.templates.domain.Plans;
import com.dokiments.templates.domain.SavedTemplate;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Each user's saved templates, in {@code users/{uid}/savedTemplates/{templateId}}, as the web app keeps them: one
 * document per template, named by the template's ID, holding only {@code savedAt} in milliseconds since 1970. The path
 * always starts from the signed-in user's ID, so it can only reach that user's own library.
 */
@Repository
class FirestoreSavedTemplateLibrary implements SavedTemplateLibrary {

    private static final String SAVED_AT = "savedAt";

    private final Firestore firestore;

    FirestoreSavedTemplateLibrary(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<SavedTemplate> all(String uid) {
        return FirestoreCalls.await(library(uid).get()).getDocuments().stream()
                .map(FirestoreSavedTemplateLibrary::saved)
                .toList();
    }

    @Override
    public SaveOutcome save(String uid, SavedTemplate saved, int limit) {
        var library = library(uid);
        var document = library.document(saved.templateId());
        // A transaction, so the library is counted and written as one step: Firestore runs it again if another save
        // changed the library in between, and two saves at the same moment can't both get past the limit.
        return FirestoreCalls.await(firestore.runTransaction(transaction -> {
            var current = transaction.get(library).get().getDocuments();
            boolean alreadySaved =
                    current.stream().anyMatch(existing -> existing.getId().equals(saved.templateId()));
            if (!Plans.maySave(current.size(), alreadySaved, limit)) {
                return SaveOutcome.LIMIT_REACHED;
            }
            transaction.set(document, Map.of(SAVED_AT, saved.savedAt().toEpochMilli()));
            return alreadySaved ? SaveOutcome.SAVED_AGAIN : SaveOutcome.ADDED;
        }));
    }

    @Override
    public void remove(String uid, String templateId) {
        // Deleting a document that doesn't exist succeeds.
        FirestoreCalls.await(library(uid).document(templateId).delete());
    }

    private CollectionReference library(String uid) {
        return firestore.collection("users").document(uid).collection("savedTemplates");
    }

    /** Only a whole number counts as the time, as in the web app; anything else reads as {@code 0}. */
    private static SavedTemplate saved(DocumentSnapshot document) {
        long savedAt = document.get(SAVED_AT) instanceof Long millis ? millis : 0;
        return new SavedTemplate(document.getId(), Instant.ofEpochMilli(savedAt));
    }
}
