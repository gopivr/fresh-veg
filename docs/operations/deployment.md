# Deployment

Build service images from each module Dockerfile. Deploy PostgreSQL first, run the root bootstrap with administrative credentials for a new environment, then deploy services with their runtime credentials and service-specific migration credentials.

Required runtime inputs include database URL/user/password, OIDC issuer/audience/JWKS settings, downstream service base URLs and any environment-specific payment, fulfillment or outbox publisher settings. Expose `/actuator/health` for liveness/readiness and `/actuator/prometheus` for metrics collection.

Before release, run:

```bash
mvn clean verify
./scripts/run-hardening-checks.sh
```

Container image scanning should run in CI/CD with the organization-approved scanner and fail on policy violations.
