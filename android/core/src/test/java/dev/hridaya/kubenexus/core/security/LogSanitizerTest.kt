package dev.hridaya.kubenexus.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `sanitize redacts bearer tokens and jwt tokens`() {
        val input =
            "Error: token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzIn0 failed"
        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("eyJhbGciOiJSUzI1Ni"))
        assertTrue(output.contains("token: [REDACTED]"))
    }

    @Test
    fun `sanitize redacts authorization bearer header`() {
        val input = "Request with Bearer ya29.a0AfH6SMD_123456 failed with 401"
        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("ya29.a0AfH6SMD_123456"))
        assertTrue(output.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `sanitize redacts client-certificate-data and client-key-data`() {
        val input = """
            client-certificate-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCg==
            client-key-data: LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQo=
            certificate-authority-data: LS0tLS1CRUdJTiBDQVRFLS0tLS0=
        """.trimIndent()

        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCg=="))
        assertFalse(output.contains("LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQo="))
        assertFalse(output.contains("LS0tLS1CRUdJTiBDQVRFLS0tLS0="))

        assertTrue(output.contains("client-certificate-data: [REDACTED]"))
        assertTrue(output.contains("client-key-data: [REDACTED]"))
        assertTrue(output.contains("certificate-authority-data: [REDACTED]"))
    }

    @Test
    fun `sanitize redacts raw PEM blocks`() {
        val input = """
            Failed with key:
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA0m4w8hZ8x...
            -----END RSA PRIVATE KEY-----
            and cert:
            -----BEGIN CERTIFICATE-----
            MIIDIDCCAigCCQDF...
            -----END CERTIFICATE-----
        """.trimIndent()

        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("BEGIN RSA PRIVATE KEY"))
        assertFalse(output.contains("BEGIN CERTIFICATE"))
        assertTrue(output.contains("[REDACTED PEM BLOCK]"))
    }

    @Test
    fun `sanitize redacts passwords and basic auth`() {
        val input = "Connecting with password: secretPass123! and Authorization: Basic dXNlcjpwYXNz"
        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("secretPass123!"))
        assertFalse(output.contains("dXNlcjpwYXNz"))
        assertTrue(output.contains("password: [REDACTED]"))
        assertTrue(output.contains("Basic [REDACTED]"))
    }

    @Test
    fun `sanitize redacts embedded url userinfo credentials`() {
        val input = "Connecting to https://admin:superSecret@k8s.example.com:6443/version"
        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("superSecret"))
        assertTrue(output.contains("https://admin:[REDACTED]@k8s.example.com:6443/version"))
    }

    @Test
    fun `sanitize preserves harmless diagnostic messages`() {
        val input = "Pod nginx-7854ff88-abc is in Running state on node worker-1"
        val output = LogSanitizer.sanitize(input)

        assertEquals(input, output)
    }

    @Test
    fun `sanitize handles null and empty inputs safely`() {
        assertEquals("", LogSanitizer.sanitize(null))
        assertEquals("", LogSanitizer.sanitize(""))
    }

    @Test
    fun `sanitize redacts json and key=value credentials`() {
        val input =
            "{\"token\":\"abc123\",\"user\":\"x\"} --token=t0ps3cret password=hunter2&next=1 " +
                    "\"client_secret\": \"s3\""
        val output = LogSanitizer.sanitize(input)

        assertEquals(
            """{"token":"[REDACTED]","user":"x"} --token=[REDACTED] password=[REDACTED]&next=1 "client_secret": "[REDACTED]"""",
            output,
        )
    }

    @Test
    fun `sanitize redacts a value whose closing quote was cut off`() {
        val output = LogSanitizer.sanitize("config {\"api_key\": \"k-9f8e7d")

        assertEquals("config {\"api_key\": \"[REDACTED]", output)
    }

    @Test
    fun `sanitize redacts a quoted value containing spaces in full`() {
        val output = LogSanitizer.sanitize("""password: "correct horse battery"""")

        assertEquals("""password: "[REDACTED]"""", output)
    }

    @Test
    fun `sanitize redacts bare jwts aws key ids and base64 pem`() {
        val jwt =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ.eyJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQifQ.c2lnbmF0dXJl"
        val input =
            "sa=$jwt key=AKIAIOSFODNN7EXAMPLE tls.key: LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCg=="
        val output = LogSanitizer.sanitize(input)

        assertFalse(output.contains("eyJzdWIi"))
        assertFalse(output.contains("AKIAIOSFODNN7EXAMPLE"))
        assertFalse(output.contains("LS0tLS1CRUdJ"))
        assertTrue(output.contains("[REDACTED JWT]"))
        assertTrue(output.contains("[REDACTED AWS KEY ID]"))
        assertTrue(output.contains("tls.key: [REDACTED BASE64 PEM]"))
    }

    @Test
    fun `sanitize redacts userinfo credentials for any url scheme`() {
        val output =
            LogSanitizer.sanitize("dial postgres://app:pa55w0rd@db:5432/app and REDIS://u:p@cache:6379")

        assertEquals(
            "dial postgres://app:[REDACTED]@db:5432/app and REDIS://u:[REDACTED]@cache:6379",
            output
        )
    }

    @Test
    fun `sanitize leaves ordinary kubernetes messages alone`() {
        val inputs = listOf(
            "secretName: db-creds",
            "tokens: 5 used",
            "MountVolume.SetUp failed for volume \"creds\" : secret \"db\" not found",
            "token_count=12",
        )
        inputs.forEach { assertEquals(it, LogSanitizer.sanitize(it)) }
    }

    @Test
    fun `sanitize is idempotent`() {
        val inputs = listOf(
            "token: abc",
            "{\"password\":\"p w\"}",
            "Bearer abc.def",
            "https://u:p@h/x",
            "client-key-data: LS0tLS1CRUdJTiBSU0E=",
        )
        inputs.forEach { input ->
            val once = LogSanitizer.sanitize(input)
            assertEquals(once, LogSanitizer.sanitize(once))
        }
    }

    @Test
    fun `withStackTrace sanitizes the throwable's message and causes`() {
        val cause = IllegalStateException("upstream said token: cause-secret")
        val error = RuntimeException("request failed with Bearer abc.def.ghi", cause)

        val output = LogSanitizer.withStackTrace("Loading pods failed", error)

        assertTrue(output.startsWith("Loading pods failed\n"))
        assertTrue(output.contains("java.lang.RuntimeException"))
        assertTrue(output.contains("Caused by: java.lang.IllegalStateException"))
        assertFalse(output.contains("cause-secret"))
        assertFalse(output.contains("abc.def.ghi"))
    }

    @Test
    fun `sanitize redacts secrets whose names carry a prefix`() {
        val input = """
            DB_PASSWORD=hunter2
            GITHUB_TOKEN=ghp_abcdef123456
            MYSQL_ROOT_PASSWORD: rootpass
            dbPassword=camelcase
            x-api-key: abc123
            "stripe_secret": "sk_live_42"
        """.trimIndent()

        val output = LogSanitizer.sanitize(input)

        listOf(
            "hunter2",
            "ghp_abcdef123456",
            "rootpass",
            "camelcase",
            "abc123",
            "sk_live_42"
        ).forEach {
            assertFalse("'$it' was not redacted in:\n$output", output.contains(it))
        }
        assertTrue(output.contains("DB_PASSWORD=[REDACTED]"))
        assertTrue(output.contains("\"stripe_secret\": \"[REDACTED]\""))
    }

    @Test
    fun `sanitize leaves names that only contain a secret word alone`() {
        val input =
            "secretName: app-credentials passwordless=true max_tokens=100 PASSWORD_FILE=/run/pw"

        assertEquals(input, LogSanitizer.sanitize(input))
    }
}
