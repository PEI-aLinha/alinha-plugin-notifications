package pt.alinha.notifications.internal

import jakarta.mail.internet.InternetAddress
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.notifications.api.EmailService
import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

/** Internal: tells [EmailSender] that a delivery was committed. Ids only (docs/events.md). */
data class EmailQueued(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val deliveryId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

@Service
class OutboxEmailService(
    private val templates: EmailTemplates,
    private val deliveries: EmailDeliveryRepository,
    private val audit: AuditTrail,
    private val events: ApplicationEventPublisher,
    private val tenants: TenantContext,
    private val correlation: CorrelationContext,
) : EmailService {

    // Rendered now, in the caller's thread, so a bad template or a missing param fails the caller's change.
    @Transactional(propagation = Propagation.MANDATORY)
    override fun send(to: String, template: String, params: Map<String, String>) {
        require(runCatching { InternetAddress(to, true) }.isSuccess) { "Invalid email address" } // no address: personal data
        val email = templates.render(template, params)
        val delivery = deliveries.save(EmailDelivery(recipient = to, template = template, subject = email.subject, html = email.html))
        audit.record("email.queue", "email", delivery.id.toString(), mapOf("template" to template))
        events.publishEvent(EmailQueued(tenants.current(), correlation.current(), delivery.id))
    }
}
