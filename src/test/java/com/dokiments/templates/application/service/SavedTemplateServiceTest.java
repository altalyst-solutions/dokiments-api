package com.dokiments.templates.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.templates.application.port.out.SavedTemplateLibrary;
import com.dokiments.templates.application.port.out.TemplateCatalog;
import com.dokiments.templates.domain.Catalog;
import com.dokiments.templates.domain.Plans;
import com.dokiments.templates.domain.SavedTemplate;
import com.dokiments.templates.domain.SavedTemplateLimitException;
import com.dokiments.templates.domain.Template;
import com.dokiments.templates.domain.TemplateNotFoundException;
import com.dokiments.templates.domain.TemplateNotInPlanException;
import com.dokiments.templates.domain.TemplateStyle;
import com.dokiments.templates.domain.TemplateTier;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserProfiles;
import com.dokiments.users.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@NullMarked
class SavedTemplateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T09:30:00.123456Z");
    private static final TemplateStyle CLASSIC = new TemplateStyle("classic", "Classic", "", TemplateTier.FREE);
    private static final TemplateStyle MODERN = new TemplateStyle("modern", "Modern", "", TemplateTier.SILVER);
    private static final Catalog CATALOG = new Catalog(
            List.of(CLASSIC, MODERN),
            List.of(
                    template("classic-contract", CLASSIC),
                    template("classic-invoice", CLASSIC),
                    template("classic-receipt", CLASSIC),
                    template("modern-invoice", MODERN)));

    private final FakeLibrary library = new FakeLibrary();
    private final Map<String, UserRole> roles = new HashMap<>();

    @Test
    void aTemplateIsSavedWithTheTimeInMilliseconds() {
        var saved = service(true).save("uid-1", "classic-invoice");

        assertThat(saved.added()).isTrue();
        assertThat(saved.saved())
                .isEqualTo(new SavedTemplate("classic-invoice", Instant.parse("2026-11-14T09:30:00.123Z")));
        assertThat(library.limits).containsExactly(Plans.FREE_SAVED_TEMPLATE_LIMIT);
    }

    @Test
    void savingAgainOnlyUpdatesTheTime() {
        library.stored("uid-1").put("classic-invoice", new SavedTemplate("classic-invoice", Instant.EPOCH));

        var saved = service(true).save("uid-1", "classic-invoice");

        assertThat(saved.added()).isFalse();
        assertThat(library.all("uid-1")).containsExactly(saved.saved());
    }

    @Test
    void aTemplateOutsideTheCatalogIsNotFound() {
        assertThatThrownBy(() -> service(true).save("uid-1", "classic-nope"))
                .isInstanceOf(TemplateNotFoundException.class)
                .hasMessage("Template not found.");
    }

    @Test
    void aTemplateOutsideThePlanIsRefusedUnlessTierLocksAreOff() {
        assertThatThrownBy(() -> service(true).save("uid-1", "modern-invoice"))
                .isInstanceOf(TemplateNotInPlanException.class)
                .hasMessage("Your plan does not include this template.");
        assertThat(library.all("uid-1")).isEmpty();

        assertThat(service(false).save("uid-1", "modern-invoice").added()).isTrue();
    }

    @Test
    void theRoleAsStoredNowDecides() {
        roles.put("uid-1", UserRole.SILVER);

        assertThat(service(true).save("uid-1", "modern-invoice").added()).isTrue();
        assertThat(library.limits).containsExactly(Integer.MAX_VALUE);
    }

    @Test
    void aFullLibraryIsRefused() {
        var service = service(true);
        service.save("uid-1", "classic-contract");
        service.save("uid-1", "classic-invoice");

        assertThatThrownBy(() -> service.save("uid-1", "classic-receipt"))
                .isInstanceOf(SavedTemplateLimitException.class)
                .hasMessage("You've reached your saved template limit.");
        assertThat(service.save("uid-1", "classic-invoice").added()).isFalse();
    }

    @Test
    void theListLeavesOutTemplatesNoLongerInTheCatalog() {
        var stored = library.stored("uid-1");
        stored.put("classic-invoice", new SavedTemplate("classic-invoice", NOW));
        stored.put("classic-retired", new SavedTemplate("classic-retired", NOW));

        var entries = service(true).list("uid-1");

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().template().id()).isEqualTo("classic-invoice");
        assertThat(service(true).list("uid-2")).isEmpty();
    }

    @Test
    void removingIsQuietAndSkipsIdsNoDocumentCouldHave() {
        library.stored("uid-1").put("classic-invoice", new SavedTemplate("classic-invoice", NOW));
        var service = service(true);

        service.remove("uid-1", "classic-invoice");
        service.remove("uid-1", "classic-invoice");
        service.remove("uid-1", "a/b");

        assertThat(library.all("uid-1")).isEmpty();
        assertThat(library.removed).containsExactly("classic-invoice", "classic-invoice");
    }

    private SavedTemplateService service(boolean tierLocks) {
        TemplateCatalog catalog = () -> CATALOG;
        return new SavedTemplateService(
                library,
                catalog,
                new FakeProfiles(),
                new DokimentsProperties(new DokimentsProperties.Features(false, tierLocks)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Template template(String id, TemplateStyle style) {
        return new Template(id, id, "", "invoice", List.of(), style, style.tier(), null);
    }

    /** Saved templates in memory, applying the limit as the real library does. */
    private static final class FakeLibrary implements SavedTemplateLibrary {

        private final Map<String, Map<String, SavedTemplate>> libraries = new HashMap<>();
        private final List<Integer> limits = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        Map<String, SavedTemplate> stored(String uid) {
            return libraries.computeIfAbsent(uid, key -> new TreeMap<>());
        }

        @Override
        public List<SavedTemplate> all(String uid) {
            return List.copyOf(stored(uid).values());
        }

        @Override
        public SaveOutcome save(String uid, SavedTemplate saved, int limit) {
            limits.add(limit);
            var library = stored(uid);
            boolean already = library.containsKey(saved.templateId());
            if (!Plans.maySave(library.size(), already, limit)) {
                return SaveOutcome.LIMIT_REACHED;
            }
            library.put(saved.templateId(), saved);
            return already ? SaveOutcome.SAVED_AGAIN : SaveOutcome.ADDED;
        }

        @Override
        public void remove(String uid, String templateId) {
            removed.add(templateId);
            stored(uid).remove(templateId);
        }
    }

    /** Only the role matters here. */
    private final class FakeProfiles implements UserProfiles {

        @Override
        public UserRole roleOf(String uid) {
            return roles.getOrDefault(uid, UserRole.FREE);
        }

        @Override
        public UserProfile findOrCreate(String uid, String email, @Nullable String displayName, AuthProvider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile updateProfile(UserProfile current, ProfileChanges changes) {
            throw new UnsupportedOperationException();
        }
    }
}
