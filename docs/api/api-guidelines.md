# API guidelines

Public APIs live under `/api/v1`. Internal service APIs live under `/internal/v1` and are not routed by the gateway. All request handlers accept and echo `X-Correlation-ID`.

Protected APIs use bearer tokens. Write commands that can be retried by clients or services accept `Idempotency-Key` and return the original result for a replay with the same authenticated actor and request scope.

Errors use `application/problem+json` with `type`, `title`, `status`, `code` and `correlationId`. Validation errors may include field-level details. List endpoints must keep bounded `pageSize` behavior and should support cursor-style pagination for future compatibility.
