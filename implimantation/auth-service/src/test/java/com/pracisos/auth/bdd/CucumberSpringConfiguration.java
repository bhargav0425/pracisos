package com.pracisos.auth.bdd;

import com.pracisos.auth.domain.repository.AuditLogRepository;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.service.JwtService;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
    "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
    "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CucumberSpringConfiguration {

    @MockBean
    protected UserRepository userRepository;

    @MockBean
    protected TenantRepository tenantRepository;

    @MockBean
    protected AuditLogRepository auditLogRepository;

    @MockBean
    protected EventPublisher eventPublisher;

    @MockBean
    protected JwtService jwtService;
}
