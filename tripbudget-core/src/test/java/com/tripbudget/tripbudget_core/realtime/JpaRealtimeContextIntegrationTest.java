package com.tripbudget.tripbudget_core.realtime;

import com.tripbudget.tripbudget_core.TripbudgetCoreApplication;
import com.tripbudget.tripbudget_core.notification.entities.NotificationEntity;
import com.tripbudget.tripbudget_core.notification.services.NotificationOutboxWorker;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;

/** Regression for the JPA bootstrap-executor -> WebSocket config -> repository cycle. */
@SpringBootTest(classes = TripbudgetCoreApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=notification-context-test",
                "spring.main.allow-circular-references=false",
                "spring.main.banner-mode=off",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
                "spring.jpa.properties.hibernate.default_schema=trip_core",
                "security.jwt.secret=notification-test-secret-with-at-least-thirty-two-characters",
                "app.web-origin=http://localhost:8080"
        })
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_TEST_DB_URL",
        matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/template_test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JpaRealtimeContextIntegrationTest {
    @DynamicPropertySource
    static void disposableDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("NOTIFICATION_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/template_test")) {
            throw new IllegalStateException("Only disposable localhost template_test is allowed");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "notification_test");
        registry.add("spring.datasource.password", () -> "");
    }

    // Persistence and repository beans are real; no connection to an application Redis is made.
    @MockitoBean StringRedisTemplate redis;
    @Autowired ConfigurableApplicationContext context;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired UserRepository users;
    @Autowired RealtimeConfig realtime;
    @Autowired RealtimeSessions sessions;
    @Autowired NotificationOutboxWorker worker;

    @Test
    void jpaAndRealtimeInitializeTogetherWithCircularReferencesDisabled() {
        assertTrue(context.isActive());
        assertTrue(entityManagerFactory.isOpen());
        assertNotNull(entityManagerFactory.getMetamodel().entity(NotificationEntity.class));
        assertFalse(mockingDetails(users).isMock());
        assertEquals(0, users.count());
        assertNotNull(context.getBean("taskScheduler", TaskScheduler.class));
        assertNotNull(realtime);
        assertNotNull(sessions);
        assertNotNull(worker);
    }
}
