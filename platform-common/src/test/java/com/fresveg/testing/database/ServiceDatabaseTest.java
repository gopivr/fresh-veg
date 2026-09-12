package com.fresveg.testing.database;

import static org.assertj.core.api.Assertions.assertThat;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public abstract class ServiceDatabaseTest {
    @Container
    protected static final PostgreSQLContainer POSTGRES = DatabaseFixture.container();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DatabaseFixture.bootstrap(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.password", () -> DatabaseFixture.PASSWORD);
        registry.add("spring.liquibase.password", () -> DatabaseFixture.PASSWORD);
    }

    @Autowired
    DataSource dataSource;
    @Autowired
    SpringLiquibase liquibase;
    @Autowired
    Environment environment;

    @Test
    void applicationUsesOnlyItsOwnedRuntimeAndMigrationConnections() throws Exception {
        String owner = environment.getRequiredProperty("spring.application.name").replace("-service", "");
        try (var runtime = dataSource.getConnection(); var admin = DatabaseFixture.admin(POSTGRES)) {
            assertThat(DatabaseFixture.scalar(runtime, "SELECT current_user")).isEqualTo("fresveg_" + owner);
            assertThat(DatabaseFixture.scalar(runtime, "SELECT current_schema()")).isEqualTo(owner);
            assertThat(DatabaseFixture.scalar(admin, "SELECT count(*) FROM " + owner + ".databasechangelog")).isEqualTo(owner.equals("commerce") ? "5" : owner.equals("supply") ? "4" : owner.equals("fulfillment") ? "3" : (owner.equals("account") || owner.equals("catalog")) ? "2" : "1");
            for (String other : DatabaseFixture.OWNERS) {
                if (!owner.equals(other)) {
                    assertThat(DatabaseFixture.scalar(admin, "SELECT to_regclass('" + other + ".databasechangelog')")).isNull();
                }
            }
            assertThat(liquibase.getChangeLog()).isEqualTo("classpath:" + DatabaseFixture.master(owner));
            liquibase.afterPropertiesSet();
            assertThat(DatabaseFixture.scalar(admin, "SELECT count(*) FROM " + owner + ".databasechangelog")).isEqualTo(owner.equals("commerce") ? "5" : owner.equals("supply") ? "4" : owner.equals("fulfillment") ? "3" : (owner.equals("account") || owner.equals("catalog")) ? "2" : "1");
            assertThat(DatabaseFixture.scalar(admin, "SELECT count(*) FROM public.fresveg_bootstrap_changelog")).isEqualTo("6");
        }
    }
}
