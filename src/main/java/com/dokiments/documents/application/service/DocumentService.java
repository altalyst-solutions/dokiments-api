package com.dokiments.documents.application.service;

import com.dokiments.documents.application.port.in.ManageDocuments;
import com.dokiments.documents.application.port.out.DocumentStore;
import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentFilter;
import com.dokiments.documents.domain.DocumentId;
import com.dokiments.documents.domain.DocumentNotFoundException;
import com.dokiments.documents.domain.UnknownTemplateException;
import com.dokiments.documents.domain.UserDocument;
import com.dokiments.templates.Templates;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

@Service
class DocumentService implements ManageDocuments {

    private final DocumentStore store;
    private final Templates templates;
    private final Clock clock;
    private final RandomGenerator random = new SecureRandom();

    DocumentService(DocumentStore store, Templates templates, Clock clock) {
        this.store = store;
        this.templates = templates;
        this.clock = clock;
    }

    @Override
    public List<UserDocument> list(String uid, DocumentFilter filter) {
        return store.all(uid).stream().filter(filter::matches).toList();
    }

    @Override
    public UserDocument get(String uid, String documentId) {
        return DocumentId.parse(documentId)
                .flatMap(id -> store.find(uid, id))
                .orElseThrow(DocumentNotFoundException::new);
    }

    @Override
    public UserDocument create(String uid, String templateId, String name, Map<String, String> values) {
        var snapshot = templates.snapshot(templateId).orElseThrow(UnknownTemplateException::new);
        var now = now();
        var document = new UserDocument(DocumentId.generate(now, random), name, templateId, snapshot, values, now, now);
        store.add(uid, document);
        return document;
    }

    @Override
    public UserDocument change(String uid, String documentId, DocumentChanges changes) {
        var id = DocumentId.parse(documentId).orElseThrow(DocumentNotFoundException::new);
        // Checked first so a change can never create a document, then read again afterwards, because the answer is
        // the whole document as stored, which another change may have touched in between.
        if (store.find(uid, id).isEmpty() || !store.change(uid, id, changes, now())) {
            throw new DocumentNotFoundException();
        }
        return store.find(uid, id).orElseThrow(DocumentNotFoundException::new);
    }

    @Override
    public void remove(String uid, String documentId) {
        DocumentId.parse(documentId).ifPresent(id -> store.remove(uid, id));
    }

    /** Milliseconds, the precision the web app stores and answers with. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
