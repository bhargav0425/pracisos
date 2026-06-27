package com.pracisos.booking.integration;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.event.PracisosEvent;
import com.pracisos.booking.event.dto.UserCreatedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"auth.user.created", "auth.tenant.created", "auth.user.updated", "auth.user.deactivated"})
class PractitionerSyncIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("test_booking")
        .withUsername("test")
        .withPassword("test");

    @BeforeAll
    static void checkDockerAndStart() {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            postgres.start();
        } catch (Exception e) {
            Assumptions.abort("Docker is not available, skipping integration test: " + e.getMessage());
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private PractitionerRepository practitionerRepository;

    @Test
    void whenUserCreatedEventReceived_thenPractitionerSynced() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserCreatedEvent payload = new UserCreatedEvent(
            userId, tenantId, "dr.bob@clinic.com",
            "Bob", "Smith", "PRACTITIONER", Instant.now()
        );
        PracisosEvent<UserCreatedEvent> event = new PracisosEvent<>(
            UUID.randomUUID(), "UserCreatedV1", tenantId, Instant.now(), "trace-123", payload
        );

        kafkaTemplate.send("auth.user.created", tenantId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Practitioner practitioner = practitionerRepository.findById(userId).orElseThrow();
            assertEquals("Bob", practitioner.getFirstName());
            assertEquals("Smith", practitioner.getLastName());
            assertEquals(tenantId, practitioner.getTenantId());
            assertEquals("ACTIVE", practitioner.getStatus());
        });
    }

    @Test
    void whenNonPractitionerUserCreated_thenIgnored() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserCreatedEvent payload = new UserCreatedEvent(
            userId, tenantId, "patient@clinic.com",
            "Jane", "Doe", "PATIENT", Instant.now()
        );
        PracisosEvent<UserCreatedEvent> event = new PracisosEvent<>(
            UUID.randomUUID(), "UserCreatedV1", tenantId, Instant.now(), "trace-123", payload
        );

        kafkaTemplate.send("auth.user.created", tenantId.toString(), event);

        await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(practitionerRepository.existsById(userId));
        });
    }
}
