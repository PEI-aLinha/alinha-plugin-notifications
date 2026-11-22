package pt.alinha.notifications.internal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.core.tenant.TenantContext
import pt.alinha.notifications.api.EmailService
import java.time.Instant

/**
 * Sends a committed delivery over SMTP and records the outcome (recipe: docs/tenancy.md, rule 4).
 * A crash mid-send leaves the row SENDING and it is not resent (no retry job yet, see [EmailService]).
 */
@Component
class EmailSender(
    private val mailer: JavaMailSender,
    private val deliveries: EmailDeliveryRepository,
    private val tenants: TenantContext,
    private val tx: TransactionTemplate,
    @Value("\${alinha.notifications.from:aLinha <nao-responder@alinha.pt>}") private val from: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.EXECUTOR)
    @TransactionalEventListener
    fun on(event: EmailQueued) = tenants.runAs(event) {
        // One conditional UPDATE, so a duplicate event cannot also send this row.
        val delivery = tx.execute { if (deliveries.claim(event.deliveryId) == 1) deliveries.findById(event.deliveryId).orElse(null) else null }
            ?: return@runAs
        try {
            mailer.send(mailer.createMimeMessage().also { message ->
                MimeMessageHelper(message, "UTF-8").apply {
                    setFrom(from)
                    setTo(delivery.recipient)
                    setSubject(delivery.subject)
                    setText(delivery.html.orEmpty(), true)
                }
            })
            delivery.status = EmailStatus.SENT
            delivery.sentAt = Instant.now()
            delivery.html = null
            delivery.error = null
        } catch (e: Exception) {
            // Any failure (including MessagingException from MimeMessageHelper) must leave the row FAILED, not SENDING.
            val reason = redact("${e.javaClass.simpleName}: ${e.message}", delivery.recipient)
            log.warn("Email delivery {} failed: {}", delivery.id, reason)
            delivery.status = EmailStatus.FAILED
            delivery.error = reason
            delivery.html = null // never retried, so do not keep a body that may carry one-time tokens
        }
        tx.executeWithoutResult { deliveries.save(delivery) }
    }

    // The address is personal data and SMTP errors quote it, in any case or in part (local part, domain).
    companion object {
        const val MAX_ERROR = 500

        // Whole tokens only, so a local part like "ana" does not corrupt "banana".
        internal fun redact(message: String, recipient: String): String =
            listOf(recipient, recipient.substringBefore('@'), recipient.substringAfter('@'))
                .filter { it.length >= 3 }
                .fold(message) { text, part ->
                    Regex("""(?<![\w.+-])${Regex.escape(part)}(?![\w.+-])""", RegexOption.IGNORE_CASE)
                        .replace(text, "<recipient>")
                }
                .take(MAX_ERROR)
    }
}
