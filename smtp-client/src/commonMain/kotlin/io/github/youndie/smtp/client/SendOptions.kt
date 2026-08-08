package io.github.youndie.smtp.client

/** How the body is encoded — `docs/rfc/rfc6152.txt`, `docs/rfc/rfc3030.txt`. */
public enum class BodyEncoding(
    internal val parameterValue: String,
) {
    /** `BODY=7BIT` — the default, stated only when something else was negotiated. */
    SEVEN_BIT("7BIT"),

    /** `BODY=8BITMIME` — `docs/rfc/rfc6152.txt`. */
    EIGHT_BIT("8BITMIME"),
}

/** When the sender wants to hear about the fate of a message — `docs/rfc/rfc3461.txt`. */
public enum class DsnNotify {
    SUCCESS,
    FAILURE,
    DELAY,

    /** Exclusive: it cannot be combined with the others (`docs/rfc/rfc3461.txt:401`). */
    NEVER,
}

/** Delivery status notification request — `docs/rfc/rfc3461.txt`. */
public class DeliveryStatusRequest(
    public val notify: Set<DsnNotify> = emptySet(),
    public val returnFullMessage: Boolean = false,
    public val envelopeId: String? = null,
    public val originalRecipient: Boolean = false,
) {
    init {
        require(!(DsnNotify.NEVER in notify && notify.size > 1)) {
            "NOTIFY=NEVER cannot be combined with other values (docs/rfc/rfc3461.txt:401)"
        }
    }
}

/** Everything that turns into ESMTP parameters on `MAIL FROM` and `RCPT TO`. */
public data class SendOptions(
    public val declaredSize: Long? = null,
    public val bodyEncoding: BodyEncoding? = null,
    public val internationalized: Boolean = false,
    public val deliveryStatus: DeliveryStatusRequest? = null,
    /**
     * Send the transaction as one group where the server offers `PIPELINING`
     * (`docs/rfc/rfc2920.txt`). Turning it off costs a round trip per recipient.
     */
    public val pipelining: Boolean = true,
    /**
     * Use `BDAT` instead of `DATA` where the server offers `CHUNKING`
     * (`docs/rfc/rfc3030.txt`). The body then travels verbatim: no dot-stuffing, no terminator.
     */
    public val chunking: Boolean = false,
)
