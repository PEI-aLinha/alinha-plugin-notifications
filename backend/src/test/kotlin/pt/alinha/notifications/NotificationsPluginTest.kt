package pt.alinha.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationsPluginTest {
    @Test
    fun `manifest has the plugin id`() {
        assertEquals("notifications", NotificationsPlugin().manifest.id)
    }
}
