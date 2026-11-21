package pt.alinha.notifications.api

/**
 * Queues a pt-PT email for the current tenant (outbox): it is stored in the caller's transaction and sent
 * asynchronously after commit, so a rolled-back change sends nothing. There is no automatic retry yet: a send that fails is logged as FAILED, and one
 * interrupted by a restart stays PENDING or SENDING. Delivery is at most once per queued email.
 * Every attempt is kept in the delivery log.
 *
 * Call it inside a `@Transactional` method of a tenant-scoped request or `TenantContext.runAs`; outside a
 * transaction it throws. [template] is a file in `email/templates/` of this plugin; every `{{param}}` it uses
 * must be in [params] (values are HTML-escaped in the body; the subject rejects line breaks).
 * An invalid [to] address throws [IllegalArgumentException].
 */
interface EmailService {
    fun send(to: String, template: String, params: Map<String, String> = emptyMap())
}
