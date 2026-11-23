package com.dokiments.documents.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.documents.application.port.out.DocumentStore;
import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentFilter;
import com.dokiments.documents.domain.DocumentId;
import com.dokiments.documents.domain.DocumentNotFoundException;
import com.dokiments.documents.domain.UnknownTemplateException;
import com.dokiments.documents.domain.UserDocument;
import com.dokiments.templates.TemplateSnapshot;
import com.dokiments.templates.Templates;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T09:30:00.123456Z");
    private static final Instant NOW_MILLIS = Instant.parse("2026-11-14T09:30:00.123Z");
    private static final TemplateSnapshot INVOICE = new TemplateSnapshot(
            "Standard Invoice",
            "A billing sheet",
            "invoice",
            List.of(new TemplateSnapshot.Field("client", "Client", null, "text")),
            new TemplateSnapshot.Style("classic", "Classic", "", "free"));

    private final FakeStore store = new FakeStore();
    private final Templates templates = id -> id.equals("classic-invoice") ? Optional.of(INVOICE) : Optional.empty();
    private final DocumentService service = new DocumentService(store, templates, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aNewDocumentKeepsACopyOfItsTemplateAndTheTimeInMilliseconds() {
        var document = service.create("uid-1", "classic-invoice", "For Acme", Map.of("client", "Acme"));

        assertThat(document.id().value()).startsWith("doc_" + Long.toString(NOW.toEpochMilli(), 36) + "_");
        assertThat(document)
                .isEqualTo(new UserDocument(
                        document.id(),
                        "For Acme",
                        "classic-invoice",
                        INVOICE,
                        Map.of("client", "Acme"),
                        NOW_MILLIS,
                        NOW_MILLIS));
        assertThat(store.all("uid-1")).containsExactly(document);
    }

    @Test
    void aTemplateOutsideTheCatalogIsNotFound() {
        assertThatThrownBy(() -> service.create("uid-1", "classic-nope", "For Acme", Map.of()))
                .isInstanceOf(UnknownTemplateException.class)
                .hasMessage("Template not found.");
        assertThat(store.all("uid-1")).isEmpty();
    }

    @Test
    void theListIsTheUsersOwnOptionallyByTemplate() {
        var invoice = stored("uid-1", "doc_a", "classic-invoice");
        var contract = stored("uid-1", "doc_b", "classic-contract");
        stored("uid-2", "doc_c", "classic-invoice");

        assertThat(service.list("uid-1", DocumentFilter.of(null))).containsExactly(invoice, contract);
        assertThat(service.list("uid-1", DocumentFilter.of("classic-contract"))).containsExactly(contract);
        assertThat(service.list("uid-3", DocumentFilter.of(null))).isEmpty();
    }

    @Test
    void aDocumentThatIsntTheUsersOwnIsNotFound() {
        stored("uid-2", "doc_a", "classic-invoice");

        assertNotFound(() -> service.get("uid-1", "doc_a"));
        assertNotFound(() -> service.get("uid-1", "a/b"));
        assertNotFound(() -> service.change("uid-1", "doc_a", DocumentChanges.of("Mine now", null)));
        assertThat(store.changes).isEmpty();
    }

    @Test
    void aDuplicateKeepsTheSourcesTemplateCopyWithTheNewNameAndValues() {
        var source = stored("uid-1", "doc_a", "classic-invoice");

        var copy = service.duplicate("uid-1", "doc_a", "Copy", Map.of("client", "Beta"));

        assertThat(copy.id()).isNotEqualTo(source.id());
        assertThat(copy.id().value()).startsWith("doc_");
        assertThat(copy)
                .isEqualTo(new UserDocument(
                        copy.id(),
                        "Copy",
                        "classic-invoice",
                        INVOICE,
                        Map.of("client", "Beta"),
                        NOW_MILLIS,
                        NOW_MILLIS));
        assertThat(store.all("uid-1")).containsExactly(source, copy);
    }

    @Test
    void onlyTheUsersOwnDocumentWithATemplateCopyCanBeDuplicated() {
        stored("uid-2", "doc_a", "classic-invoice");
        store.add(
                "uid-1",
                new UserDocument(
                        new DocumentId("doc_old"),
                        "Old",
                        "classic-invoice",
                        null,
                        Map.of(),
                        Instant.EPOCH,
                        Instant.EPOCH));

        assertNotFound(() -> service.duplicate("uid-1", "doc_a", "Copy", Map.of()));
        assertNotFound(() -> service.duplicate("uid-1", "doc_old", "Copy", Map.of()));
        assertNotFound(() -> service.duplicate("uid-1", "a/b", "Copy", Map.of()));
        assertThat(store.all("uid-1")).hasSize(1);
    }

    @Test
    void aChangeIsStoredAndTheDocumentReadAgain() {
        stored("uid-1", "doc_a", "classic-invoice");
        var changes = DocumentChanges.of("Renamed", null);

        var changed = service.change("uid-1", "doc_a", changes);

        assertThat(changed.name()).isEqualTo("Renamed");
        assertThat(changed.updatedAt()).isEqualTo(NOW_MILLIS);
        assertThat(store.changes).containsExactly(changes);
    }

    @Test
    void aDocumentRemovedBeforeTheWriteIsNotFound() {
        stored("uid-1", "doc_a", "classic-invoice");
        store.goneOnChange = true;

        assertNotFound(() -> service.change("uid-1", "doc_a", DocumentChanges.of("Renamed", null)));
    }

    @Test
    void removingIsQuietAndSkipsIdsNoDocumentCouldHave() {
        stored("uid-1", "doc_a", "classic-invoice");

        service.remove("uid-1", "doc_a");
        service.remove("uid-1", "doc_a");
        service.remove("uid-1", "__name__");

        assertThat(store.all("uid-1")).isEmpty();
    }

    private UserDocument stored(String uid, String id, String templateId) {
        var document = new UserDocument(
                new DocumentId(id), "Doc " + id, templateId, INVOICE, Map.of(), Instant.EPOCH, Instant.EPOCH);
        store.add(uid, document);
        return document;
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(DocumentNotFoundException.class).hasMessage("Document not found.");
    }

    /** Documents in memory, by user, in ID order like Firestore's. */
    private static final class FakeStore implements DocumentStore {

        private final Map<String, Map<String, UserDocument>> documents = new HashMap<>();
        private final List<DocumentChanges> changes = new java.util.ArrayList<>();
        private boolean goneOnChange;

        private Map<String, UserDocument> of(String uid) {
            return documents.computeIfAbsent(uid, key -> new TreeMap<>());
        }

        @Override
        public List<UserDocument> all(String uid) {
            return List.copyOf(of(uid).values());
        }

        @Override
        public Optional<UserDocument> find(String uid, DocumentId id) {
            return Optional.ofNullable(of(uid).get(id.value()));
        }

        @Override
        public void add(String uid, UserDocument document) {
            of(uid).put(document.id().value(), document);
        }

        @Override
        public boolean change(String uid, DocumentId id, DocumentChanges changes, Instant changedAt) {
            var current = of(uid).get(id.value());
            if (goneOnChange || current == null) {
                return false;
            }
            this.changes.add(changes);
            var name = changes.name();
            var values = changes.values();
            of(uid).put(
                            id.value(),
                            new UserDocument(
                                    id,
                                    name != null ? name : current.name(),
                                    current.templateId(),
                                    current.templateSnapshot(),
                                    values != null ? values : current.values(),
                                    current.createdAt(),
                                    changedAt));
            return true;
        }

        @Override
        public void remove(String uid, DocumentId id) {
            of(uid).remove(id.value());
        }
    }
}
