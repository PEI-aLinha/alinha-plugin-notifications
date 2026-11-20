package pt.alinha.notifications.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmailTemplatesTest {
    private val templates = EmailTemplates()
    private val params = mapOf("name" to "Ana", "organization" to "Acme")

    @Test
    fun `first line of the template is the subject`() {
        assertEquals("Olá Ana", templates.render("test-greeting", params).subject)
    }

    @Test
    fun `body is wrapped in the aLinha layout`() {
        val html = templates.render("test-greeting", params).html
        assertTrue(html.contains("<p>Bem-vindo à organização Acme.</p>"))
        assertTrue(html.contains("aLinha"))
        assertTrue(html.contains("<html lang=\"pt-PT\">"))
    }

    @Test
    fun `params are html escaped`() {
        val html = templates.render("test-greeting", params + ("organization" to "<script>x</script>")).html
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;x&lt;/script&gt;"))
    }

    @Test
    fun `a missing param fails instead of sending a placeholder`() {
        assertThrows<IllegalArgumentException> { templates.render("test-greeting", mapOf("name" to "Ana")) }
    }

    @Test
    fun `an unknown template fails`() {
        assertThrows<IllegalArgumentException> { templates.render("nope", params) }
    }

    @Test
    fun `a template name cannot leave the templates folder`() {
        assertThrows<IllegalArgumentException> { templates.render("../layout", params) }
    }
}
