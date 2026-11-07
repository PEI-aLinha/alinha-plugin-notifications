package pt.alinha.notifications.internal.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationsController {

    /** Walking skeleton: prova que o plugin está carregado de ponta a ponta. */
    @GetMapping("/ping")
    fun ping() = mapOf("plugin" to "notifications", "status" to "ok")
}
