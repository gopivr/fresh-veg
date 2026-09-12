# ADR-007: Foundation baseline

Date: 2026-09-07

Status: Accepted and implemented in Phase 1.

## Context

Phase 0 proposed Java 21 and deferred dependency selection. The host has JDK 25.0.2 and Maven 3.9.10. The master lists JUnit 5 as part of its preferred stack, while the currently selected Boot generation manages newer Jupiter tooling.

## Decision

Compile for Java 21, with Maven 3.9.x and JDK 21–25 accepted by the build. Use Spring Boot 4.0.8 and Spring Cloud 2025.1.3 BOMs, and retain Boot-managed Jackson 3 and JUnit Jupiter 6.0.3/Mockito instead of overriding their test platform to JUnit 5. This is a compatibility adjustment to the preferred tooling, not a service-boundary change. Validate the complete build on host JDK 25 and executable JARs on Java 21.

Use WebFlux Gateway and MVC domain services. Common has generic correlation and Problem Details helpers; its Spring servlet dependencies are optional and are not pulled into the gateway. Application security allows only health probes until business security is implemented. JPA, PostgreSQL, Liquibase and Testcontainers versions remain BOM-managed; add their runtime/test dependencies when used in Phase 2. SpringDoc 3.0.3 is centrally managed for future MVC contract generation; Phase 1 maintains one static Actuator OpenAPI contract.

## Compatibility evidence

- [Spring Cloud compatibility](https://spring.io/projects/spring-cloud/) maps 2025.1 to Boot 4.0/4.1.
- [Spring Cloud 2025.1.3 release](https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/) documents the selected release.
- [Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) describes the new framework/module baseline.
- [Boot 4.0 testing](https://docs.spring.io/spring-boot/4.0/reference/testing/index.html) documents its managed test platform.
- [SpringDoc compatibility](https://springdoc.org/faq.html) maps Boot 4.0.x to SpringDoc 3.0.x.

Published POMs were resolved from Maven Central. Actual compilation and runtime checks, rather than these compatibility mappings alone, are the Phase 1 acceptance evidence.

## Consequences and alternatives

The preferred JUnit 5 version is adjusted to the BOM-managed Jupiter 6 baseline; tests retain Jupiter APIs. A Boot 3.5/Cloud 2025.0 foundation would preserve that older preferred tooling but starts on a Cloud train marked out of support in the compatibility table. Overriding Boot's framework/test dependencies independently adds avoidable compatibility risk.

Future persistence and contract work must use Boot 4 starter/module names and Jackson 3 APIs. SpringDoc and Testcontainers integration are not yet exercised. Phase 2 must activate real persistence without disabling migrations or adding fallback embedded databases. Reactive correlation is in Reactor context; bridging to MDC/tracing belongs to the observability work.

## Validation

`mvn clean verify` compiles all seven modules and runs unit/integration tests. The packaged-service script verifies default/local startup on Java 21; the infrastructure script verifies the isolated PostgreSQL/Redis Compose stack. See [phase summary](../PREVIOUS_PHASE_SUMMARY.md) for results.
