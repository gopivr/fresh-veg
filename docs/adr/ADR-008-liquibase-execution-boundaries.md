# ADR-008: Liquibase execution boundaries

Date: 2026-09-08

Status: Accepted and implemented in Phase 2; refines ADR-002 and ADR-003.

## Context

Schema-local Liquibase tracking needs a schema to exist before service startup. A root update cannot apply the same changesets into public history while services independently apply them into owner histories without risking duplicate execution. Services also must not receive privileges to create cluster roles or access other schemas.

## Decision

Use disjoint required contexts beneath each owner master. Administrator bootstrap executes role/schema provisioning through the deterministic root master with context `bootstrap` and administrator-only public history. Each application uses context `service`, its own schema-local tracking tables, and a separate scoped migration login. All business migrations added in future go in the owner service branch, inheriting its required context. The root adds a required bootstrap filter and is not a cross-service business deployment command.

Use stable changeset logical paths across filesystem/classpath packaging. Package only the owner's database directory in each service; common's shared database fixtures/resources live exclusively in its test JAR under a distinct fixture path, avoiding resource collisions. Preserve schema validation and disable Spring SQL initialization.

Provision ten passwordless scoped LOGIN roles (runtime and migration per schema), with credentials assigned separately by the operator/secret tooling. Runtime privileges start at schema USAGE and database CONNECT; later business migrations explicitly grant required DML. Keep Liquibase metadata inaccessible to runtime roles and revoke database/public-schema default access. All versioned provisioning/privilege changes are Liquibase changesets.

## Consequences and alternatives

Bootstrap is a prerequisite for application startup and requires an administrator. Root and service histories intentionally track different changeset subsets. This avoids one shared writable history/lock table, duplicated MARK_RAN records, or schema creation by application code. The root is for bootstrap only; documented commands fix its context, and service migration preconditions enforce the expected migration login/schema.

Applications currently receive separate runtime and migration secrets to run startup migrations; a future deployment job can isolate migration secrets further. Service logins have no default credentials. Bootstrap is designed for a dedicated database with these role names unused; existing environments require deliberate DBA handling.

Infrastructure changes are forward-only because generic rollback cannot safely restore prior privileges or remove schemas after use. The test-only table changelog has a safe rollback and is never shipped in application JARs.

## Validation and references

Testcontainers checks clean and repeated execution, contexts, stable histories, lock release, scoped access and rollback of the test fixture. Application integration tests exercise actual Liquibase/JPA startup. Packaged Java 21 validation also uses the Maven bootstrap profile.

- [Liquibase contexts](https://docs.liquibase.com/secure/reference-guide-5-0/changelog-attributes/what-are-contexts)
- [Spring Boot Liquibase properties](https://docs.spring.io/spring-boot/4.0/appendix/application-properties/index.html)
- [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/)

See [migration guide](../database/migration-guide.md) for commands, ownership and operational restrictions.
