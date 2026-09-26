package dev.hridaya.kubenexus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDraftTest {

    private fun validDraft() = ServiceDraft(
        name = "nginx",
        namespace = "default",
        selectorApp = "web",
        port = 80,
        targetPort = 8080,
    )

    @Test
    fun `valid draft has no errors`() {
        assertTrue(validDraft().validate().isEmpty())
    }

    @Test
    fun `blank required fields are reported individually`() {
        val errors = ServiceDraft().validate()

        assertEquals(setOf("name", "namespace", "selectorApp", "port", "targetPort"), errors.keys)
        assertTrue(errors["name"]!!.contains("required"))
        assertTrue(errors["namespace"]!!.contains("required"))
        assertTrue(errors["selectorApp"]!!.contains("required"))
    }

    @Test
    fun `name must be a dns1035 label`() {
        val draft = validDraft().copy(name = "Web_Server")

        assertEquals(1, draft.validate().size)
        assertTrue(draft.validate().containsKey("name"))
    }

    @Test
    fun `name cannot start or end with a hyphen`() {
        assertTrue(validDraft().copy(name = "-nginx").validate().containsKey("name"))
        assertTrue(validDraft().copy(name = "nginx-").validate().containsKey("name"))
    }

    @Test
    fun `name longer than 63 characters is rejected`() {
        val longName = "a".repeat(64)

        val errors = validDraft().copy(name = longName).validate()

        assertTrue(errors["name"]!!.contains("63"))
    }

    @Test
    fun `single character names are valid`() {
        assertTrue(validDraft().copy(name = "a").validate().isEmpty())
        assertTrue(validDraft().copy(name = "a-b-c").validate().isEmpty())
    }

    @Test
    fun `namespace follows dns1123 labels and may start with a digit`() {
        assertTrue(validDraft().copy(namespace = "team-42").validate().isEmpty())
        assertTrue(validDraft().copy(namespace = "-team").validate().containsKey("namespace"))
        assertTrue(validDraft().copy(namespace = "Team").validate().containsKey("namespace"))
    }

    @Test
    fun `selector must be a label value`() {
        listOf("-web", "web-", "web frontend", "web/frontend", "a".repeat(64)).forEach { value ->
            val errors = validDraft().copy(selectorApp = value).validate()

            assertEquals(value, setOf("selectorApp"), errors.keys)
        }
    }

    // Label values are not DNS names: uppercase, underscores and dots are all allowed.
    @Test
    fun `selector accepts label values that are not dns labels`() {
        listOf("Web.Frontend", "MyApp", "my_app", "web.v2", "a".repeat(63)).forEach { value ->
            assertEquals(value, emptyMap<String, String>(), validDraft().copy(selectorApp = value).validate())
        }
    }

    // Unlike Service names, selectors may target any app label, including
    // digit-led DNS-1123 values.
    @Test
    fun `selector may start with a digit like any app label`() {
        assertTrue(validDraft().copy(selectorApp = "web-frontend").validate().isEmpty())
        assertTrue(validDraft().copy(selectorApp = "42-web").validate().isEmpty())
    }

    // Unlike the Pod draft's omittable container port, a Service port is the
    // thing being exposed, so zero is simply out of range.
    @Test
    fun `port must be within the valid range`() {
        assertTrue(validDraft().copy(port = -1).validate().containsKey("port"))
        assertTrue(validDraft().copy(port = 0).validate().containsKey("port"))
        assertTrue(validDraft().copy(port = 65536).validate().containsKey("port"))
        assertEquals(
            emptyMap<String, String>(),
            validDraft().copy(port = 1).validate(),
        )
        assertEquals(
            emptyMap<String, String>(),
            validDraft().copy(port = 65535).validate(),
        )
    }

    @Test
    fun `targetPort must be within the valid range`() {
        assertTrue(validDraft().copy(targetPort = -1).validate().containsKey("targetPort"))
        assertTrue(validDraft().copy(targetPort = 65536).validate().containsKey("targetPort"))
        assertEquals(
            emptyMap<String, String>(),
            validDraft().copy(targetPort = 65535).validate(),
        )
    }

    @Test
    fun `service type must be one of the supported types`() {
        val draft = validDraft().copy(serviceType = "ExternalName")

        assertEquals(1, draft.validate().size)
        assertTrue(draft.validate().containsKey("serviceType"))
    }

    @Test
    fun `every listed service type validates`() {
        ServiceDraft.TYPES.forEach { type ->
            assertEquals(
                emptyMap<String, String>(),
                validDraft().copy(serviceType = type).validate(),
            )
        }
    }
}
