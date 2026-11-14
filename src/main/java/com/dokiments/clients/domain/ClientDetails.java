package com.dokiments.clients.domain;

import com.dokiments.shared.EmailAddresses;
import com.dokiments.shared.TypedText;

/**
 * What a user keeps about one of their clients, to fill in documents. Every field is always present: the optional ones
 * are empty text rather than missing, so a client has one shape everywhere it is read.
 *
 * <p>The record itself accepts any values, because clients stored before these rules existed must still be readable.
 * Details as typed go through {@link #of}, which applies the web app's rules.
 *
 * @param address the postal address, optional
 * @param brn the business registration number, optional
 * @param companyName the company's name
 * @param email the contact address
 * @param name the contact's name
 * @param nationalId the contact's national ID number, optional
 * @param phone the contact's phone number
 */
public record ClientDetails(
        String address, String brn, String companyName, String email, String name, String nationalId, String phone) {

    // The web app's limits. They stop oversized values rather than constrain real use.
    static final int MAX_ADDRESS = 500;
    static final int MAX_IDENTIFIER = 100;
    static final int MAX_NAME = 200;
    static final int MAX_PHONE = 50;

    /**
     * Details as typed, checked as the web app checks them: every field trimmed and within its limit, the name, company
     * name and phone not empty, and the address a plausible email address.
     *
     * @throws InvalidClientException if a rule is broken
     */
    public static ClientDetails of(
            String address,
            String brn,
            String companyName,
            String email,
            String name,
            String nationalId,
            String phone) {
        return new ClientDetails(
                optional(address, MAX_ADDRESS),
                optional(brn, MAX_IDENTIFIER),
                required(companyName, MAX_NAME),
                email(email),
                required(name, MAX_NAME),
                optional(nationalId, MAX_IDENTIFIER),
                required(phone, MAX_PHONE));
    }

    static String optional(String typed, int max) {
        String trimmed = TypedText.trim(typed);
        if (trimmed.length() > max) {
            throw new InvalidClientException();
        }
        return trimmed;
    }

    static String required(String typed, int max) {
        String trimmed = optional(typed, max);
        if (trimmed.isEmpty()) {
            throw new InvalidClientException();
        }
        return trimmed;
    }

    /** The address trimmed but kept as typed otherwise: it is printed on documents the way the user wrote it. */
    static String email(String typed) {
        String trimmed = EmailAddresses.trim(typed);
        if (EmailAddresses.isImplausible(trimmed)) {
            throw new InvalidClientException();
        }
        return trimmed;
    }
}
