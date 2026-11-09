package com.dokiments.platform.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The emails as recipients see them, in both versions, with the web app's wording. */
class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates(URI.create("https://dokiments.com"));

    @Test
    void theSharedEmailInvitesTheRecipientToSign() {
        var email = templates.render(shared(false, "Jane Doe", "Service agreement"));

        assertThat(email.html())
                .contains("Jane Doe shared a document with you")
                .contains("Jane Doe shared &quot;Service agreement&quot; with you on Dokiments for your signature.")
                .contains("New to Dokiments? Create an account with this email address")
                .contains("href=\"https://dokiments.com/my-documents?tab=shared\"")
                .contains(">Open document<")
                .doesNotContain("made changes");
        assertThat(email.text())
                .startsWith("Jane Doe shared a document with you\n\n")
                .contains("Jane Doe shared \"Service agreement\" with you on Dokiments for your signature.")
                .contains("Open document: https://dokiments.com/my-documents?tab=shared")
                .doesNotContain("made changes");
    }

    @Test
    void theSharedEmailSaysWhenTheDocumentWasUpdated() {
        var email = templates.render(shared(true, "Jane Doe", "Service agreement"));

        assertThat(email.html())
                .contains("Jane Doe updated a shared document")
                .contains("Jane Doe made changes to &quot;Service agreement&quot;. Open it to review the latest version"
                        + " and sign.")
                .doesNotContain("for your signature");
        assertThat(email.text()).startsWith("""
                        Jane Doe updated a shared document

                        Jane Doe made changes to "Service agreement". Open it to review the latest version and sign.

                        New to Dokiments?""");
    }

    @Test
    void theSignedEmailSaysWhoSignedAndWhen() {
        var email = templates.render(EmailMessage.of(
                "owner@example.com",
                "Bob signed \"Service agreement\"",
                "sharing/document-signed",
                Map.of(
                        "signerName", "Bob Smith",
                        "documentName", "Service agreement",
                        "signedAt", "31 Oct 2026, 21:30 UTC",
                        "url", "https://dokiments.com/my-documents/shared/abc")));

        assertThat(email.html())
                .contains("Bob Smith signed your document")
                .contains("Bob Smith signed &quot;Service agreement&quot; on 31 Oct 2026, 21:30 UTC.")
                .contains("The document is now locked");
        assertThat(email.text())
                .startsWith("Bob Smith signed your document\n\n")
                .contains("Open document: https://dokiments.com/my-documents/shared/abc");
    }

    @Test
    void notesForDevelopersInTheTemplatesAreNotSent() {
        var email = templates.render(shared(false, "Jane Doe", "Service agreement"));

        assertThat(email.html()).doesNotContain("<!--", "EmailLabels");
    }

    @Test
    void namesCantAddMarkupToTheEmail() {
        var email = templates.render(shared(false, "<b>Jane</b>", "<script>alert(1)</script>"));

        assertThat(email.html())
                .doesNotContain("<script>", "<b>Jane</b>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void theLayoutHasTheSubjectAsPreviewTheLogoAndTheFooter() {
        var email = templates.render(shared(false, "Jane Doe", "Service agreement"));

        assertThat(email.html()).startsWith("<!DOCTYPE html>");
        assertThat(email.html().split("<html", -1)).as("one html element").hasSize(2);
        assertThat(email.html())
                .contains("<title>Jane Doe shared &quot;Service agreement&quot; with you</title>")
                .contains("src=\"https://dokiments.com/icons/icon-192.png\"")
                .contains("You received this email because of activity on your")
                .contains("href=\"https://dokiments.com\"");
        assertThat(email.text()).endsWith("""
                        You received this email because of activity on your Dokiments account.
                        https://dokiments.com
                        """);
    }

    @Test
    void locallyTheLogoAndFooterStillPointAtThePublicSite() {
        // A recipient's mail client can't load images from localhost.
        var local = new EmailTemplates(URI.create("http://localhost:3000"));

        var email = local.render(shared(false, "Jane Doe", "Service agreement"));

        assertThat(email.html())
                .contains("src=\"https://www.dokiments.com/icons/icon-192.png\"")
                .doesNotContain("localhost:3000/icons");
    }

    private static EmailMessage shared(boolean updated, String senderName, String documentName) {
        return EmailMessage.of(
                "recipient@example.com",
                senderName + " shared \"" + documentName + "\" with you",
                "sharing/document-shared",
                Map.of(
                        "updated", updated,
                        "senderName", senderName,
                        "documentName", documentName,
                        "url", "https://dokiments.com/my-documents?tab=shared"));
    }
}
