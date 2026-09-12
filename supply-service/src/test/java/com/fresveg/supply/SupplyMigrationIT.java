package com.fresveg.supply;

import static org.assertj.core.api.Assertions.*;
import com.fresveg.testing.database.DatabaseFixture;
import java.sql.Connection;
import java.sql.SQLException;
import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class SupplyMigrationIT {
    @Test
    void phaseTwoUpgradeIsRepeatableAndRollbackPreservesBusinessData() throws Exception {
        try (var postgres = DatabaseFixture.container()) {
            postgres.start();
            DatabaseFixture.bootstrap(postgres);
            try (var migration = DatabaseFixture.connect(postgres, "supply", true);
                 var runtime = DatabaseFixture.connect(postgres, "supply", false)) {
                DatabaseFixture.migrate(migration, "database-test/supply-phase2-baseline.yaml", "supply", "service", false);
                String baseline = history(migration);
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM supply.databasechangelog")).isEqualTo("1");
                DatabaseFixture.migrate(migration, "database-test/supply-phase5-baseline.yaml", "supply", "service", false);
                assertThat(history(migration)).startsWith(baseline);
                String upgraded = history(migration);
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM supply.databasechangelog")).isEqualTo("2");
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM information_schema.tables WHERE table_schema='supply' AND table_name NOT LIKE 'databasechangelog%'")).isEqualTo("4");
                assertThat(DatabaseFixture.scalar(runtime, "SELECT count(*) FROM supply.vendor_listings")).isEqualTo("0");
                // Every business primary key is UUID; every FK stays within Supply.
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM information_schema.columns WHERE table_schema='supply' AND column_name IN ('created_at','updated_at') AND data_type='timestamp with time zone'")).isEqualTo("8");
                assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class r ON r.oid=c.confrelid JOIN pg_namespace rn ON rn.oid=r.relnamespace WHERE c.contype='f' AND n.nspname='supply' AND rn.nspname<>'supply'")).isEqualTo("0");
                DatabaseFixture.migrate(migration, "database-test/supply-phase5-baseline.yaml", "supply", "service", false);
                assertThat(history(migration)).isEqualTo(upgraded);
                rollback(migration);
                assertThat(history(migration)).isEqualTo(baseline);
                assertThat(DatabaseFixture.scalar(migration, "SELECT to_regclass('supply.vendor_locations')")).isNull();
                DatabaseFixture.migrate(migration, "database-test/supply-phase5-baseline.yaml", "supply", "service", false);
                try (var statement = runtime.createStatement()) {
                    statement.execute("INSERT INTO supply.vendor_locations(location_id,vendor_id,code,name,line1,city,postal_code,country_code,status) VALUES ('20000000-0000-0000-0000-000000000001',gen_random_uuid(),'RETAINED','Retained location','1 Road','Boston','02110','US','ACTIVE')");
                }
                String populated = history(migration);
                assertThatThrownBy(() -> rollback(migration)).hasStackTraceContaining("Supply rollback refused");
                migration.setAutoCommit(true);
                assertThat(DatabaseFixture.scalar(runtime, "SELECT count(*) FROM supply.vendor_locations")).isEqualTo("1");
                assertThat(history(migration)).isEqualTo(populated);
                for (String sql : new String[] {
                        "UPDATE supply.vendor_listing_prices SET unit_price=1",
                        "UPDATE supply.price_tiers SET unit_price=1",
                        "DELETE FROM supply.vendor_locations",
                        "UPDATE supply.databasechangelog SET md5sum=NULL"}) {
                    assertThatThrownBy(() -> { try (var statement = runtime.createStatement()) { statement.execute(sql); } })
                            .isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
                }
            }
        }
    }

    private static String history(Connection connection) throws Exception {
        return DatabaseFixture.scalar(connection, "SELECT string_agg(id || ':' || filename || ':' || md5sum || ':' || dateexecuted::text, ',' ORDER BY orderexecuted) FROM supply.databasechangelog");
    }
    private static void rollback(Connection connection) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName("supply");
        database.setLiquibaseSchemaName("supply");
        var liquibase = new Liquibase("database-test/supply-phase5-baseline.yaml", new ClassLoaderResourceAccessor(), database);
        liquibase.rollback(1, new Contexts("service"), new LabelExpression());
        connection.setAutoCommit(true);
        // Emulate a fresh CLI invocation: Liquibase caches fast checks across commands in this test JVM.
        Scope.getCurrentScope().getSingleton(liquibase.changelog.FastCheckService.class).clearCache();
    }
}
