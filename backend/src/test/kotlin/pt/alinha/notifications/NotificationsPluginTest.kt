package pt.alinha.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationsPluginTest {
    @Test
    fun `manifest tem o id do plugin`() {
        assertEquals("notifications", NotificationsPlugin().manifest.id)
    }
}
