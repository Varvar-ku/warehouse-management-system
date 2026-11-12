package ru.warehouse.auth.it;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import ru.warehouse.common.event.OutboxPublisher;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Основа интеграционных тестов (R15): настоящая PostgreSQL в Docker (миграции Flyway выполняются как в работе),
 * полная цепочка Spring Security с настоящей проверкой JWT, MockMvc. Kafka и outbox отключены,
 * OutboxPublisher заменён заглушкой. Без Docker тесты пропускаются.
 */
@SpringBootTest
public abstract class IntegrationTestBase {
    static final boolean DOCKER = dockerAvailable();
    private static GenericContainer<?> postgres;

    static {
        if (DOCKER) {
            postgres = new GenericContainer<>("postgres:18")
                    .withEnv("POSTGRES_DB", "test").withEnv("POSTGRES_USER", "test").withEnv("POSTGRES_PASSWORD", "test")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));
            postgres.start(); // контейнер общий для всех тестов модуля, снимается автоматически
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DOCKER, "Docker недоступен: интеграционные тесты пропущены");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/test");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        // P2-11: RS256. Публичный ключ для проверки токенов; приватный нужен auth-service для выпуска токенов
        registry.add("app.security.jwt.public-key-file", TestTokens::publicKeyFile);
        registry.add("app.security.jwt.private-key-file", TestTokens::privateKeyFile);
        registry.add("wms.outbox.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @MockitoBean
    protected OutboxPublisher outbox;

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;

    protected MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        // цепочка безопасности подключается вручную: spring-security-test для этого не нужен
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }
}
