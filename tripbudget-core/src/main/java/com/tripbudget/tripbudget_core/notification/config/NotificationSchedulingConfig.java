package com.tripbudget.tripbudget_core.notification.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.*;

@Configuration
@EnableScheduling
public class NotificationSchedulingConfig {
    @Bean
    public java.time.Clock notificationClock() { return java.time.Clock.systemUTC(); }

    @Bean(name = "notificationExecutor", destroyMethod = "shutdown")
    public ExecutorService notificationExecutor() {
        // No dependency on JPA/services: avoids scheduler/JPA startup cycles.
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
    }
}
