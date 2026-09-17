package com.groceryecom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ContextLoadTest {

    @Test
    void contextLoads() {
        // If the application context fails to start, this test will fail
        assertThat(true).isTrue();
    }
}

