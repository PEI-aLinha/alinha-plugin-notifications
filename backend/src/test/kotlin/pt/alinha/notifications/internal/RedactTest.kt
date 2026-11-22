package pt.alinha.notifications.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedactTest {
    @Test
    fun `redacts the address and its parts but not words that contain them`() {
        assertEquals(
            "550 <recipient> unknown, user <recipient> at <recipient>; banana",
            EmailSender.redact("550 Ana@Acme.pt unknown, user ana at acme.pt; banana", "ana@acme.pt"),
        )
    }
}
