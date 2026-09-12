#!/usr/bin/env python3
"""Smoke-test Compose in an isolated project; remove only this run's resources."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import uuid

root = Path(__file__).resolve().parents[1]
project = "fresveg-phase1-check-" + uuid.uuid4().hex[:12]
environment = dict(os.environ)
environment.update(
    POSTGRES_DB="fresveg",
    POSTGRES_USER="fresveg_check",
    POSTGRES_PASSWORD=secrets.token_urlsafe(32),
    POSTGRES_PORT="0",
    REDIS_PORT="0",
)
compose = ["docker", "compose", "--env-file", os.devnull,
           "--project-name", project, "--file", str(root / "docker-compose.yml")]


def run(*arguments, capture=False):
    return subprocess.run(compose + list(arguments), env=environment, check=True,
                          text=True, stdout=subprocess.PIPE if capture else None).stdout


try:
    run("config", "--quiet")
    run("up", "--detach", "--wait", "--wait-timeout", "120")
    status = [json.loads(line) for line in run("ps", "--format", "json", capture=True).splitlines()]
    assert {item["Service"] for item in status} == {"postgres", "redis"}, status
    assert all(item["Health"] == "healthy" for item in status), status
    sql = run("exec", "-T", "postgres", "psql", "-h", "127.0.0.1",
              "-U", environment["POSTGRES_USER"], "-d", environment["POSTGRES_DB"],
              "-Atc", "SELECT 1", capture=True)
    assert sql.strip() == "1", sql
    pong = run("exec", "-T", "redis", "redis-cli", "ping", capture=True)
    assert pong.strip() == "PONG", pong
    print(run("exec", "-T", "postgres", "postgres", "--version", capture=True).strip())
    print(run("exec", "-T", "redis", "redis-server", "--version", capture=True).strip())
    print("PASS: Compose starts healthy PostgreSQL and Redis; SELECT 1 and PING succeed.")
finally:
    # The generated project is unique to this invocation. Never touch developer volumes.
    run("down", "--volumes", "--remove-orphans")
