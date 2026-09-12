#!/usr/bin/env python3
"""Validate Java 21 applications against an isolated, Liquibase-bootstrapped PostgreSQL database."""
import json
import os
from pathlib import Path
import subprocess
import secrets
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid

root = Path(__file__).resolve().parents[1]
image = os.environ.get("JAVA_TEST_IMAGE", "eclipse-temurin:21-jre")
modules = ["api-gateway", "account-service", "catalog-service", "supply-service",
           "commerce-service", "fulfillment-service"]


def docker(*args, env=None):
    return subprocess.check_output(["docker", *args], text=True, env=env).strip()


def check(module, profile, network, database_environment):
    jar = root / module / "target" / f"{module}-0.1.0-SNAPSHOT.jar"
    if not jar.is_file():
        raise RuntimeError(f"Missing {jar}; run mvn clean verify first")
    name = "fresveg-jar-check-" + uuid.uuid4().hex[:12]
    args = ["run", "--detach", "--name", name, "--publish", "127.0.0.1::8080",
            "--mount", f"type=bind,source={jar},target=/app.jar,readonly",
            "--env", "SERVER_ADDRESS=0.0.0.0", "--env", "SERVER_PORT=8080", "--network", network]
    application_environment = dict(database_environment)
    if module != "api-gateway":
        owner = module.removesuffix("-service")
        application_environment.update(DB_USER="fresveg_" + owner, MIGRATION_DB_USER="fresveg_" + owner + "_migrator")
        for variable in ["DB_URL", "DB_USER", "DB_PASSWORD", "MIGRATION_DB_USER", "MIGRATION_DB_PASSWORD"]:
            args += ["--env", variable]
    if module in {"account-service", "catalog-service", "supply-service", "commerce-service", "fulfillment-service"}:
        application_environment.update(OIDC_ISSUER_URI="https://identity.example.test",
                                       OIDC_AUDIENCE="fresveg-account",
                                       OIDC_JWK_SET_URI="https://identity.example.test/jwks")
        for variable in ["OIDC_ISSUER_URI", "OIDC_AUDIENCE", "OIDC_JWK_SET_URI"]:
            args += ["--env", variable]
    if module == "catalog-service":
        application_environment.update(OIDC_AUDIENCE="fresveg-catalog", ACCOUNT_BASE_URL="http://account-unavailable.example.test")
        args += ["--env", "ACCOUNT_BASE_URL"]
    if module == "supply-service":
        application_environment.update(OIDC_AUDIENCE="fresveg-supply",
                                       ACCOUNT_BASE_URL="http://account-unavailable.example.test",
                                       CATALOG_BASE_URL="http://catalog-unavailable.example.test")
        args += ["--env", "ACCOUNT_BASE_URL", "--env", "CATALOG_BASE_URL"]
    if module == "commerce-service":
        application_environment.update(OIDC_AUDIENCE="fresveg-commerce",
                                       ACCOUNT_BASE_URL="http://account-unavailable.example.test",
                                       SUPPLY_BASE_URL="http://supply-unavailable.example.test")
        args += ["--env", "ACCOUNT_BASE_URL", "--env", "SUPPLY_BASE_URL"]
    if module == "fulfillment-service":
        application_environment.update(OIDC_AUDIENCE="fresveg-fulfillment")
    if profile == "local":
        args += ["--env", "SPRING_PROFILES_ACTIVE=local"]
    try:
        docker(*args, image, "java", "-Xmx256m", "-XX:ActiveProcessorCount=2", "-jar", "/app.jar", env=application_environment)
        port = json.loads(docker("inspect", name))[0]["NetworkSettings"]["Ports"]["8080/tcp"][0]["HostPort"]
        base = f"http://127.0.0.1:{port}"
        deadline = time.monotonic() + 60
        while True:
            try:
                with urlopen(base + "/actuator/health", timeout=2) as response:
                    health = json.load(response)
                    assert health.get("status") == "UP", health
                    assert set(health) <= {"status", "groups"}, health
                break
            except (URLError, TimeoutError, ConnectionError):
                if time.monotonic() >= deadline:
                    raise RuntimeError(f"{module}/{profile} did not become healthy")
                time.sleep(0.5)
        for path in ["/actuator/health/liveness", "/actuator/health/readiness"]:
            with urlopen(Request(base + path, headers={"X-Correlation-ID": "packaged-check"}), timeout=5) as response:
                assert json.load(response) == {"status": "UP"}
                assert response.headers["X-Correlation-ID"] == "packaged-check"
        try:
            urlopen(base + "/internal/v1/test", timeout=5)
        except HTTPError as error:
            if module == "fulfillment-service":
                assert error.code == 401
                assert error.headers["WWW-Authenticate"] == "Bearer"
                assert json.load(error)["code"] == "SEC-401-001"
            else:
                assert error.code == 403
                assert json.load(error)["code"] == "SEC-403-001"
        else:
            raise AssertionError("Internal request was not denied")
        if module == "account-service":
            try:
                urlopen(base + "/api/v1/accounts/me", timeout=5)
            except HTTPError as error:
                assert error.code == 401
                assert error.headers["WWW-Authenticate"] == "Bearer"
                assert json.load(error)["code"] == "SEC-401-001"
            else:
                raise AssertionError("Account accepted an unauthenticated request")
            if profile == "local":
                with urlopen(base + "/v3/api-docs", timeout=10) as response:
                    spec = json.load(response)
                    assert len(spec["paths"]) == 5
                    assert "201" in spec["paths"]["/api/v1/accounts/me/addresses"]["post"]["responses"]
            else:
                try:
                    urlopen(base + "/v3/api-docs", timeout=5)
                except HTTPError as error:
                    assert error.code == 403
                else:
                    raise AssertionError("API docs should be disabled in the default profile")
        if module == "catalog-service":
            with urlopen(Request(base + "/api/v1/catalog/products", headers={"X-Correlation-ID": "catalog-packaged"}), timeout=5) as response:
                result = json.load(response)
                assert result["data"] == [] and result["pagination"]["hasNext"] is False
                assert result["meta"]["requestId"] == "catalog-packaged"
            try:
                urlopen(Request(base + "/api/v1/catalog/products", data=b"{}", headers={"Content-Type": "application/json"}), timeout=5)
            except HTTPError as error:
                assert error.code == 401
            else:
                raise AssertionError("Catalog allowed an unauthenticated write")
            if profile == "local":
                with urlopen(base + "/v3/api-docs", timeout=10) as response:
                    spec = json.load(response)
                    assert len(spec["paths"]) == 4 and "CatalogProblem" in spec["components"]["schemas"]
            else:
                try:
                    urlopen(base + "/v3/api-docs", timeout=5)
                except HTTPError as error:
                    assert error.code == 403
                else:
                    raise AssertionError("Catalog docs should default to disabled")
        if module == "supply-service":
            try:
                urlopen(Request(base + "/internal/v1/inventory/reservations", data=b"{}", headers={"Content-Type": "application/json"}), timeout=5)
            except HTTPError as error:
                assert error.code == 401 and json.load(error)["code"] == "SEC-401-001"
            else:
                raise AssertionError("Supply allowed an unauthenticated reservation")
            for path, expected in [("/api/v1/supply/listings/" + str(uuid.uuid4()), 404),
                                   ("/api/v1/supply/vendor/listings", 401)]:
                try:
                    urlopen(base + path, timeout=5)
                except HTTPError as error:
                    assert error.code == expected
                    assert "correlationId" in json.load(error)
                else:
                    raise AssertionError("Unexpected Supply response")
            if profile == "local":
                with urlopen(base + "/v3/api-docs", timeout=10) as response:
                    spec = json.load(response)
                    assert len(spec["paths"]) == 17 and "SupplyProblem" in spec["components"]["schemas"]
            else:
                try:
                    urlopen(base + "/v3/api-docs", timeout=5)
                except HTTPError as error:
                    assert error.code == 403
                else:
                    raise AssertionError("Supply docs should default to disabled")
        if module == "commerce-service":
            try:
                urlopen(Request(base + "/api/v1/checkout/preview", data=b"{}", headers={"Content-Type": "application/json"}), timeout=5)
            except HTTPError as error:
                assert error.code == 401 and json.load(error)["code"] == "SEC-401-001"
            else:
                raise AssertionError("Commerce accepted an unauthenticated checkout preview")
            try:
                urlopen(Request(base + "/api/v1/carts", data=b'{"currency":"USD"}', headers={"Content-Type": "application/json"}), timeout=5)
            except HTTPError as error:
                assert error.code == 401 and json.load(error)["code"] == "SEC-401-001"
            else:
                raise AssertionError("Commerce accepted an unauthenticated cart")
            if profile == "local":
                with urlopen(base + "/v3/api-docs", timeout=10) as response:
                    spec = json.load(response)
                    assert len(spec["paths"]) == 12 and "CommerceProblem" in spec["components"]["schemas"]
            else:
                try:
                    urlopen(base + "/v3/api-docs", timeout=5)
                except HTTPError as error:
                    assert error.code == 403
                else:
                    raise AssertionError("Commerce docs should default to disabled")
        if module == "fulfillment-service":
            with urlopen(Request(base + "/api/v1/fulfillment/slots", headers={"X-Correlation-ID": "fulfillment-packaged"}), timeout=5) as response:
                result = json.load(response)
                assert result["data"] == [] and result["meta"]["requestId"] == "fulfillment-packaged"
            try:
                urlopen(Request(base + "/internal/v1/fulfillments", data=b"{}", headers={"Content-Type": "application/json"}), timeout=5)
            except HTTPError as error:
                assert error.code == 401 and json.load(error)["code"] == "SEC-401-001"
            else:
                raise AssertionError("Fulfillment accepted an unauthenticated internal create")
            if profile == "local":
                with urlopen(base + "/v3/api-docs", timeout=10) as response:
                    spec = json.load(response)
                    assert len(spec["paths"]) == 6 and "FulfillmentProblem" in spec["components"]["schemas"]
            else:
                try:
                    urlopen(base + "/v3/api-docs", timeout=5)
                except HTTPError as error:
                    assert error.code in {401, 403}
                    if error.code == 401:
                        assert error.headers["WWW-Authenticate"] == "Bearer"
                else:
                    raise AssertionError("Fulfillment docs should default to disabled")
        events = []
        for line in docker("logs", name).splitlines():
            if line.startswith("{"):
                events.append(json.loads(line))
        assert any(e.get("service", {}).get("name") == module and "@timestamp" in e for e in events), "No structured service logs"
        print(f"PASS: {module}/{profile}: Java 21 jar startup, health/probes, correlation, denial and ECS logs", flush=True)
    except Exception:
        print(docker("logs", name), flush=True)
        raise
    finally:
        docker("rm", "--force", name)


