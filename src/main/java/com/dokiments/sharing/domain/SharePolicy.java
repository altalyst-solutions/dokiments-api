package com.dokiments.sharing.domain;

import com.dokiments.templates.TemplateSnapshot;
import com.dokiments.users.UserRole;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The web app's rules for whom a document may be shared with. */
public final class SharePolicy {

    /** The template field the recipient signs in. Only a document whose template has it can be shared for signing. */
    public static final String RECIPIENT_SIGNATURE_KEY = "recipientSignature";

    /** The plans that have a sharing limit, with their limit. None has one today. */
    private static final Map<UserRole, Integer> LIMITS = Map.of();

    private SharePolicy() {}

    /**
     * How many different people a plan may share documents with, counted over the owner's whole share ledger. Null
     * means no limit. No plan has a limit today; the rule is kept so a limit is a change here only.
     */
    public static @Nullable Integer limitFor(UserRole role) {
        return LIMITS.get(role);
    }

    /** Whether a document made from this template can be shared for signing. */
    public static boolean isSignable(@Nullable TemplateSnapshot template) {
        return template != null
                && template.fields().stream().anyMatch(field -> field.key().equals(RECIPIENT_SIGNATURE_KEY));
    }

    /**
     * Checks that the owner may share with {@code email}: not themselves, and within the plan's limit, where someone
     * already in the ledger never counts again.
     *
     * @throws SelfShareException if it is the owner's own address
     * @throws ShareLimitReachedException if the plan allows nobody more
     */
    public static void check(String email, String ownerEmail, Collection<String> ledger, @Nullable Integer limit) {
        if (email.equals(ShareEmails.normalize(ownerEmail))) {
            throw new SelfShareException();
        }
        if (limit != null && !ledger.contains(email) && ledger.size() >= limit) {
            throw new ShareLimitReachedException();
        }
    }
}
