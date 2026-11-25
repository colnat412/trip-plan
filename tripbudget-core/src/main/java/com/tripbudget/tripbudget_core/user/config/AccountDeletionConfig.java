package com.tripbudget.tripbudget_core.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AccountDeletionConfig {
    @Bean(name = "accountMediaScheduler")
    public ThreadPoolTaskScheduler accountMediaScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("account-media-cleanup-");
        return scheduler;
    }
}
