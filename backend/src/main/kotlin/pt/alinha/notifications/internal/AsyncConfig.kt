package pt.alinha.notifications.internal

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

// shortcut: nothing else in the app enables @Async, which the tenancy listener recipe needs; move to the core when a second plugin needs it.
@Configuration
@EnableAsync
class AsyncConfig {
    /** Bounded, so a burst of emails queues instead of spawning threads. Own name: a plain `taskExecutor` would replace the app-wide one. */
    @Bean(EXECUTOR)
    fun notificationsEmailExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 200
        setThreadNamePrefix("notifications-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(10)
    }

    companion object {
        const val EXECUTOR = "notificationsEmailExecutor"
    }
}
