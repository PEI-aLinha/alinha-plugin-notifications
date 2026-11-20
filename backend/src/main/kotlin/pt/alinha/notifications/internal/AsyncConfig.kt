package pt.alinha.notifications.internal

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

// shortcut: nothing else in the app enables @Async, which the tenancy listener recipe needs; move to the core when a second plugin needs it.
@Configuration
@EnableAsync
class AsyncConfig
