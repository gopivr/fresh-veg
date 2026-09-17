# =============================================================================
# FresVeg Platform - Unified Database Migration Script
# Runs Liquibase to create all 5 schemas and 37 service tables in PostgreSQL
# =============================================================================

param (
    [string]$DbUrl = "jdbc:postgresql://localhost:5432/fresveg",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "postgres"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$FreshVegDir = Split-Path -Parent $ScriptDir

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " FresVeg Platform - Liquibase Database Migrations" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Target Database URL : $DbUrl"
Write-Host "Database User       : $DbUser"
Write-Host "Changelog           : database/db.changelog-unified.yaml"
Write-Host "============================================================"

Push-Location $FreshVegDir
try {
    Write-Host "`n[1/1] Running Liquibase migration via Maven..." -ForegroundColor Yellow
    mvn -N -Pdatabase-unified liquibase:update `
        "-Ddb.url=$DbUrl" `
        "-Ddb.user=$DbUser" `
        "-Ddb.password=$DbPassword"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n>>> Liquibase migrations completed successfully! <<<" -ForegroundColor Green
        Write-Host "Created all 5 schemas (account, catalog, supply, commerce, fulfillment) and 37 service tables." -ForegroundColor Green
    } else {
        Write-Host "`n>>> Liquibase migration failed with exit code $LASTEXITCODE. <<<" -ForegroundColor Red
    }
} finally {
    Pop-Location
}
