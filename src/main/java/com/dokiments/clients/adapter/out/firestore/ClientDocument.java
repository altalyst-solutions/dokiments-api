package com.dokiments.clients.adapter.out.firestore;

import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientDetails;
import com.dokiments.clients.domain.ClientId;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A client as stored in {@code users/{uid}/clients/{clientId}}: the seven details as text, and {@code createdAt} and
 * {@code updatedAt} as timestamps. Field names and types are fixed by the documents the web app already wrote.
 *
 * <p>Reading is lenient, as the web app's is: a missing field, or one of another type, reads as empty text, and a
 * missing time as the start of 1970 (shown as {@code 0}). Older clients came from the browser's storage and may lack
 * fields.
 */
final class ClientDocument {

    private static final String ADDRESS = "address";
    private static final String BRN = "brn";
    private static final String COMPANY_NAME = "companyName";
    private static final String EMAIL = "email";
    private static final String NAME = "name";
    private static final String NATIONAL_ID = "nationalId";
    private static final String PHONE = "phone";
    private static final String CREATED_AT = "createdAt";
    static final String UPDATED_AT = "updatedAt";

    private ClientDocument() {}

    static Map<String, Object> of(Client client) {
        var details = client.details();
        var fields = new HashMap<String, Object>();
        fields.put(ADDRESS, details.address());
        fields.put(BRN, details.brn());
        fields.put(COMPANY_NAME, details.companyName());
        fields.put(EMAIL, details.email());
        fields.put(NAME, details.name());
        fields.put(NATIONAL_ID, details.nationalId());
        fields.put(PHONE, details.phone());
        fields.put(CREATED_AT, FirestoreCalls.toTimestamp(client.createdAt()));
        fields.put(UPDATED_AT, FirestoreCalls.toTimestamp(client.updatedAt()));
        return fields;
    }

    /** Only the fields that change, and the time of the change. */
    static Map<String, Object> of(ClientChanges changes, Instant changedAt) {
        var fields = new HashMap<String, Object>();
        putIfGiven(fields, ADDRESS, changes.address());
        putIfGiven(fields, BRN, changes.brn());
        putIfGiven(fields, COMPANY_NAME, changes.companyName());
        putIfGiven(fields, EMAIL, changes.email());
        putIfGiven(fields, NAME, changes.name());
        putIfGiven(fields, NATIONAL_ID, changes.nationalId());
        putIfGiven(fields, PHONE, changes.phone());
        fields.put(UPDATED_AT, FirestoreCalls.toTimestamp(changedAt));
        return fields;
    }

    static Client toClient(DocumentSnapshot document) {
        return new Client(
                new ClientId(document.getId()),
                new ClientDetails(
                        text(document, ADDRESS),
                        text(document, BRN),
                        text(document, COMPANY_NAME),
                        text(document, EMAIL),
                        text(document, NAME),
                        text(document, NATIONAL_ID),
                        text(document, PHONE)),
                time(document, CREATED_AT),
                time(document, UPDATED_AT));
    }

    private static void putIfGiven(Map<String, Object> fields, String name, @Nullable String value) {
        if (value != null) {
            fields.put(name, value);
        }
    }

    private static String text(DocumentSnapshot document, String field) {
        return document.get(field) instanceof String value ? value : "";
    }

    private static Instant time(DocumentSnapshot document, String field) {
        return document.get(field) instanceof Timestamp value ? FirestoreCalls.toInstant(value) : Instant.EPOCH;
    }
}
