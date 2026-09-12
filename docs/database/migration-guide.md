# Liquibase foundation and database operations

Phase 2 established schema isolation and infrastructure histories. Phase 3 adds the eight Account business tables through new changeset `account-003-create-domain`; Phase 4 adds six Catalog tables through `catalog-003-create-domain`. Phase 5 adds four Supply offer/pricing tables through `supply-003-create-offers`. Phase 6 adds inventory, batches, movement ledger and reservations through `supply-004-create-inventory`. Phase 7 adds customer carts/items through `commerce-003-create-carts`. Phase 8 is read-only and adds no schema objects. Phase 9 appends `commerce-004-create-orders` for five durable order/history/idempotency/outbox tables. Phase 10 appends `commerce-005-create-payments` for payment attempts/refunds and payment lifecycle statuses. Phase 11 appends `fulfillment-003-create-domain` for delivery slots, fulfillments, fulfillment items, shipments, shipment events and delivery assignments. Phase 12 appends `commerce-006-enable-outbox-publication`, granting Commerce runtime only the outbox publish-state updates needed by the publisher; prior numbered migrations remain unchanged.

## Ownership and credentials

| Schema | Application login | Migration login / schema owner |
| --- | --- | --- |
| account | fresveg_account | fresveg_account_migrator |
| catalog | fresveg_catalog | fresveg_catalog_migrator |
| supply | fresveg_supply | fresveg_supply_migrator |
| commerce | fresveg_commerce | fresveg_commerce_migrator |
| fulfillment | fresveg_fulfillment | fresveg_fulfillment_migrator |

Bootstrap uses a separate administrator with schema/role provisioning privileges. Both scoped logins lack superuser, database creation, role creation, replication and RLS-bypass privileges, and have no role memberships. Migration logins own their schema; application logins have CONNECT and USAGE only until a business migration explicitly grants required table operations. Applications cannot modify schema or Liquibase metadata.

The database is dedicated to FresVeg. Bootstrap revokes PUBLIC privileges on the database and public schema, including database CREATE/TEMPORARY. It grants CONNECT to the ten scoped roles. Future objects created by migration roles do not give PUBLIC table/sequence access or function execution. Do not grant runtime roles membership in migration roles or grant every table indiscriminately: this would expose migration history and locks. PostgreSQL system catalog visibility is unaffected; the isolation boundary is application schema data and DDL.

Roles are cluster-wide. Bootstrap intentionally halts if any specified role or schema already exists without recorded history; it does not silently adopt or overwrite existing ownership. For existing/shared installations, have the DBA plan an explicit migration instead of bypassing preconditions.

## Two execution paths

The root `database/master/db.changelog-master.yaml` includes database access hardening, then account, catalog, supply, commerce and fulfillment masters in that order. It is an **administrator bootstrap entry point**, not an all-service business migration executor.

Every owner master has two includes: `@bootstrap` selects role/schema creation, and `@service` selects the owner's service-migration branch. The root adds `@bootstrap` to its owner includes. Required contexts prevent accidental provisioning when no context is supplied. The root Maven profile fixes its context to `bootstrap`; applications use only `service`.

| Execution | Context | History / lock tables |
| --- | --- | --- |
| Administrator root bootstrap | bootstrap | public.fresveg_bootstrap_changelog / public.fresveg_bootstrap_changelog_lock |
| Each service using its migration login | service | owner.databasechangelog / owner.databasechangeloglock |

There are six bootstrap changesets (database hardening and five schema/role changesets) and one infrastructure service changeset per owner (migration-history protection), plus Account, Catalog and Supply changeset 003. Account, Catalog and Fulfillment histories each have two entries, Supply has three and Commerce has four. There are nineteen production changesets in total (including six bootstrap), 37 business tables and twelve history/lock tables. Bootstrap never executes the service branch. Service startup never executes provisioning changesets. Thus the two histories track disjoint work; no copying, checksum clearing, MARK_RAN workaround or shared service lock is needed. Re-running either path leaves its history unchanged.

Schema creation must precede service startup because Liquibase creates its tracking tables in an existing owned schema. All service JARs package only `database/<owner>/`; gateway and common production JARs contain no migrations. Stable `logicalFilePath` values keep changeset identity independent of filesystem/classpath/test-fixture location.

**Do not override root contexts to include `service`, or run service migrations as the bootstrap user.** The first service migration checks its actual login and schema. Future migrations belong beneath `db.changelog-<owner>-service.yaml`, where the required service context is inherited. Treat the root and service masters as different operational entry points, not interchangeable CLI targets.

## Local bootstrap

Start the existing PostgreSQL/Redis Compose stack using the README. Export the actual administrator connection information to the Maven process; Maven does not automatically load `.env`:

```sh
export BOOTSTRAP_DB_URL=jdbc:postgresql://localhost:5432/fresveg
export BOOTSTRAP_DB_USER=fresveg_bootstrap
# Supply BOOTSTRAP_DB_PASSWORD through your environment/secret tooling.
mvn -N -Pdatabase-bootstrap liquibase:validate
mvn -N -Pdatabase-bootstrap liquibase:update
mvn -N -Pdatabase-bootstrap liquibase:status
```

Use a dedicated fresh database and administrative role for this initial bootstrap. These commands change database privileges and create roles/schemas; they are not an inspection-only operation. The profile is optional, root-only and has no lifecycle execution, so `mvn clean verify` does not migrate a developer database. It instead uses isolated Testcontainers.

Set login passwords using secret-management/DBA tooling. Passwords are intentionally absent from changesets, parameters and history. For local manual provisioning, open psql as the bootstrap administrator and use its password prompt for each required login, for example:

