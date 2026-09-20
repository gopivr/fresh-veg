# Local login and tokens

The local Docker Compose stack includes Keycloak on http://localhost:8180.
The `fresveg` public client enables password credentials (Keycloak Direct
Access Grants) for local authentication. Users can obtain tokens directly from
Postman or curl without a browser, callback URL or client secret. This grant is
for local development only; use Authorization Code with PKCE for production user
login. Browser login with PKCE remains available for registration and account setup.

## Start and create a user

```bash
docker compose up -d authorization-server
```

Wait for `docker compose ps authorization-server` to report healthy. Use an
existing user in the `fresveg` realm, or create one in the admin console:

1. Open http://localhost:8180/admin and select the **fresveg** realm.
2. Select **Users → Add user**. Fill in email, first name and last name and save.
3. Under **Credentials**, set a password with **Temporary** switched off.
4. Ensure the user is enabled and has no pending required actions. Complete any
   required profile or verification steps before requesting a password token.

Use the customer's credentials from `fresveg`, not the bootstrap admin from
`master`. Account provisions the local customer on the first successful API call.

## Get a token in Postman

Create a **POST** request to:

```text
http://localhost:8180/realms/fresveg/protocol/openid-connect/token
```

Select **Authorization → No Auth**, then **Body → x-www-form-urlencoded**:

| Key | Value |
| --- | --- |
| `grant_type` | `password` |
| `client_id` | `fresveg` |
| `username` | Your registered user's email |
| `password` | Your user's password |
| `scope` | `openid profile email` |

Send the request and copy `access_token` from the JSON response. On application
requests, select **Authorization → Bearer Token** and paste that value.
No authorization URL, redirect URI, PKCE verifier or service account is required
for this token request.

## Get a token with curl

Replace the placeholders with a local user's credentials:

```bash
curl --fail-with-body -sS \
  http://localhost:8180/realms/fresveg/protocol/openid-connect/token \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=fresveg' \
  --data-urlencode 'username=<your-email>' \
  --data-urlencode 'password=<your-password>' \
  --data-urlencode 'scope=openid profile email'
```

Start the application with `./scripts/start-local.sh`, then use the returned
`access_token`:

```bash
export ACCESS_TOKEN='<access_token from the response>'
curl -fsS http://localhost:8080/api/v1/accounts/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Access tokens expire after five minutes. Request another token with the same
flow, or exchange the returned refresh token:

```bash
curl --fail-with-body -sS \
  http://localhost:8180/realms/fresveg/protocol/openid-connect/token \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'client_id=fresveg' \
  --data-urlencode 'refresh_token=<refresh_token from the response>'
```

## Existing installations and troubleshooting

For a realm imported before this change, rename **Clients → fresveg-local** to
`fresveg` (or create a new public client with the same settings), enable **Direct
access grants**, and save. Restarting alone does not reimport an existing realm.
  and enabled status.
- `Account is not fully set up`: Complete required actions and profile fields;
  set a permanent password rather than a temporary one.
- `Public client not allowed to retrieve service account`: Use `grant_type=password`;
  `client_credentials` is a separate service-account flow.

The client also retains Authorization Code with S256 PKCE. For optional browser
login, its registered callback URLs are `http://127.0.0.1:8765/callback`,
`https://oauth.pstmn.io/v1/callback` and
`https://oauth.pstmn.io/v1/browser-callback`.

## Configuration and roles

Compose uses `http://localhost:8180/realms/fresveg` as the exact token issuer.
Containers fetch keys using
`http://authorization-server:8080/realms/fresveg/protocol/openid-connect/certs`.
For services running directly on the host, export:

```bash
export OIDC_ISSUER_URI=http://localhost:8180/realms/fresveg
export OIDC_JWK_SET_URI=http://localhost:8180/realms/fresveg/protocol/openid-connect/certs
export OIDC_AUDIENCE=fresveg-account # Change for each service.
```

The local client includes the gateway and all five domain audiences so forwarded
tokens pass each service's validation. Newly registered users receive the
`CUSTOMER` realm role, emitted under `realm_access.roles`, which the gateway
already supports. Setting an admin/vendor role in Keycloak alone does not grant
domain permissions: Account's stored roles and vendor memberships remain required.
This client does not issue privileged internal service credentials.

Keycloak's admin console is at http://localhost:8180/admin. Local bootstrap
credentials default to `admin` / `local-admin-change-me`; override them with
`KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` before first startup.
Keycloak uses the existing PostgreSQL server and the same `${POSTGRES_DB:-fresveg}`
database as the other services. Its tables live in the dedicated `authorization`
schema, owned by `fresveg_authorization`. Set `KEYCLOAK_DB_PASSWORD` before starting
the stack; the local default is `authorization_password`. Bootstrap creates the
schema and role, password provisioning completes, then Keycloak starts and manages
its own table migrations. Users and signing keys persist in `postgres_data`.
Startup import skips
an existing realm; later configuration changes must be applied through the admin
console or an explicit migration. Do not delete `postgres_data` unless you intend to
lose its users and keys. Existing application users from another issuer are not
automatically linked to newly registered Keycloak users.

If you previously ran the H2 configuration, its `keycloak_data` volume is left
untouched but is no longer mounted. Existing H2 users and keys are not automatically
migrated to PostgreSQL. Export/import that realm before switching if you need to
preserve existing identities; the bundled realm import initializes a fresh database.

This configuration is for local development: loopback ports, HTTP, development
server mode and local bootstrap credentials. Production needs an HTTPS hostname,
production server settings, managed database/admin credentials and appropriate registration/email
policies. Existing external providers remain configurable with the OIDC variables.

References: [Keycloak containers](https://www.keycloak.org/server/containers),
[realm import](https://www.keycloak.org/server/importExport),
[database configuration](https://www.keycloak.org/server/db),
[OIDC flows](https://www.keycloak.org/securing-apps/oidc-layers).
