package com.fresveg.catalog;

import static org.assertj.core.api.Assertions.*;
import com.fresveg.testing.database.DatabaseFixture;
import java.sql.Connection;
import java.sql.SQLException;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class CatalogMigrationIT {
    @Test
    void phaseTwoUpgradeIsRepeatableAndRollbackPreservesBusinessData() throws Exception {
        try (var postgres = DatabaseFixture.container()) {
            postgres.start();
            DatabaseFixture.bootstrap(postgres);
            try (var migration = DatabaseFixture.connect(postgres, "catalog", true);
                 var runtime = DatabaseFixture.connect(postgres, "catalog", false)) {
                DatabaseFixture.migrate(migration, "database-test/catalog-phase2-baseline.yaml", "catalog", "service", false);
                String baseline = history(migration);
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM catalog.databasechangelog")).isEqualTo("1");
                DatabaseFixture.migrate(migration, DatabaseFixture.master("catalog"), "catalog", "service", false);
                assertThat(history(migration)).startsWith(baseline);
                String upgraded = history(migration);
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM catalog.databasechangelog")).isEqualTo("2");
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM information_schema.tables WHERE table_schema='catalog' AND table_name NOT LIKE 'databasechangelog%'")).isEqualTo("6");
                assertThat(DatabaseFixture.scalar(runtime, "SELECT count(*) FROM catalog.units_of_measure")).isEqualTo("5");
                // Every business primary key is UUID; every FK stays within Catalog.
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM information_schema.columns WHERE table_schema='catalog' AND column_name IN ('created_at','updated_at') AND data_type='timestamp with time zone'")).isEqualTo("12");
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class r ON r.oid=c.confrelid JOIN pg_namespace rn ON rn.oid=r.relnamespace WHERE c.contype='f' AND n.nspname='catalog' AND rn.nspname<>'catalog'")).isEqualTo("0");
                DatabaseFixture.migrate(migration, DatabaseFixture.master("catalog"), "catalog", "service", false);
                assertThat(history(migration)).isEqualTo(upgraded);
                rollback(migration);
                assertThat(history(migration)).isEqualTo(baseline);
                assertThat(DatabaseFixture.scalar(migration, "SELECT to_regclass('catalog.products')")).isNull();
                DatabaseFixture.migrate(migration, DatabaseFixture.master("catalog"), "catalog", "service", false);
                try (var statement = runtime.createStatement()) {
                    statement.execute("INSERT INTO catalog.products(product_id,code,name,status) VALUES ('20000000-0000-0000-0000-000000000001','RETAINED','Retained product','ACTIVE')");
                }
                String populated = history(migration);
                assertThatThrownBy(() -> rollback(migration)).hasStackTraceContaining("Catalog rollback refused");
                migration.setAutoCommit(true);
                assertThat(DatabaseFixture.scalar(runtime, "SELECT count(*) FROM catalog.products")).isEqualTo("1");
                assertThat(history(migration)).isEqualTo(populated);
                for (String sql : new String[] {
                        "INSERT INTO catalog.units_of_measure(unit_id,code,name,dimension) VALUES (gen_random_uuid(),'X','Forbidden','COUNT')",
                        "UPDATE catalog.categories SET name='Forbidden'",
                        "DELETE FROM catalog.products",
                        "UPDATE catalog.databasechangelog SET md5sum=NULL"}) {
                    assertThatThrownBy(() -> { try (var statement = runtime.createStatement()) { statement.execute(sql); } })
                            .isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
                }
            }
        }
    }

    private static String history(Connection connection) throws Exception {
        return DatabaseFixture.scalar(connection, "SELECT string_agg(id || ':' || filename || ':' || md5sum || ':' || dateexecuted::text, ',' ORDER BY orderexecuted) FROM catalog.databasechangelog");
    }
    private static void rollback(Connection connection) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName("catalog");
        database.setLiquibaseSchemaName("catalog");
        var liquibase = new Liquibase(DatabaseFixture.master("catalog"), new ClassLoaderResourceAccessor(), database);
        liquibase.rollback(1, new Contexts("service"), new LabelExpression());
        connection.setAutoCommit(true);
        // Emulate a fresh CLI invocation: Liquibase caches fast checks across commands in this test JVM.
        Scope.getCurrentScope().getSingleton(liquibase.changelog.FastCheckService.class).clearCache();
    }
}
