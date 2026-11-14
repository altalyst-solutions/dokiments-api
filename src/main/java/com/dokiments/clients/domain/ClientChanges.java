package com.dokiments.clients.domain;

import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A change to some of a client's details. A missing field ({@code null}) is left as it is, so correcting a phone number
 * doesn't need every other field again. A field that is given follows the same rules as for a new client.
 *
 * @param address the new postal address, or {@code null} to keep it
 * @param brn the new business registration number, or {@code null} to keep it
 * @param companyName the new company name, or {@code null} to keep it
 * @param email the new contact address, or {@code null} to keep it
 * @param name the new contact name, or {@code null} to keep it
 * @param nationalId the new national ID number, or {@code null} to keep it
 * @param phone the new phone number, or {@code null} to keep it
 */
public record ClientChanges(
        @Nullable String address,
        @Nullable String brn,
        @Nullable String companyName,
        @Nullable String email,
        @Nullable String name,
        @Nullable String nationalId,
        @Nullable String phone) {

    /**
     * Changes as typed, checked as the web app checks them. At least one field must be given, so an empty change is
     * refused instead of only touching the time of the last edit.
     *
     * @throws InvalidClientException if a rule is broken, or nothing is given
     */
    public static ClientChanges of(
            @Nullable String address,
            @Nullable String brn,
            @Nullable String companyName,
            @Nullable String email,
            @Nullable String name,
            @Nullable String nationalId,
            @Nullable String phone) {
        if (Stream.of(address, brn, companyName, email, name, nationalId, phone).allMatch(Objects::isNull)) {
            throw new InvalidClientException();
        }
        return new ClientChanges(
                address == null ? null : ClientDetails.optional(address, ClientDetails.MAX_ADDRESS),
                brn == null ? null : ClientDetails.optional(brn, ClientDetails.MAX_IDENTIFIER),
                companyName == null ? null : ClientDetails.required(companyName, ClientDetails.MAX_NAME),
                email == null ? null : ClientDetails.email(email),
                name == null ? null : ClientDetails.required(name, ClientDetails.MAX_NAME),
                nationalId == null ? null : ClientDetails.optional(nationalId, ClientDetails.MAX_IDENTIFIER),
                phone == null ? null : ClientDetails.required(phone, ClientDetails.MAX_PHONE));
    }

    /** The details with these changes made. */
    public ClientDetails applyTo(ClientDetails details) {
        return new ClientDetails(
                address != null ? address : details.address(),
                brn != null ? brn : details.brn(),
                companyName != null ? companyName : details.companyName(),
                email != null ? email : details.email(),
                name != null ? name : details.name(),
                nationalId != null ? nationalId : details.nationalId(),
                phone != null ? phone : details.phone());
    }
}
