package com.dokiments.newsletter.adapter.in.web;

import com.dokiments.newsletter.application.port.in.SubscribeToNewsletter;
import com.dokiments.newsletter.application.port.in.SubscribeToNewsletter.SubscribeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/newsletter}: public newsletter sign-up from the website, before a visitor has an account.
 *
 * <p>Answers {@code 200 {"status": "subscribed"}}, also when the address was already subscribed, so the response
 * doesn't reveal who is on the list. An address that isn't plausible is {@code 400 Invalid request body.}
 */
@RestController
class NewsletterController {

    private final SubscribeToNewsletter subscribeToNewsletter;

    NewsletterController(SubscribeToNewsletter subscribeToNewsletter) {
        this.subscribeToNewsletter = subscribeToNewsletter;
    }

    @PostMapping("/api/newsletter")
    SubscribeResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        subscribeToNewsletter.subscribe(new SubscribeCommand(request.email()));
        return new SubscribeResponse("subscribed");
    }

    /** The request body. Other fields are ignored, as in the web app. */
    record SubscribeRequest(@NotNull String email) {}

    /** The answer: always {@code subscribed}, also for an address that was already on the list. */
    record SubscribeResponse(String status) {}
}
