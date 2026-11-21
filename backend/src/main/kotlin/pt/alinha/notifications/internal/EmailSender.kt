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
import java.time.Instant

/** Sends a committed delivery over SMTP and records the outcome (recipe: docs/tenancy.md, rule 4). */
@Component
class EmailSender(
    private val mailer: JavaMailSender,
    private val deliveries: EmailDeliveryRepository,
    private val tenants: TenantContext,
    private val tx: TransactionTemplate,
    @Value("\${alinha.notifications.from:aLinha <nao-responder@alinha.pt>}") private val from: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener
    fun on(event: EmailQueued) = tenants.runAs(event) {
        // Idempotent: a republished event for a delivery already handled does nothing.
        val delivery = tx.execute { deliveries.findById(event.deliveryId).orElse(null) }
            ?.takeIf { it.status == EmailStatus.PENDING } ?: return@runAs
        try {
            mailer.send(mailer.createMimeMessage().also { message ->
                MimeMessageHelper(message, "UTF-8").apply {
                    setFrom(from)
                    setTo(delivery.recipient)
                    setSubject(delivery.subject)
                    setText(delivery.html, true)
                }
            })
            delivery.status = EmailStatus.SENT
            delivery.sentAt = Instant.now()
        } catch (e: Exception) {
            // Any failure (including MessagingException from MimeMessageHelper) must leave the row FAILED, not PENDING.
            // The address is personal data and SMTP errors often quote it: redact it before logging or storing.
            val reason = "${e.javaClass.simpleName}: ${e.message}".replace(delivery.recipient, "<recipient>")
            log.warn("Email delivery {} failed: {}", delivery.id, reason)
            delivery.status = EmailStatus.FAILED
            delivery.error = reason
        }
        tx.executeWithoutResult { deliveries.save(delivery) }
    }
}
