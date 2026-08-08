package io.github.youndie.smtp.protocol

/**
 * The server sent something the protocol does not allow: an unparseable reply, an over-long line,
 * different codes on the lines of one reply.
 *
 * This is not a refusal by the server. A refusal arrives as an ordinary reply with a 4xx or 5xx
 * code and is parsed without exceptions. Refusal classification lands in M-26.
 */
public class SmtpProtocolException(
    message: String,
) : RuntimeException(message)
