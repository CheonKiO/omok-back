package org.scoula.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 실제 MySQL에 Flyway 마이그레이션(V1/V2)을 적용하고 JPA 엔티티와 대조한다 (#27).
 * 기존 단위 테스트는 H2 + flyway.enabled=false라 prod 스키마 소스(V*.sql)를 한 번도 실행하지
 * 않아, 마이그레이션-엔티티 드리프트를 CI가 못 잡았다. 이 IT는 컨테이너 MySQL에 실제 스크립트를
 * 적용하고 ddl-auto=validate로 엔티티 매핑까지 검증한다(context 로드 성공 = 스키마 정합).
 *
 * Docker가 있는 환경(GitHub Actions ubuntu 등)에서만 실행되고, 없으면 스킵된다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class FlywayMigrationIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("omok");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // 빈 컨테이너이므로 baseline 없이 V1부터 실제로 실행한다.
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
        // 마이그레이션이 만든 스키마와 엔티티 매핑이 어긋나면 부팅(context 로드)이 실패한다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long!!");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsApplyAndSchemaMatchesEntities() {
        // context 로드 성공 자체가 "V1/V2 적용 + validate 통과"의 증거.
        // 추가로 마이그레이션 이력과 핵심 테이블 존재를 명시 확인한다.
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertEquals(2, applied, "V1, V2 두 마이그레이션이 성공 적용되어야 한다");

        Integer gameTable = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = 'game'", Integer.class);
        assertEquals(1, gameTable, "game 테이블이 존재해야 한다");
    }
}
