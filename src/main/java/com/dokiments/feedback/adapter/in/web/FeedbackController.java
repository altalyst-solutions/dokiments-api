package com.dokiments.feedback.adapter.in.web;

import com.dokiments.feedback.application.port.in.SendFeedback;
import com.dokiments.feedback.domain.Feedback;
import com.dokiments.feedback.domain.FeedbackMessage;
import com.dokiments.shared.SignedInUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/feedback}: feedback or a problem report from the signed-in user. Path, body, status codes and
 * messages are the web app's. There is no way to read feedback through the API: an administrator reads it in Firestore.
 */
@RestController
@Tag(name = "Feedback")
class FeedbackController {

    private static final String ERROR = "#/components/schemas/Error";

    private final SendFeedback sendFeedback;

    FeedbackController(SendFeedback sendFeedback) {
        this.sendFeedback = sendFeedback;
    }

    @PostMapping("/api/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "sendFeedback",
            summary = "Send feedback",
            description = "The message is trimmed and 1 to 2000 characters; path, the page it was sent from, at most"
                    + " 512. Who sent it comes from the session, never from the body. An account may send feedback once"
                    + " a minute.")
    @ApiResponse(responseCode = "201", description = "Kept")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "429",
            description = "Sent less than a minute after the last (\"Thanks — we just received your last message."
                    + " Please wait a moment before sending another.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    FeedbackEnvelope send(@AuthenticationPrincipal SignedInUser user, @Valid @RequestBody FeedbackRequest body) {
        var feedback = sendFeedback.send(user, FeedbackMessage.of(body.message(), body.path(), body.type()));
        return new FeedbackEnvelope(FeedbackResponse.of(feedback));
    }

    /** The feedback. Other fields are ignored, as in the web app; a path sent as {@code null} counts as left out. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record FeedbackRequest(
            @NotNull @Schema(example = "The export button does nothing.")
            String message,

            @Nullable @Schema(example = "/my-documents") String path,

            @NotNull @Schema(allowableValues = {"feedback", "problem"})
            String type) {}

    /** The answer: {@code { "feedback": {...} }}. */
    record FeedbackEnvelope(FeedbackResponse feedback) {}

    /**
     * The feedback as kept.
     *
     * @param createdAt when it was sent, in milliseconds since 1970
     * @param path the page it was sent from, empty when none was sent
     */
    record FeedbackResponse(
            long createdAt,
            String displayName,
            String email,
            @Schema(example = "feedback_mhx2k1q8_4f9a2c") String id,
            String message,
            String path,

            @Schema(allowableValues = {"feedback", "problem"})
            String type,

            String uid) {

        static FeedbackResponse of(Feedback feedback) {
            var message = feedback.message();
            return new FeedbackResponse(
                    feedback.createdAt().toEpochMilli(),
                    feedback.displayName(),
                    feedback.email(),
                    feedback.id().value(),
                    message.message(),
                    message.path(),
                    message.type().value(),
                    feedback.uid());
        }
    }
}
