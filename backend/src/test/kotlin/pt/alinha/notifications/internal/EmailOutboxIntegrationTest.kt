package pt.alinha.notifications.internal

import jakarta.mail.Address
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import pt.alinha.core.audit.internal.AuditActor
import pt.alinha.core.audit.internal.JpaAuditTrail
import pt.alinha.core.tenant.TenantContext
import pt.alinha.core.tenant.internal.HibernateTenantResolver
import pt.alinha.core.tenant.internal.JwtTenantContext
import pt.alinha.core.tenant.internal.ScopedCorrelationContext
import pt.alinha.notifications.api.EmailService
import pt.alinha.pluginapi.TenantId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Testcontainers
// No web environment: runAs refuses inside a request.
@SpringBootTest(classes = [EmailTestApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EmailOutboxIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")

        @DynamicPropertySource
        @JvmStatic
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.locations") { "classpath:db/migration/core,classpath:db/migration/notifications" }
        }
    }

    @Autowired private lateinit var emails: EmailService
    @Autowired private lateinit var deliveries: EmailDeliveryRepository
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var mailer: FakeMailSender

    private val tenant = TenantId(UUID.randomUUID())
    private val params = mapOf("name" to "Ana", "organization" to "Acme")

    private fun <T> asTenant(block: () -> T): T = tenants.runAs(tenant) { tx.execute { block() } as T }

    private fun deliveryTo(to: String): EmailDelivery? = asTenant { deliveries.findAll().singleOrNull { it.recipient == to } }

    private fun awaitStatus(to: String, status: EmailStatus): EmailDelivery {
        repeat(100) {
            deliveryTo(to)?.takeIf { it.status == status }?.let { return it }
            Thread.sleep(100)
        }
        error("No $status delivery to $to; got ${deliveryTo(to)?.status}")
    }

    @Test
    fun `a queued email is sent after commit and logged as sent`() {
        val to = "ana-${UUID.randomUUID()}@example.pt"
        asTenant { emails.send(to, "test-greeting", params) }

        val delivery = awaitStatus(to, EmailStatus.SENT)
        val message = mailer.sent.single { it.allRecipients.map(Address::toString) == listOf(to) }
        assertEquals("Olá Ana", message.subject)
        assertEquals("test-greeting", delivery.template)
        assertTrue(delivery.sentAt != null)
    }

    @Test
    fun `queueing is audited in the caller's transaction`() {
        val to = "audit-${UUID.randomUUID()}@example.pt"
        asTenant { emails.send(to, "test-greeting", params) }

        val id = awaitStatus(to, EmailStatus.SENT).id.toString()
        val count = jdbc.queryForObject(
            "select count(*) from audit.audit_event where action = 'email.queue' and entity_id = ?", Int::class.java, id,
        )
        assertEquals(1, count)
    }

    @Test
    fun `a rolled back transaction neither logs nor sends the email`() {
        val to = "rollback-${UUID.randomUUID()}@example.pt"
        assertThrows<IllegalStateException> {
            asTenant { emails.send(to, "test-greeting", params); error("caller fails after queueing") }
        }

        // No row means the sender has nothing to send, so this needs no waiting.
        assertEquals(null, deliveryTo(to))
        assertTrue(mailer.sent.none { it.allRecipients.map(Address::toString) == listOf(to) })
    }

    @Test
    fun `a failed send is logged as failed with the reason`() {
        val to = "${FakeMailSender.REJECTED}-${UUID.randomUUID()}@example.pt"
        asTenant { emails.send(to, "test-greeting", params) }

        val delivery = awaitStatus(to, EmailStatus.FAILED)
        assertTrue(delivery.error!!.contains("SMTP down"))
        assertFalse(delivery.error!!.contains(to), "the address is personal data")
    }

    @Test
    fun `an unexpected send error is logged as failed instead of staying pending`() {
        val to = "${FakeMailSender.BROKEN}-${UUID.randomUUID()}@example.pt"
        asTenant { emails.send(to, "test-greeting", params) }

        assertTrue(awaitStatus(to, EmailStatus.FAILED).error!!.contains("IllegalStateException"))
    }

    @Test
    fun `a malformed address fails the caller and queues nothing`() {
        assertThrows<IllegalArgumentException> { asTenant { emails.send("not an address", "test-greeting", params) } }
        assertEquals(null, deliveryTo("not an address"))
    }

    @Test
    fun `another tenant cannot see the delivery log`() {
        val to = "isolated-${UUID.randomUUID()}@example.pt"
        // SENT is read back under this tenant, so the async send also ran under the event's tenant.
        val id = asTenant { emails.send(to, "test-greeting", params) }.let { awaitStatus(to, EmailStatus.SENT).id }

        val other = TenantId(UUID.randomUUID())
        tenants.runAs(other) {
            tx.executeWithoutResult {
                assertTrue(deliveries.findAll().none { it.id == id })
                assertTrue(deliveries.findById(id).isEmpty)
            }
        }
    }

    @Test
    fun `sending outside a transaction fails so an email is never fire-and-forget`() {
        assertThrows<IllegalTransactionStateException> {
            tenants.runAs(tenant) { emails.send("x@example.pt", "test-greeting", params) }
        }
    }
}

/** Captures messages instead of talking SMTP; [REJECTED] recipients fail like a down server, [BROKEN] ones with a non-mail error. */
@Component
class FakeMailSender : JavaMailSenderImpl() {
    val sent = CopyOnWriteArrayList<MimeMessage>()

    override fun doSend(mimeMessages: Array<out MimeMessage>, originalMessages: Array<out Any>?) {
        mimeMessages.forEach { message ->
            val to = message.allRecipients.single().toString()
            if (to.startsWith(REJECTED)) throw MailSendException("SMTP down for $to")
            if (to.startsWith(BROKEN)) throw IllegalStateException("not a MailException")
            sent += message
        }
    }

    companion object {
        const val REJECTED = "rejected"
        const val BROKEN = "broken"
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
// Entities and repositories of the core (audit) and of this plugin.
@AutoConfigurationPackage(basePackages = ["pt.alinha.core", "pt.alinha.notifications"])
@Import(
    JwtTenantContext::class, HibernateTenantResolver::class, ScopedCorrelationContext::class,
    AuditActor::class, JpaAuditTrail::class,
    AsyncConfig::class, EmailTemplates::class, OutboxEmailService::class, EmailSender::class, FakeMailSender::class,
)
class EmailTestApplication
