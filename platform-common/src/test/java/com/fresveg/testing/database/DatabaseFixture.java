package com.fresveg.testing.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Test-only database administration; never included in the production common JAR. */
public final class DatabaseFixture {
    public static final List<String> OWNERS = List.of("account", "catalog", "supply", "commerce", "fulfillment");
    public static final String ROOT = "fixture-database/master/db.changelog-master.yaml";
    public static final String PASSWORD = UUID.randomUUID().toString();
    private static final String IMAGE = "postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73";

    private DatabaseFixture() {
    }

    public static PostgreSQLContainer container() {
        return new PostgreSQLContainer(DockerImageName.parse(System.getenv().getOrDefault("TEST_POSTGRES_IMAGE", IMAGE))
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("fresveg").withUsername("fresveg_bootstrap")
                .withPassword(UUID.randomUUID().toString());
    }

    public static Connection admin(PostgreSQLContainer container) throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    public static Connection connect(PostgreSQLContainer container, String owner, boolean migration) throws SQLException {
        requireOwner(owner);
        return DriverManager.getConnection(container.getJdbcUrl(), "fresveg_" + owner + (migration ? "_migrator" : ""), PASSWORD);
    }

    public static void bootstrap(PostgreSQLContainer container) {
        try (var connection = admin(container)) {
            migrate(connection, ROOT, "public", "bootstrap", true);
            // Credential provisioning is separate from versioned schema migrations.
            // Generated test values never appear in changelogs or Liquibase history.
            for (String owner : OWNERS) {
                for (String suffix : List.of("", "_migrator")) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("ALTER ROLE fresveg_" + owner + suffix + " PASSWORD '" + PASSWORD + "'");
                    }
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize the isolated PostgreSQL fixture", error);
        }
    }

    public static void migrate(Connection connection, String changelog, String schema, String context, boolean bootstrap)
            throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        database.setLiquibaseSchemaName(schema);
        if (bootstrap) {
            database.setDatabaseChangeLogTableName("fresveg_bootstrap_changelog");
            database.setDatabaseChangeLogLockTableName("fresveg_bootstrap_changelog_lock");
        }
        // Keep caller-owned connection open for subsequent assertions/credential provisioning.
        var liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database);
        liquibase.validate();
        liquibase.update(new Contexts(context), new LabelExpression());
        connection.setAutoCommit(true);
        if (!liquibase.listUnrunChangeSets(new Contexts(context), new LabelExpression()).isEmpty()) {
            throw new IllegalStateException("Pending changesets after update: " + changelog);
        }
    }

    public static String master(String owner) {
        requireOwner(owner);
        return "database/" + owner + "/db.changelog-" + owner + "-master.yaml";
    }

    public static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new IllegalStateException("Expected a database result");
            }
            return result.getString(1);
        }
    }

    private static void requireOwner(String owner) {
        if (!OWNERS.contains(owner)) {
            throw new IllegalArgumentException("Unknown service owner");
        }
    }
}