```sh
docker compose exec postgres psql -U fresveg_bootstrap -d fresveg
```

```text
\password fresveg_account
\password fresveg_account_migrator
```

Repeat for the other services as needed. Adapt the bootstrap user/database to your Compose values. Roles have no usable password until provisioned; application startup must fail rather than fall back to admin/embedded credentials. Password changes are credential administration, not versioned schema migrations.

Run Account after exporting its distinct runtime and migration passwords and the three OIDC settings in the [Account guide](../account/account-service.md):

```sh
# Export DB_PASSWORD, MIGRATION_DB_PASSWORD, OIDC_ISSUER_URI, OIDC_AUDIENCE and OIDC_JWK_SET_URI.
SPRING_PROFILES_ACTIVE=local java -jar account-service/target/account-service-0.1.0-SNAPSHOT.jar
```

The local profile defaults to `jdbc:postgresql://localhost:5432/fresveg`, with POSTGRES_HOST/POSTGRES_PORT/POSTGRES_DB overrides. Default/production configuration requires DB_URL. DB_USER and MIGRATION_DB_USER default to that application's scoped role names; both can be supplied externally. Spring Liquibase uses a separate migration connection. The application datasource uses the runtime login. Hibernate validates after Liquibase; it never creates or updates schema.

## Adding later migrations

Add a new owner-prefixed changeset under the owner's changelog directory, and explicitly include it from the owner's service branch. Use a unique immutable ID, author, stable logical path, comments, useful preconditions, justified constraints/indexes and rollback where safe. All DDL belongs in Liquibase. Use local FKs and external UUIDs without cross-schema relationships. Follow the master for UUIDs, decimal money, currency codes, audit timestamps and naming.

Grant only necessary operations on newly created business tables to the owner runtime login in that same migration. Grant sequence usage only when needed. Never grant schema CREATE, PUBLIC access, cross-service privileges or DML on Liquibase metadata. Check effective grants with real scoped connections, not only SQL catalog inspection.

## Production expectations and rollback

Provision/bootstrap under DBA control with restricted credentials, a reviewed target URL, backup/recovery planning and normal deployment coordination. Use separate migration and runtime secrets. This phase runs owner migrations during application startup, so a deployment provides both credential sets to the process; never provide bootstrap credentials to an application. A dedicated migration-job deployment can remove migration credentials from runtime processes in future operations work while preserving identical owner histories.

The initial privilege/role/schema changes and metadata protection have no automatic rollback: restoring unknown prior grants or dropping a schema that now contains data/metadata is unsafe. Use reviewed forward corrections/new changesets and database recovery procedures. Do not edit recorded changesets, use DROP CASCADE, clear checksums or delete Liquibase history to reset an environment. Catalog 003 likewise has an empty-business-data-only rollback with exclusive table locks and no CASCADE; its five reference units are expected. It preserves populated data and earlier infrastructure. See the [Catalog guide](../catalog/catalog-service.md). Account 003 has a guarded rollback only while its business tables are empty (the four system roles are expected). It locks the eight tables, refuses populated rollback and drops without CASCADE. Its upgrade from the Phase 2 baseline, unchanged repeat, empty rollback/reapply and populated refusal are tested. Supply 003 locks its four tables and permits rollback only when all are empty, without CASCADE. Upgrade, repeat, empty rollback/reapply, populated refusal and immutable pricing grants are tested. See the [Supply guide](../supply/supply-service.md). Supply 004 permits rollback only with empty inventory tables; existing Phase 5 offers are preserved. Its upgrade/repeat/rollback tests also assert stock constraints and append-only ledger privileges. Commerce 003 permits rollback only while both cart tables are empty; upgrade/repeat/rollback, audit columns, local FKs and restricted runtime ownership grants are verified in its migration test. Fulfillment 003 permits rollback only while all six fulfillment business tables are empty; upgrade, repeat, populated rollback refusal, local FKs and restricted runtime grants are verified. The test-only table probe also demonstrates safe rollback for every owner.

## Validation

`mvn clean verify` requires Docker and downloads the pinned PostgreSQL Testcontainers image if missing. No Docker-absence skip, H2 substitute or disabled migrations is used. Tests cover clean bootstrap, explicit contexts, unchanged history/checksums/timestamps on repeat runs, separate owner migration histories, lock release, real runtime DML on a test-only table, forbidden cross-schema reads/writes/DDL, forbidden public/TEMP/schema creation, protected metadata and role escalation. The probe itself is created and rolled back by a test-only Liquibase changelog.

Every existing service integration test now starts on migrated PostgreSQL with real scoped runtime/migration credentials, and additionally re-runs its configured Liquibase bean without adding history. The gateway retains its database-independent tests.

`python3 scripts/validate-packaged-services.py` verifies the actual Maven bootstrap CLI and Java 21 JARs with default/local profiles against a separate ephemeral PostgreSQL container. It provisions random test credentials outside changelogs, checks root reruns after service startup, and removes its own resources. The original `validate-local-infrastructure.py` still validates the Compose infrastructure independently.

Commerce 004 locks all five new tables during rollback, refuses any order, event or recovery data and preserves Phase 7 carts. Orders/items/history/events cannot be deleted by runtime; only lifecycle/vendor-decision/recovery columns are writable. Snapshot money uses NUMERIC, timestamps TIMESTAMPTZ, PKs UUID and FKs only within Commerce. The old cart migration test targets its frozen Phase 7 baseline; a separate order migration test verifies upgrade/repeat/guarded rollback and privileges. See [orders](../commerce/orders.md).
