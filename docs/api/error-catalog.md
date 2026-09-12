# Error catalog

| HTTP status | Meaning | Typical codes |
| --- | --- | --- |
| 400 | Invalid request syntax, validation failure or unsupported command state | `validation.failed`, `request.invalid` |
| 401 | Missing, expired or invalid bearer token | `auth.unauthorized` |
| 403 | Authenticated actor lacks role, ownership or service authority | `auth.forbidden` |
| 404 | Resource not found or hidden by ownership boundary | `resource.not_found` |
| 409 | Version conflict, capacity conflict, idempotency conflict or business invariant violation | `state.conflict`, `inventory.insufficient`, `idempotency.conflict` |
| 422 | Syntactically valid request rejected by business policy | `checkout.policy_rejected`, `payment.declined` |
| 500 | Unexpected service failure | `internal.error` |
| 503 | Downstream dependency or readiness failure | `service.unavailable` |

Every problem response carries the effective `X-Correlation-ID` value for support triage.
