#!/usr/bin/env bash
# =============================================================================
# 00-env.sh — Shared environment variables for the Public Land Map data pipeline
#
# Source this file from other pipeline scripts:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "${SCRIPT_DIR}/00-env.sh"
#
# All variables can be overridden via environment before running.
# =============================================================================

# Resolve SCRIPT_DIR relative to the sourcing script, not this file
_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load backend/.env if present (the same file docker-compose reads)
if [[ -f "${_ENV_DIR}/../.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${_ENV_DIR}/../.env"
    set +a
fi

# Directory for raw downloaded data (large files, gitignored)
export DATA_DIR="${DATA_DIR:-${_ENV_DIR}/../data}"

# Directory for generated MBTiles (gitignored)
export TILES_DIR="${TILES_DIR:-${_ENV_DIR}/../tiles}"

# PostgreSQL connection parameters. 127.0.0.1 matches the loopback-only port mapping in docker-compose.yml.
export PG_HOST="${PG_HOST:-127.0.0.1}"
export PG_PORT="${PG_PORT:-5432}"
export PG_DB="${PG_DB:-publiclandmap}"
export PG_USER="${PG_USER:-publiclandmap}"
export PG_PASS="${PG_PASSWORD:?Set PG_PASSWORD (copy backend/.env.example to backend/.env)}"

# The password goes to psql and ogr2ogr (libpq) through the environment, never on a command line,
# where other users on the machine could read it with ps.
export PGPASSWORD="${PG_PASS}"

# OGR-compatible connection string for ogr2ogr -f PostgreSQL (no password; PGPASSWORD supplies it)
export PG_CONN="PG:host=${PG_HOST} port=${PG_PORT} dbname=${PG_DB} user=${PG_USER}"

# psql connection string (no password; PGPASSWORD supplies it)
export PSQL_CONN="postgresql://${PG_USER}@${PG_HOST}:${PG_PORT}/${PG_DB}"

# ArcGIS REST API pagination batch size
export ARCGIS_PAGE_SIZE="${ARCGIS_PAGE_SIZE:-2000}"

# Number of parallel download workers (for wget/curl)
export DOWNLOAD_WORKERS="${DOWNLOAD_WORKERS:-4}"

# Ensure output directories exist
mkdir -p "${DATA_DIR}" "${TILES_DIR}"

echo "[env] DATA_DIR=${DATA_DIR}"
echo "[env] TILES_DIR=${TILES_DIR}"
echo "[env] PG_HOST=${PG_HOST}:${PG_PORT}/${PG_DB}"
