package com.tripbudget.tripbudget_core.notification;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.notification.entities.*;
import com.tripbudget.tripbudget_core.notification.repositories.*;
import com.tripbudget.tripbudget_core.notification.services.*;
import com.tripbudget.tripbudget_core.trip.entities.*;
import com.tripbudget.tripbudget_core.trip.enums.*;
import com.tripbudget.tripbudget_core.trip.repositories.*;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringJUnitConfig(NotificationIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/template_test")
class NotificationIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {NotificationRepository.class, TripRepository.class, UserRepository.class, com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository.class})
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("NOTIFICATION_TEST_DB_URL");
            if (url == null || !url.matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):[0-9]+/template_test"))
                throw new IllegalStateException("Only disposable localhost template_test is allowed");
            return new DriverManagerDataSource(url, "notification_test", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.tripbudget.tripbudget_core.notification.entities",
                    "com.tripbudget.tripbudget_core.trip.entities", "com.tripbudget.tripbudget_core.user.entities",
                    "com.tripbudget.tripbudget_core.plan.entities");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.hbm2ddl.create_namespaces", "true", "hibernate.default_schema", "trip_core"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean HashidsService ids() { return new HashidsService("notification-test", 8); }
        @Bean NotificationService service(NotificationRepository n, RealtimeOutboxRepository o,
                TripMemberRepository m, TripRepository t, UserRepository u, HashidsService ids, com.tripbudget.tripbudget_core.plan.repositories.PlanActivityRepository a) {
            return new NotificationService(n, o, m, t, u, ids, a);
        }
        @Bean OutboxStore store(RealtimeOutboxRepository repository) { return new OutboxStore(repository); }
    }
    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired RealtimeOutboxRepository outbox;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository members;
    @Autowired UserRepository users;
    @Autowired OutboxStore store;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManagerFactory emf;
    @Autowired DataSource source;
    @Autowired HashidsService ids;
    TripEntity trip;
    TripMemberEntity lan;
    TransactionTemplate tx;

    @BeforeEach void seed() throws Exception {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            outbox.deleteAll(); notifications.deleteAll(); members.deleteAll(); trips.deleteAll(); users.deleteAll();
        });
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO public.users(id,email,name,status) VALUES (1,'nam@test','Nam','ACTIVE'),(2,'vice@test','Vice','ACTIVE'),(3,'lan@test','Lan','ACTIVE')");
        }
        tx.executeWithoutResult(status -> {
            trip = trips.save(TripEntity.create(1L, "Dalat", "Dalat", null, LocalDate.now(), LocalDate.now().plusDays(1), "VND"));
            members.save(TripMemberEntity.createOwner(trip, 1L));
            members.save(TripMemberEntity.addDirectMember(trip, 2L, TripMemberRole.VICE));
            lan = members.save(TripMemberEntity.addDirectMember(trip, 3L, TripMemberRole.MEMBER));
        });
    }

    @Test void recordsRecipientsWithBusinessTransactionAndNeverTheActor() {
        tx.executeWithoutResult(s -> service.activityCreated(1L, trip.getId(), 10L, false));
        assertEquals(2, notifications.count()); assertEquals(2, outbox.count());
        assertEquals(0, service.unreadCount(1L)); assertEquals(1, service.unreadCount(3L));
        assertEquals("Dalat", service.snapshot(3L, 0, 20, false).listing().items().getFirst().tripName());
    }

    @Test void rollbackLeavesNeitherNotificationNorOutbox() {
        tx.executeWithoutResult(s -> { service.activityCreated(1L, trip.getId(), 10L, false); s.setRollbackOnly(); });
        assertEquals(0, notifications.count()); assertEquals(0, outbox.count());
    }

    @Test void removedMemberCannotListCountReadOrOpenOldNotifications() {
        tx.executeWithoutResult(s -> service.activityCreated(1L, trip.getId(), 10L, false));
        Long id = ids.decode(service.list(3L, 0, 20, false).items().getFirst().id());
        tx.executeWithoutResult(s -> { var member = members.findById(lan.getId()).orElseThrow(); member.remove(); });
        assertEquals(0, service.unreadCount(3L)); assertTrue(service.list(3L, 0, 20, false).items().isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.read(3L, id));
        assertThrows(ResponseStatusException.class, () -> service.target(3L, id));
    }

    @Test void cannotReadAnotherUsersInboxAndReadIsIdempotentAcrossDevices() {
        tx.executeWithoutResult(s -> service.activityCreated(1L, trip.getId(), 10L, false));
        Long id = ids.decode(service.list(3L, 0, 20, false).items().getFirst().id());
        assertThrows(ResponseStatusException.class, () -> service.read(2L, id));
        service.read(3L, id);
        Instant readAt = notifications.findById(id).orElseThrow().getReadAt();
        long events = outbox.count(); service.read(3L, id); service.read(3L, null);
        assertEquals(readAt, notifications.findById(id).orElseThrow().getReadAt());
        assertEquals(events, outbox.count()); assertEquals(0, service.unreadCount(3L));
    }

    @Test void pendingInvitationNotifiesInviteeAndOwnerWithoutGrantingTripAccess() {
        tx.executeWithoutResult(s -> {
            var pending = members.findById(lan.getId()).orElseThrow(); pending.updateStatus(TripMemberStatus.INVITED);
            service.memberAdded(2L, trip, pending);
        });
        assertEquals(1, service.unreadCount(1L)); assertEquals(1, service.unreadCount(3L));
        Long id = ids.decode(service.list(3L, 0, 20, false).items().getFirst().id());
        assertFalse(service.target(3L, id).available()); assertNull(service.target(3L, id).path());
        tx.executeWithoutResult(s -> {
            var member = members.findById(lan.getId()).orElseThrow(); member.updateStatus(TripMemberStatus.ACTIVE);
            service.memberApproved(1L, member);
        });
        assertEquals(2, service.unreadCount(3L)); assertTrue(service.target(3L, id).available());
    }

    @Test void workerClaimIsExclusiveAndStaleClaimCannotAcknowledgeNewOwner() {
        tx.executeWithoutResult(s -> service.activityCreated(1L, trip.getId(), 10L, false));
        var item = store.due().getFirst(); UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
        assertTrue(store.claim(item.getId(), first)); assertFalse(store.claim(item.getId(), second));
        Instant later = Instant.now().plusSeconds(120);
        tx.executeWithoutResult(s -> assertEquals(1, outbox.claim(item.getId(), second, later, later.plusSeconds(60))));
        store.finish(item.getId(), first, true, 1);
        assertEquals("PROCESSING", outbox.findById(item.getId()).orElseThrow().getStatus());
        store.finish(item.getId(), second, true, 2);
        assertEquals("DISPATCHED", outbox.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test void retriesEventuallyFailAndBatchCreatesOneNotificationPerRecipient() {
        tx.executeWithoutResult(s -> service.activityCreated(1L, trip.getId(), null, true));
        assertEquals(2, notifications.count());
        var item = store.due().getFirst(); UUID token = UUID.randomUUID();
        assertTrue(store.claim(item.getId(), token)); store.finish(item.getId(), token, false, 10);
        assertEquals("FAILED", outbox.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test void deletedActivityTargetIsUnavailableEvenWhenNotificationRemainsInInbox() {
        tx.executeWithoutResult(s -> service.activityCreated(1L, trip.getId(), 99999L, false));
        Long id = ids.decode(service.list(3L, 0, 20, false).items().getFirst().id());
        assertFalse(service.target(3L, id).available());
        assertEquals("UNAVAILABLE", service.target(3L, id).status());
    }

    @Test void recordingRequiresBusinessTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> service.activityCreated(1L, trip.getId(), 10L, false));
    }

    @Test void migrationMatchesJpaMappings() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE trip_core.realtime_outbox, trip_core.notifications");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__notifications_realtime.sql"));
        }
        emf.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
    }
}
