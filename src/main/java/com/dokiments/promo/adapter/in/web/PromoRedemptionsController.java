package com.dokiments.promo.adapter.in.web;

import com.dokiments.promo.application.port.in.ManagePromoRedemptions;
import com.dokiments.promo.domain.PromoCode;
import com.dokiments.promo.domain.PromoRedemption;
import com.dokiments.shared.SignedInUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in account's promo codes, for the promotion card in the settings. Paths, bodies, status codes and messages
 * are the web app's. A code can also be given while signing up or in; see {@code com.dokiments.promo.PromoAtSignIn}.
 */
@RestController
@Tag(name = "Promo codes")
class PromoRedemptionsController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManagePromoRedemptions redemptions;

    PromoRedemptionsController(ManagePromoRedemptions redemptions) {
        this.redemptions = redemptions;
    }

    @GetMapping("/api/promo-redemptions")
    @Operation(
            operationId = "listPromoRedemptions",
            summary = "List the redeemed promo codes",
            description = "Every promotion the account has redeemed, also ones that have ended. The web app picks out"
                    + " the one it shows by promoId.")
    @ApiResponse(responseCode = "200", description = "The redemptions, an empty list for an account with none")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    RedemptionsResponse list(@AuthenticationPrincipal SignedInUser user) {
        return new RedemptionsResponse(redemptions.list(user.uid()).stream()
                .map(RedemptionResponse::of)
                .toList());
    }

    @PostMapping("/api/promo-redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "redeemPromoCode",
            summary = "Redeem a promo code",
            description = "At most once per account. Matching ignores case and spaces. The redemption keeps the"
                    + " promotion's own code, not what was typed.")
    @ApiResponse(responseCode = "201", description = "Redeemed")
    @ApiResponse(
            responseCode = "400",
            description = "A code that matches no promotion (\"Invalid promo code.\"), or an invalid body",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "409",
            description = "Redeemed already (\"This promo code has already been used for your account.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    RedemptionEnvelope redeem(@AuthenticationPrincipal SignedInUser user, @Valid @RequestBody RedeemRequest body) {
        return new RedemptionEnvelope(RedemptionResponse.of(redemptions.redeem(user.uid(), body.promoCode())));
    }

    /**
     * The code to redeem, as typed. It isn't trimmed here: a code of only spaces is a wrong code ({@code Invalid promo
     * code.}), not an invalid body, as in the web app.
     */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record RedeemRequest(
            @NotEmpty @Size(max = PromoCode.MAX_LENGTH) @Schema(example = "ViSHDOK2026!")
            String promoCode) {}

    /** The answer to listing: {@code { "redemptions": [...] }}. */
    record RedemptionsResponse(List<RedemptionResponse> redemptions) {}

    /** The answer to redeeming: {@code { "redemption": {...} }}. */
    record RedemptionEnvelope(RedemptionResponse redemption) {}

    /**
     * A redemption.
     *
     * @param redeemedAt when it was redeemed, in milliseconds since 1970
     */
    record RedemptionResponse(
            String code, @Schema(example = "launch-2026") String promoId, long redeemedAt) {

        static RedemptionResponse of(PromoRedemption redemption) {
            return new RedemptionResponse(
                    redemption.code(),
                    redemption.promoId(),
                    redemption.redeemedAt().toEpochMilli());
        }
    }
}