def validate():
    owners = [module.removesuffix("-service") for module in modules if module != "api-gateway"]
    suffix = uuid.uuid4().hex[:12]
    network = "fresveg-db-check-" + suffix
    postgres = network + "-postgres"
    environment = dict(os.environ)
    environment.update(POSTGRES_DB="fresveg", POSTGRES_USER="fresveg_bootstrap",
                       POSTGRES_PASSWORD=secrets.token_urlsafe(32),
                       DB_PASSWORD=secrets.token_urlsafe(32), MIGRATION_DB_PASSWORD=secrets.token_urlsafe(32),
                       DB_URL="jdbc:postgresql://postgres:5432/fresveg")
    # Reuse the exact Compose image without loading developer .env credentials.
    config = json.loads(docker("compose", "--env-file", os.devnull, "config", "--format", "json", env=environment))
    postgres_image = config["services"]["postgres"]["image"]
    subprocess.run(["docker", "pull", image], check=True)
    print(docker("run", "--rm", image, "java", "-version"))
    docker("network", "create", network)
    try:
        docker("run", "--detach", "--name", postgres, "--network", network, "--network-alias", "postgres",
               "--publish", "127.0.0.1::5432", "--tmpfs", "/var/lib/postgresql/data",
               "--env", "POSTGRES_DB", "--env", "POSTGRES_USER", "--env", "POSTGRES_PASSWORD",
               postgres_image, env=environment)
        deadline = time.monotonic() + 60
        while subprocess.run(["docker", "exec", postgres, "pg_isready", "-U", "fresveg_bootstrap", "-d", "fresveg"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            if time.monotonic() >= deadline:
                raise RuntimeError("Isolated PostgreSQL did not become ready")
            time.sleep(0.5)
        port = json.loads(docker("inspect", postgres))[0]["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"]
        environment.update(BOOTSTRAP_DB_URL=f"jdbc:postgresql://127.0.0.1:{port}/fresveg",
                           BOOTSTRAP_DB_USER="fresveg_bootstrap", BOOTSTRAP_DB_PASSWORD=environment["POSTGRES_PASSWORD"])
        command = ["mvn", "-N", "-B", "-ntp", "-Pdatabase-bootstrap"]
        subprocess.run(command + ["liquibase:validate", "liquibase:update", "liquibase:status"],
                       cwd=root, env=environment, check=True)
        # Password rotation/provisioning is not versioned schema data. psql reads
        # generated credentials from environment; they are not command-line values.
        sql = "\\getenv runtime_password DB_PASSWORD\n\\getenv migration_password MIGRATION_DB_PASSWORD\n"
        for owner in owners:
            sql += f"ALTER ROLE fresveg_{owner} PASSWORD :'runtime_password';\n"
            sql += f"ALTER ROLE fresveg_{owner}_migrator PASSWORD :'migration_password';\n"
        subprocess.run(["docker", "exec", "-i", "--env", "DB_PASSWORD", "--env", "MIGRATION_DB_PASSWORD", postgres,
                        "psql", "-U", "fresveg_bootstrap", "-d", "fresveg", "-v", "ON_ERROR_STOP=1"],
                       input=sql, env=environment, text=True, stdout=subprocess.DEVNULL, check=True)
        # Sequential startup keeps this a reproducible smoke check on small Docker VMs.
        histories = {}
        def history(owner):
            return docker("exec", postgres, "psql", "-U", "fresveg_bootstrap", "-d", "fresveg", "-Atc",
                          f"SELECT string_agg(id || ':' || filename || ':' || md5sum || ':' || dateexecuted::text, ',' ORDER BY orderexecuted) FROM {owner}.databasechangelog")
        for module in modules:
            for profile in ["default", "local"]:
                check(module, profile, network, environment)
                if module != "api-gateway":
                    owner = module.removesuffix("-service")
                    current = history(owner)
                    if owner in histories:
                        assert current == histories[owner], (owner, "Migration history changed on repeat startup")
                    histories[owner] = current
        subprocess.run(command + ["liquibase:update", "liquibase:status"], cwd=root, env=environment, check=True)
        for owner in owners:
            assert history(owner) == histories[owner], (owner, "Root rerun changed service history")
            count = docker("exec", postgres, "psql", "-U", "fresveg_bootstrap", "-d", "fresveg", "-Atc",
                           f"SELECT count(*) FROM {owner}.databasechangelog")
            expected = {"account": "2", "catalog": "2", "supply": "3", "commerce": "5", "fulfillment": "2"}[owner]
            assert count == expected, (owner, count)
        assert docker("exec", postgres, "psql", "-U", "fresveg_bootstrap", "-d", "fresveg", "-Atc",
                      "SELECT count(*) FROM public.fresveg_bootstrap_changelog") == "6"
        print("PASS: root Maven bootstrap, service startups and root rerun keep separate unchanged migration histories.", flush=True)
    finally:
        subprocess.run(["docker", "rm", "--force", postgres], stdout=subprocess.DEVNULL, check=True)
        subprocess.run(["docker", "network", "rm", network], stdout=subprocess.DEVNULL, check=True)


if __name__ == "__main__":
    validate()
