package com.dokiments.platform.email;

/** Who receives email. Worked out from the settings by {@link EmailProperties#mode()}, never set directly. */
public enum EmailMode {
    /** No Resend key: nothing is sent. */
    DISABLED,
    /** Only the addresses on the allow list receive email. */
    RESTRICTED,
    /** Every recipient receives email. Production only. */
    LIVE
}
