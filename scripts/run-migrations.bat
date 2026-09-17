@echo off
REM =============================================================================
REM FresVeg Platform - Unified Database Migration Script
REM Runs Liquibase to create all 5 schemas and 37 service tables in PostgreSQL
REM =============================================================================

setlocal enabledelayedexpansion

set DB_URL=jdbc:postgresql://localhost:5432/fresveg
set DB_USER=postgres
set DB_PASSWORD=postgres

if not "%~1"=="" set DB_URL=%~1
if not "%~2"=="" set DB_USER=%~2
if not "%~3"=="" set DB_PASSWORD=%~3

echo ============================================================
echo  FresVeg Platform - Liquibase Database Migrations
echo ============================================================
echo Target Database URL : %DB_URL%
echo Database User       : %DB_USER%
echo Changelog           : database/db.changelog-unified.yaml
echo ============================================================

cd /d "%~dp0\.."
mvn -N -Pdatabase-unified liquibase:update -Ddb.url=%DB_URL% -Ddb.user=%DB_USER% -Ddb.password=%DB_PASSWORD%

if %ERRORLEVEL% equ 0 (
    echo.
    echo ^>^>^> Liquibase migrations completed successfully! ^<^<^<
    echo Created all 5 schemas (account, catalog, supply, commerce, fulfillment) and 37 service tables.
) else (
    echo.
    echo ^>^>^> Liquibase migration failed with error code %ERRORLEVEL%. ^<^<^<
)

endlocal
