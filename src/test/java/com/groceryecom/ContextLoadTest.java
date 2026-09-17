package com.groceryecom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContextLoadTest extends PostgresIntegrationTest {

    @Test
    void contextLoads() {
        // If the application context fails to start (including Flyway migrations
        // and Hibernate schema validation against PostgreSQL), this test will fail
        assertThat(true).isTrue();
    }
}
