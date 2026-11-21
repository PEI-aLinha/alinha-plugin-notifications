package pt.alinha.notifications.internal

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import pt.alinha.core.tenant.TenantOwnedEntity
import java.time.Instant
import java.util.UUID

enum class EmailStatus { PENDING, SENDING, SENT, FAILED }

/** Outbox row and delivery log entry of one email. */
@Entity
@Table(schema = "notifications", name = "email_delivery")
class EmailDelivery(
    @Id val id: UUID = UUID.randomUUID(),
    val recipient: String = "",
    val template: String = "",
    val subject: String = "",
    var html: String? = null,
    @Enumerated(EnumType.STRING) var status: EmailStatus = EmailStatus.PENDING,
    var error: String? = null,
    val createdAt: Instant = Instant.now(),
    var sentAt: Instant? = null,
) : TenantOwnedEntity()

interface EmailDeliveryRepository : JpaRepository<EmailDelivery, UUID> {
    /** Atomic claim: 1 if this caller now owns the send, 0 if it was already claimed or handled. */
    @Modifying(clearAutomatically = true)
    @Query("update EmailDelivery d set d.status = pt.alinha.notifications.internal.EmailStatus.SENDING where d.id = :id and d.status = pt.alinha.notifications.internal.EmailStatus.PENDING")
    fun claim(id: UUID): Int
}
