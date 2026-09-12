package com.fresveg.testing.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class DatabaseFoundationIT {
    @Test
    void cleanBootstrapServiceMigrationsAndRepeatedRunsStayIsolated() throws Exception {
        try (var postgres = DatabaseFixture.container()) {
            postgres.start();
            DatabaseFixture.bootstrap(postgres);
            try (var admin = DatabaseFixture.admin(postgres)) {
                String bootstrapHistory = history(admin, "public.fresveg_bootstrap_changelog");
                assertThat(DatabaseFixture.scalar(admin, "SELECT count(*) FROM public.fresveg_bootstrap_changelog")).isEqualTo("6");
                assertThat(DatabaseFixture.scalar(admin, "SELECT count(*) FROM pg_roles WHERE rolname LIKE 'fresveg_%' AND rolname <> 'fresveg_bootstrap' AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolreplication)")).isEqualTo("0");
                for (String owner : DatabaseFixture.OWNERS) {
                    try (var migration = DatabaseFixture.connect(postgres, owner, true)) {
                        DatabaseFixture.migrate(migration, "fixture-" + DatabaseFixture.master(owner), owner, "service", false);
                        String serviceHistory = history(migration, owner + ".databasechangelog");
                        assertThat(DatabaseFixture.scalar(migration, "SELECT count(*) FROM " + owner + ".databasechangelog")).isEqualTo(owner.equals("commerce") ? "5" : owner.equals("supply") ? "4" : owner.equals("fulfillment") ? "3" : (owner.equals("account") || owner.equals("catalog")) ? "2" : "1");
                        DatabaseFixture.migrate(migration, "fixture-" + DatabaseFixture.master(owner), owner, "service", false);
                        assertThat(history(migration, owner + ".databasechangelog")).isEqualTo(serviceHistory);
                        assertThat(DatabaseFixture.scalar(migration, "SELECT locked FROM " + owner + ".databasechangeloglock")).isEqualTo("f");
                        createPrivilegeProbe(migration, owner);
                    }
                }
                // Running root bootstrap after every owner's service migration must not
                // replay service changes into privileged public history.
                DatabaseFixture.migrate(admin, DatabaseFixture.ROOT, "public", "bootstrap", true);
                assertThat(history(admin, "public.fresveg_bootstrap_changelog")).isEqualTo(bootstrapHistory);
                assertThat(DatabaseFixture.scalar(admin, "SELECT locked FROM public.fresveg_bootstrap_changelog_lock")).isEqualTo("f");
                for (String owner : DatabaseFixture.OWNERS) {
                    try (var runtime = DatabaseFixture.connect(postgres, owner, false);
                         var migration = DatabaseFixture.connect(postgres, owner, true)) {
                        String id = UUID.randomUUID().toString();
                        execute(runtime, "INSERT INTO " + owner + ".ownership_probe VALUES ('" + id + "', 1.25)");
                        execute(runtime, "UPDATE " + owner + ".ownership_probe SET amount=2.50 WHERE id='" + id + "'");
                        assertThat(DatabaseFixture.scalar(runtime, "SELECT amount FROM " + owner + ".ownership_probe")).isEqualTo("2.50");
                        execute(runtime, "DELETE FROM " + owner + ".ownership_probe WHERE id='" + id + "'");
                        denied(runtime, "SELECT * FROM " + owner + ".databasechangelog");
                        denied(runtime, "UPDATE " + owner + ".databasechangeloglock SET locked=true");
                        denied(runtime, "CREATE TABLE " + owner + ".not_allowed (id UUID)");
                        for (String other : DatabaseFixture.OWNERS) {
                            if (!owner.equals(other)) {
                                denied(runtime, "SELECT * FROM " + other + ".ownership_probe");
                                denied(runtime, "INSERT INTO " + other + ".ownership_probe VALUES ('" + id + "', 1)");
                                denied(migration, "SELECT * FROM " + other + ".ownership_probe");
                                denied(migration, "CREATE TABLE " + other + ".not_allowed (id UUID)");
                            }
                        }
                        for (Connection scoped : new Connection[] {runtime, migration}) {
                            denied(scoped, "CREATE TABLE public.not_allowed (id UUID)");
                            denied(scoped, "CREATE TEMP TABLE not_allowed (id UUID)");
                            denied(scoped, "CREATE SCHEMA not_allowed");
                            denied(scoped, "SELECT * FROM public.fresveg_bootstrap_changelog");
                            denied(scoped, "SET ROLE fresveg_bootstrap");
                        }
                        rollbackPrivilegeProbe(migration, owner);
                        assertThat(DatabaseFixture.scalar(admin, "SELECT to_regclass('" + owner + ".ownership_probe')")).isNull();
                    }
                }
            }
        }
    }

    @Test
    void bootstrapRequiresExplicitContext() throws Exception {
        try (var postgres = DatabaseFixture.container()) {
            postgres.start();
            try (var admin = DatabaseFixture.admin(postgres)) {
                DatabaseFixture.migrate(admin, DatabaseFixture.ROOT, "public", "", true);
                assertThat(DatabaseFixture.scalar(admin, "SELECT count(*) FROM pg_namespace WHERE nspname IN ('account','catalog','supply','commerce','fulfillment')")).isEqualTo("0");
            }
        }
    }

    private static String history(Connection connection, String table) throws SQLException {
        return DatabaseFixture.scalar(connection, "SELECT COALESCE(string_agg(id || ':' || author || ':' || filename || ':' || md5sum || ':' || dateexecuted::text, ',' ORDER BY orderexecuted),'') FROM " + table);
    }

    private static Liquibase probe(Connection connection, String owner) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(owner);
        database.setLiquibaseSchemaName(owner);
        var liquibase = new Liquibase("database-test/ownership-probe.yaml", new ClassLoaderResourceAccessor(), database);
        liquibase.setChangeLogParameter("runtimeRole", "fresveg_" + owner);
        return liquibase;
    }

    private static void createPrivilegeProbe(Connection connection, String owner) throws Exception {
        var liquibase = probe(connection, owner);
        liquibase.validate();
        liquibase.update(new Contexts(), new LabelExpression());
        connection.setAutoCommit(true);
    }

    private static void rollbackPrivilegeProbe(Connection connection, String owner) throws Exception {
        probe(connection, owner).rollback(1, new Contexts(), new LabelExpression());
        connection.setAutoCommit(true);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void denied(Connection connection, String sql) {
        assertThatThrownBy(() -> execute(connection, sql)).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
    }
}
