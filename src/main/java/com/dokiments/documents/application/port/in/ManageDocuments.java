package com.dokiments.documents.application.port.in;

import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentFilter;
import com.dokiments.documents.domain.UserDocument;
import java.util.List;
import java.util.Map;

/** A user's own documents. Every method works only on the documents of the user {@code uid}, the signed-in user. */
public interface ManageDocuments {

    /** The user's documents matching the filter, in the order they are stored, by ID. */
    List<UserDocument> list(String uid, DocumentFilter filter);

    /**
     * One of the user's documents.
     *
     * @throws com.dokiments.documents.domain.DocumentNotFoundException if the user has no document with this ID
     */
    UserDocument get(String uid, String documentId);

    /**
     * Makes a document from a template of the catalog, keeping a copy of the template as it is now.
     *
     * @param name the name, already checked with {@code DocumentContent.name}
     * @param values the values, already checked with {@code DocumentContent.values}
     * @throws com.dokiments.documents.domain.UnknownTemplateException if the template isn't in the catalog
     */
    UserDocument create(String uid, String templateId, String name, Map<String, String> values);

    /**
     * Makes an independent copy of one of the user's documents, with a new name and values. The copy keeps the source's
     * template and its copy of the template, never the catalog's current one, and gets a new ID and new times.
     *
     * @param name the name, already checked with {@code DocumentContent.name}
     * @param values the values, already checked with {@code DocumentContent.values}
     * @throws com.dokiments.documents.domain.DocumentNotFoundException if the user has no document with this ID, or it
     *     is older than template copies and so has nothing to copy the form from
     */
    UserDocument duplicate(String uid, String documentId, String name, Map<String, String> values);

    /**
     * Changes a document's name or values and returns it as stored now.
     *
     * @throws com.dokiments.documents.domain.DocumentNotFoundException if the user has no document with this ID
     */
    UserDocument change(String uid, String documentId, DocumentChanges changes);

    /** Removes a document. Removing one that doesn't exist is not an error. */
    void remove(String uid, String documentId);
}
