package pt.alinha.notifications.internal

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

data class RenderedEmail(val subject: String, val html: String)

/**
 * pt-PT email templates in `email/templates/<name>.html`: the first line is the subject, the rest is the body,
 * wrapped in `email/layout.html`. `{{param}}` placeholders are HTML-escaped in the body and plain in the subject
 * (a header, so CR/LF is rejected); a missing one fails.
 */
@Component
class EmailTemplates {
    private val layout = read("email/layout.html") ?: error("email/layout.html is missing")

    fun render(name: String, params: Map<String, String>): RenderedEmail {
        require(NAME.matches(name)) { "Invalid email template name: $name" }
        val template = read("email/templates/$name.html") ?: throw IllegalArgumentException("Unknown email template: $name")
        val subject = fill(template.substringBefore('\n').removeSuffix("\r"), params, html = false)
        require('\r' !in subject && '\n' !in subject) { "Email subject cannot contain line breaks" }
        val body = fill(template.substringAfter('\n'), params, html = true)
        return RenderedEmail(subject, layout.replace("{{subject}}", escape(subject)).replace("{{content}}", body))
    }

    private fun fill(text: String, params: Map<String, String>, html: Boolean): String = PLACEHOLDER.replace(text) {
        val key = it.groupValues[1]
        val value = params[key] ?: throw IllegalArgumentException("Missing email param: $key")
        if (html) escape(value) else value
    }

    private fun escape(value: String) = HtmlUtils.htmlEscape(value, "UTF-8")

    private fun read(path: String): String? =
        ClassPathResource(path).takeIf { it.exists() }?.inputStream?.use { it.readAllBytes().toString(Charsets.UTF_8) }

    private companion object {
        val NAME = Regex("[a-z0-9-]+")
        val PLACEHOLDER = Regex("""\{\{(\w+)}}""")
    }
}
