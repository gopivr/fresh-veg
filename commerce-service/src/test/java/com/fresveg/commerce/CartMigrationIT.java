package com.fresveg.commerce;

import static org.assertj.core.api.Assertions.*;
import com.fresveg.testing.database.DatabaseFixture;
import java.sql.Connection;
import java.sql.SQLException;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class CartMigrationIT {
    @Test
    void phaseTwoUpgradeIsRepeatableAndRollbackPreservesBusinessData() throws Exception {
        try (var postgres = DatabaseFixture.container()) {
            postgres.start();
            DatabaseFixture.bootstrap(postgres);
            try (var migration = DatabaseFixture.connect(postgres, "commerce", true);
                 var runtime = DatabaseFixture.connect(postgres, "commerce", false)) {
                DatabaseFixture.migrate(migration, "database-test/commerce-phase2-baseline.yaml", "commerce", "service", false);
                String baseline = history(migration);
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM commerce.databasechangelog")).isEqualTo("1");
                DatabaseFixture.migrate(migration, "database-test/commerce-phase7-baseline.yaml", "commerce", "service", false);
                assertThat(history(migration)).startsWith(baseline);
                String upgraded = history(migration);
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM commerce.databasechangelog")).isEqualTo("2");
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM information_schema.tables WHERE table_schema='commerce' AND table_name NOT LIKE 'databasechangelog%'")).isEqualTo("2");
                assertThat(DatabaseFixture.scalar(runtime, "SELECT count(*) FROM commerce.carts")).isEqualTo("0");
                // Every business primary key is UUID; every FK stays within Supply.
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM information_schema.columns WHERE table_schema='commerce' AND column_name IN ('created_at','updated_at') AND data_type='timestamp with time zone'")).isEqualTo("4");
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class r ON r.oid=c.confrelid JOIN pg_namespace rn ON rn.oid=r.relnamespace WHERE c.contype='f' AND n.nspname='commerce' AND rn.nspname<>'commerce'")).isEqualTo("0");
                DatabaseFixture.migrate(migration, "database-test/commerce-phase7-baseline.yaml", "commerce", "service", false);
                assertThat(history(migration)).isEqualTo(upgraded);
                rollback(migration);
                assertThat(history(migration)).isEqualTo(baseline);
                assertThat(DatabaseFixture.scalar(migration, "SELECT to_regclass('commerce.carts')")).isNull();
                DatabaseFixture.migrate(migration, "database-test/commerce-phase7-baseline.yaml", "commerce", "service", false);
                try (var statement = runtime.createStatement()) {
                    statement.execute("INSERT INTO commerce.carts(cart_id,customer_id,currency) VALUES (gen_random_uuid(),gen_random_uuid(),'USD')");
                }
                String populated = history(migration);
                assertThatThrownBy(() -> rollback(migration)).hasStackTraceContaining("Cart rollback refused");
                migration.setAutoCommit(true);
                assertThat(DatabaseFixture.scalar(runtime, "SELECT count(*) FROM commerce.carts")).isEqualTo("1");
                assertThat(history(migration)).isEqualTo(populated);
                for (String sql : new String[] {
                        "DELETE FROM commerce.carts",
                        "UPDATE commerce.carts SET customer_id=gen_random_uuid()",
                        "UPDATE commerce.cart_items SET listing_id=gen_random_uuid()",
                        "DELETE FROM commerce.carts",
                        "UPDATE commerce.databasechangelog SET md5sum=NULL"}) {
                    assertThatThrownBy(() -> { try (var statement = runtime.createStatement()) { statement.execute(sql); } })
                            .isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
                }
            }
        }
    }

    private static String history(Connection connection) throws Exception {
        return DatabaseFixture.scalar(connection, "SELECT string_agg(id || ':' || filename || ':' || md5sum || ':' || dateexecuted::text, ',' ORDER BY orderexecuted) FROM commerce.databasechangelog");
    }
    private static void rollback(Connection connection) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName("commerce");
        database.setLiquibaseSchemaName("commerce");
        var liquibase = new Liquibase("database-test/commerce-phase7-baseline.yaml", new ClassLoaderResourceAccessor(), database);
        liquibase.rollback(1, new Contexts("service"), new LabelExpression());
        connection.setAutoCommit(true);
        // Emulate a fresh CLI invocation: Liquibase caches fast checks across commands in this test JVM.
        Scope.getCurrentScope().getSingleton(liquibase.changelog.FastCheckService.class).clearCache();
    }
}
