# Troubleshooting

Start with the response `X-Correlation-ID`, then search service logs for the same value. Gateway failures usually indicate token, route, CORS or rate-limit configuration. Domain-service `403` responses usually indicate ownership or stored role/membership state.

For database startup failures, verify that bootstrap has run, the service migrator password is valid, and the runtime role has only the grants created by the service migrations. Liquibase lock problems should be handled by inspecting the owning schema lock table; do not clear checksums as a workaround.

For checkout failures, inspect Commerce order/idempotency state, Supply reservation state, payment attempts and outbox rows. Payment failures should leave a failed payment attempt, cancellation events and released inventory reservations.
