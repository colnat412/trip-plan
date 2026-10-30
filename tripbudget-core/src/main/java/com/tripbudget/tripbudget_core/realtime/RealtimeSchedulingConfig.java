package com.tripbudget.tripbudget_core.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Scheduling infrastructure must be created without loading WebSocket services or JPA repositories. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RealtimeSchedulingConfig {
    @Bean
    public TaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("notification-worker-");
        return scheduler;
    }
}
