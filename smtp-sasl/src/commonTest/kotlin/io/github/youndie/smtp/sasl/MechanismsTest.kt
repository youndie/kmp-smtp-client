package io.github.youndie.smtp.sasl

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mechanisms, checked against the examples printed in their own RFCs.
 *
 * A vector out of the specification is worth more than any amount of self-consistency: it is the
 * one thing that catches an implementation which is wrong in the same way twice.
 */
class MechanismsTest {
    @Test
    fun `PLAIN sends authzid then authcid then password separated by NUL`() {
        // rfc4616.txt:83: message = [authzid] UTF8NUL authcid UTF8NUL passwd
        val mechanism = PlainMechanism(username = "test", password = "secret")

        assertEquals("PLAIN", mechanism.name)
        assertContentEquals("\u0000test\u0000secret".encodeToByteArray(), mechanism.initialResponse())
        assertTrue(mechanism.isComplete)
    }

    @Test
    fun `PLAIN carries an authorization identity when one is given`() {
        val mechanism = PlainMechanism(username = "test", password = "secret", authorizationIdentity = "admin")

        assertContentEquals("admin\u0000test\u0000secret".encodeToByteArray(), mechanism.initialResponse())
    }

    @Test
    fun `PLAIN refuses a NUL inside a credential`() {
        // The separator is NUL, so a NUL in the value would silently move the field boundaries.
        assertFailsWith<SaslException> {
            PlainMechanism(
                username = "te\u0000st",
                password = "secret",
            ).initialResponse()
        }
    }

    @Test
    fun `LOGIN answers the username and password challenges in order`() {
        // LOGIN has no RFC; this is the exchange every server implements.
        val mechanism = LoginMechanism(username = "user", password = "secret")

        assertNull(mechanism.initialResponse(), "LOGIN is server-first")
        assertContentEquals("user".encodeToByteArray(), mechanism.respond("Username:".encodeToByteArray()))
        assertContentEquals("secret".encodeToByteArray(), mechanism.respond("Password:".encodeToByteArray()))
        assertTrue(mechanism.isComplete)
    }

    @Test
    fun `LOGIN gives up on a third challenge`() {
        val mechanism = LoginMechanism(username = "user", password = "secret")
        mechanism.respond("Username:".encodeToByteArray())
        mechanism.respond("Password:".encodeToByteArray())

        assertFailsWith<SaslException> { mechanism.respond("What else?".encodeToByteArray()) }
    }

    @Test
    fun `CRAM-MD5 reproduces the digest printed in RFC 2195`() {
        // rfc2195.txt:131: the exchange, rfc2195.txt:152: the expected digest.
        val mechanism = CramMd5Mechanism(username = "tim", secret = "tanstaaftanstaaf")

        val response = mechanism.respond("<1896.697170952@postoffice.reston.mci.net>".encodeToByteArray())

        assertEquals("tim b913a602c7eda7a495b4e6e7334d3890", response.decodeToString())
        assertTrue(mechanism.isComplete)
    }

    @Test
    fun `SCRAM-SHA-1 reproduces the exchange printed in RFC 5802`() {
        // rfc5802.txt:496. The nonce is fixed here because the specification fixed it; in
        // production it comes from a cryptographic source.
        val mechanism =
            ScramMechanism(
                username = "user",
                password = "pencil",
                algorithm = ScramAlgorithm.SHA_1,
                nonceSource = { "fyko+d2lbbFgONRv9qkxdawL" },
            )

        assertEquals("SCRAM-SHA-1", mechanism.name)
        assertEquals("n,,n=user,r=fyko+d2lbbFgONRv9qkxdawL", mechanism.initialResponse().decodeToString())

        val serverFirst = "r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,i=4096"
        assertEquals(
            "c=biws,r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=",
            mechanism.respond(serverFirst.encodeToByteArray()).decodeToString(),
        )

        mechanism.respond("v=rmF9pqV8S7suAoZWja4dJRkFsKQ=".encodeToByteArray())
        assertTrue(mechanism.isComplete)
    }

