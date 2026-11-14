package com.dokiments.templates.domain;

import com.dokiments.users.UserRole;

/** What each plan allows with templates, as the web app decides it. */
public final class Plans {

    /** How many templates a free account may keep saved. */
    public static final int FREE_SAVED_TEMPLATE_LIMIT = 2;

    private Plans() {}

    /**
     * Whether a role's plan includes templates of a tier. Plans rank free, silver, gold, then the special and
     * superadmin roles, and each includes the tiers up to its own.
     *
     * @param tierLocks whether plans limit templates at all; while off, every plan includes every tier
     */
    public static boolean includes(UserRole role, TemplateTier tier, boolean tierLocks) {
        return !tierLocks || rank(role) >= rank(tier);
    }

    /** How many templates the role may keep saved: a limit for free accounts, none for the others. */
    public static int savedTemplateLimit(UserRole role) {
        return role == UserRole.FREE ? FREE_SAVED_TEMPLATE_LIMIT : Integer.MAX_VALUE;
    }

    /**
     * Whether a template may be saved: always when it is saved already (saving again only updates the time), otherwise
     * only while the library is below the limit.
     */
    public static boolean maySave(int savedCount, boolean alreadySaved, int limit) {
        return alreadySaved || savedCount < limit;
    }

    private static int rank(TemplateTier tier) {
        return switch (tier) {
            case FREE -> 0;
            case SILVER -> 1;
            case GOLD -> 2;
        };
    }

    private static int rank(UserRole role) {
        return switch (role) {
            case FREE -> 0;
            case SILVER -> 1;
            case GOLD -> 2;
            case SPECIAL -> 3;
            case SUPERADMIN -> 4;
        };
    }
}
