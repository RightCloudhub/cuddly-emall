package com.example.mall;

import com.example.mall.support.PostgresBackedTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MallApplicationTest extends PostgresBackedTest {

    @Test
    void contextLoads() {
        // smoke test against a real PG container — exercises Flyway + JPA validation.
    }
}