    @Test
    fun `SCRAM-SHA-256 reproduces the exchange printed in RFC 7677`() {
        // rfc7677.txt:126
        val mechanism =
            ScramMechanism(
                username = "user",
                password = "pencil",
                algorithm = ScramAlgorithm.SHA_256,
                nonceSource = { "rOprNGfwEbeRWgbNEkqO" },
            )

        assertEquals("SCRAM-SHA-256", mechanism.name)
        assertEquals("n,,n=user,r=rOprNGfwEbeRWgbNEkqO", mechanism.initialResponse().decodeToString())

        val serverFirst =
            "r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096"
        assertEquals(
            "c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF\$k0," +
                "p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=",
            mechanism.respond(serverFirst.encodeToByteArray()).decodeToString(),
        )

        mechanism.respond("v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=".encodeToByteArray())
        assertTrue(mechanism.isComplete)
    }

    @Test
    fun `SCRAM refuses a server nonce that does not start with the client one`() {
        // rfc5802.txt: the server nonce must extend the client's. Accepting anything else lets a
        // relay replay somebody else's exchange.
        val mechanism = scramForTest()
        mechanism.initialResponse()

        assertFailsWith<SaslException> {
            mechanism.respond("r=somebodyElsesNonce,s=QSXCR+Q6sek8bf92,i=4096".encodeToByteArray())
        }
    }

    @Test
    fun `SCRAM refuses a wrong server signature`() {
        // This is the half of SCRAM that authenticates the server. Skipping the check turns the
        // mechanism into a slower PLAIN.
        val mechanism = scramForTest()
        mechanism.initialResponse()
        mechanism.respond("r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,i=4096".encodeToByteArray())

        assertFailsWith<SaslException> { mechanism.respond("v=AAAAAAAAAAAAAAAAAAAAAAAAAAA=".encodeToByteArray()) }
    }

    @Test
    fun `SCRAM refuses a server error message`() {
        val mechanism = scramForTest()
        mechanism.initialResponse()

        assertFailsWith<SaslException> { mechanism.respond("e=unknown-user".encodeToByteArray()) }
    }

    @Test
    fun `SCRAM refuses an iteration count below the floor`() {
        // A server naming i=1 is asking for the password hash to be cheap to brute-force.
        val mechanism = scramForTest()
        mechanism.initialResponse()

        assertFailsWith<SaslException> {
            mechanism.respond("r=fyko+d2lbbFgONRv9qkxdawLxx,s=QSXCR+Q6sek8bf92,i=1".encodeToByteArray())
        }
    }

    @Test
    fun `XOAUTH2 builds the string Google documented`() {
        val mechanism = XOAuth2Mechanism(username = "user@example.com", accessToken = "ya29.token")

        assertEquals("XOAUTH2", mechanism.name)
        assertEquals(
            "user=user@example.com\u0001auth=Bearer ya29.token\u0001\u0001",
            mechanism.initialResponse().decodeToString(),
        )
    }

    @Test
    fun `OAUTHBEARER builds the GS2 message of RFC 7628`() {
        // rfc7628.txt:110: gs2-header, then key=value pairs separated by ^A.
        val mechanism =
            OAuthBearerMechanism(
                username = "user@example.com",
                accessToken = "vF9dft4qmT",
                host = "smtp.example.com",
                port = 587,
            )

        assertEquals("OAUTHBEARER", mechanism.name)
        assertEquals(
            "n,a=user@example.com,\u0001host=smtp.example.com\u0001port=587\u0001auth=Bearer vF9dft4qmT\u0001\u0001",
            mechanism.initialResponse().decodeToString(),
        )
    }

    @Test
    fun `OAUTHBEARER answers a server error with the empty message the RFC requires`() {
        // rfc7628.txt: on failure the server sends a JSON error and the client must reply with a
        // single ^A before the server can report the failure properly.
        val mechanism =
            OAuthBearerMechanism(
                username = "user@example.com",
                accessToken = "expired",
                host = "smtp.example.com",
                port = 587,
            )
        mechanism.initialResponse()

        assertContentEquals(
            "\u0001".encodeToByteArray(),
            mechanism.respond("""{"status":"invalid_token"}""".encodeToByteArray()),
        )
    }

    private fun scramForTest() =
        ScramMechanism(
            username = "user",
            password = "pencil",
            algorithm = ScramAlgorithm.SHA_1,
            nonceSource = { "fyko+d2lbbFgONRv9qkxdawL" },
        )
}
