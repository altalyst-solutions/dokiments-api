package com.dokiments.sharing.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCallException;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.shared.error.DokimentsException;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.DocumentLockedException;
import com.dokiments.sharing.domain.NotRecipientException;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharePolicy;
import com.dokiments.sharing.domain.ShareRaceException;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.sharing.domain.Viewer;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Transaction;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Shared copies in the top-level {@code sharedDocuments} collection, and each owner's ledger in
 * {@code users/{uid}/shareRecipients}, as the web app keeps them.
 *
 * <p>The web app relies on Firestore's security rules to decide who may read or change a copy. This service reads and
 * writes with full access, so those checks are made in code instead: the owner's in this store and the use cases, by
 * matching {@code ownerId} with the signed-in user, and a recipient's in {@code SharedDocument}, by their account's ID
 * or their verified address.
 */
@Repository
class FirestoreSharedDocuments implements SharedDocumentStore {

    /** The web app's wording for a copy the owner doesn't have. */
    static final String DOCUMENT_NOT_FOUND = "Document not found.";

    private final Firestore firestore;

    FirestoreSharedDocuments(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<ShareLedgerEntry> ledger(String ownerUid) {
        return FirestoreCalls.await(ledgerOf(ownerUid).get()).getDocuments().stream()
                .map(SharedDocumentFields::toLedgerEntry)
                .toList();
    }

    @Override
    public List<SharedDocument> ownedBy(String ownerUid) {
        return FirestoreCalls.await(shared().whereEqualTo(SharedDocumentFields.OWNER_ID, ownerUid)
                        .get())
                .getDocuments()
                .stream()
                .map(SharedDocumentFields::toDocument)
                .toList();
    }

    @Override
    public List<SharedDocument> sharedWith(Viewer viewer) {
        var found = new LinkedHashMap<String, SharedDocument>();
        // An address only counts when Firebase has confirmed it, as in the web app's Firestore rules.
        if (viewer.emailVerified()) {
            collect(found, shared().whereArrayContains(SharedDocumentFields.RECIPIENT_EMAILS, viewer.email()));
        }
        collect(found, shared().whereArrayContains(SharedDocumentFields.RECIPIENT_UIDS, viewer.uid()));
        return List.copyOf(found.values());
    }

    @Override
    public Optional<SharedDocument> find(String sharedDocumentId) {
        DocumentSnapshot stored =
                FirestoreCalls.await(shared().document(sharedDocumentId).get());
        return stored.exists() ? Optional.of(SharedDocumentFields.toDocument(stored)) : Optional.empty();
    }

    @Override
    public Share share(ShareRequest request) {
        var source = request.source();
        var copy = shared().document(SharedDocument.idFor(request.ownerUid(), source.id()));
        var ledger = ledgerOf(request.ownerUid());
        return inTransaction(ShareRaceException::new, transaction -> {
            DocumentSnapshot stored = transaction.get(copy).get();
            var entries = transaction.get(ledger).get().getDocuments().stream()
                    .map(SharedDocumentFields::toLedgerEntry)
                    .toList();
            SharedDocument existing = stored.exists() ? SharedDocumentFields.toDocument(stored) : null;

            var share = Share.of(
                    existing,
                    source,
                    request.ownerUid(),
                    request.ownerEmail(),
                    request.ownerName(),
                    request.email(),
                    request.now());
            SharePolicy.check(
                    request.email(),
                    request.ownerEmail(),
                    entries.stream().map(ShareLedgerEntry::email).toList(),
                    request.limit());

            var previous = entries.stream()
                    .filter(entry -> entry.email().equals(request.email()))
                    .findFirst();
            var entry = new ShareLedgerEntry(
                    request.email(),
                    previous.map(ShareLedgerEntry::firstSharedAt).orElse(request.now()),
                    request.now());
            transaction.set(copy, SharedDocumentFields.of(share.document()));
            transaction.set(ledger.document(request.email()), SharedDocumentFields.of(entry));
            return share;
        });
    }

    @Override
    public SharedDocument sign(String sharedDocumentId, Viewer signer, String value, Instant now) {
        var copy = shared().document(sharedDocumentId);
        // Firestore gives up retrying when other requests keep changing the same copy. A copy being signed twice at the
        // same moment is the likeliest cause, and what the second request would find is a signed copy.
        return inTransaction(DocumentLockedException::new, transaction -> {
            DocumentSnapshot stored = transaction.get(copy).get();
            if (!stored.exists()) {
                throw new NotRecipientException();
            }
            var signed = SharedDocumentFields.toDocument(stored).signedBy(signer, value, now);
            transaction.update(copy, SharedDocumentFields.signatureChange(signed));
            return signed;
        });
    }

    @Override
    public void recordView(String sharedDocumentId, Viewer viewer, Instant now) {
        var copy = shared().document(sharedDocumentId);
        inTransaction(ShareRaceException::new, transaction -> {
            DocumentSnapshot stored = transaction.get(copy).get();
            if (stored.exists()) {
                SharedDocumentFields.toDocument(stored)
                        .firstViewBy(viewer, now)
                        // Only this viewer's own entry is written, so a signature or a change by the owner made at
                        // the same moment is never overwritten, and the copy's time of change stays as it was.
                        .ifPresent(view -> transaction.update(
                                copy,
                                FieldPath.of(SharedDocumentFields.VIEWS, viewer.uid()),
                                SharedDocumentFields.of(view)));
            }
            return Boolean.TRUE;
        });
    }

    @Override
    public SharedDocument revoke(String ownerUid, String sharedDocumentId, Instant now) {
        var copy = shared().document(sharedDocumentId);
        return inTransaction(ShareRaceException::new, transaction -> {
            DocumentSnapshot stored = transaction.get(copy).get();
            if (!stored.exists() || !ownerUid.equals(stored.get(SharedDocumentFields.OWNER_ID))) {
                // Another owner's copy is answered like one that doesn't exist, so IDs can't be probed.
                throw new NotFoundException(DOCUMENT_NOT_FOUND);
            }
            var revoked = SharedDocumentFields.toDocument(stored).revoked(now);
            transaction.update(copy, SharedDocumentFields.recipientChange(revoked));
            return revoked;
        });
    }

    /**
     * Runs a transaction whose rules may refuse it. A refusal thrown inside Firestore's transaction would reach the
     * caller wrapped, as an unknown failure, so it is carried out as a value and thrown again here. A transaction
     * Firestore gave up retrying, because other requests kept changing the same documents, is answered with
     * {@code onContention}.
     */
    private <T> T inTransaction(Supplier<? extends DokimentsException> onContention, Body<T> body) {
        try {
            Outcome<T> outcome = FirestoreCalls.await(firestore.runTransaction(transaction -> {
                try {
                    return Outcome.of(body.run(transaction));
                } catch (DokimentsException refused) {
                    return Outcome.refused(refused);
                }
            }));
            return outcome.get();
        } catch (FirestoreCallException e) {
            if (e.code() == StatusCode.Code.ABORTED) {
                throw onContention.get();
            }
            throw e;
        }
    }

    private void collect(Map<String, SharedDocument> found, Query query) {
        FirestoreCalls.await(query.get())
                .getDocuments()
                .forEach(document -> found.putIfAbsent(document.getId(), SharedDocumentFields.toDocument(document)));
    }

    private CollectionReference shared() {
        return firestore.collection("sharedDocuments");
    }

    private CollectionReference ledgerOf(String ownerUid) {
        return firestore.collection("users").document(ownerUid).collection("shareRecipients");
    }

    /** The work done inside a transaction. */
    @FunctionalInterface
    private interface Body<T> {
        T run(Transaction transaction) throws Exception;
    }

    /** What a transaction ended with: a value, or a refusal to throw once it is over. */
    private record Outcome<T>(@Nullable T value, @Nullable DokimentsException refusal) {

        static <T> Outcome<T> of(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> refused(DokimentsException refusal) {
            return new Outcome<>(null, refusal);
        }

        T get() {
            if (refusal != null) {
                throw refusal;
            }
            if (value == null) {
                throw new IllegalStateException("A transaction ended with neither a value nor a refusal");
            }
            return value;
        }
    }
}
