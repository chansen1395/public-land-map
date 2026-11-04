#!/usr/bin/env bash
# =============================================================================
# 12-clip-to-states.sh
#
# Clips all imported spatial data to WA and OR state boundaries.
# This removes any features that accidentally extend beyond state lines
# (e.g., USFS roads that cross into Idaho or California).
#
# Steps:
#   1. Download WA and OR state boundary polygons from Census TIGER/Line
#   2. Import boundaries into a temp table
#   3. Clip each spatial table to the boundary union
#   4. Remove features entirely outside WA/OR
#
# Prerequisites:
#   - PostGIS running
#   - 10-import and 11-normalize have completed
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------
run_sql() {
    psql "${PSQL_CONN}" -c "$1"
}

# ---------------------------------------------------------------------------
# Download state boundaries from Census TIGER/Line (2023 vintage)
# We use the "States and Equivalent" shapefile
# ---------------------------------------------------------------------------
BOUNDARY_DIR="${DATA_DIR}/boundaries"
BOUNDARY_FILE="${BOUNDARY_DIR}/tl_2023_us_state.shp"
BOUNDARY_ZIP="${BOUNDARY_DIR}/tl_2023_us_state.zip"
TIGER_URL="https://www2.census.gov/geo/tiger/TIGER2023/STATE/tl_2023_us_state.zip"

mkdir -p "${BOUNDARY_DIR}"

if [[ -f "${BOUNDARY_FILE}" ]]; then
    echo "[clip] State boundary shapefile already exists."
else
    if [[ -f "${BOUNDARY_ZIP}" ]]; then
        echo "[clip] Boundary ZIP already downloaded."
    else
        echo "[clip] Downloading US state boundaries from Census TIGER..."
        # --fail so an HTTP error page is never saved as the archive; download under a temporary name
        curl --fail -L -o "${BOUNDARY_ZIP}.part" \
            --progress-bar \
            --retry 3 \
            "${TIGER_URL}"
        mv "${BOUNDARY_ZIP}.part" "${BOUNDARY_ZIP}"
    fi

    echo "[clip] Extracting shapefile..."
    unzip -o -d "${BOUNDARY_DIR}" "${BOUNDARY_ZIP}"

    if [[ ! -f "${BOUNDARY_FILE}" ]]; then
        echo "[clip] ERROR: Expected shapefile not found after extraction."
        ls -la "${BOUNDARY_DIR}/"
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# Import WA and OR boundaries into a temp table in PostGIS
# ---------------------------------------------------------------------------
echo "[clip] Importing WA/OR boundaries into PostGIS..."
run_sql "DROP TABLE IF EXISTS _state_boundaries;"

ogr2ogr \
    -f "PostgreSQL" "${PG_CONN}" \
    "${BOUNDARY_FILE}" \
    -nln "_state_boundaries" \
    -overwrite \
    -where "STUSPS IN ('WA','OR')" \
    -t_srs EPSG:4326 \
    -lco GEOMETRY_NAME=geom \
    -nlt PROMOTE_TO_MULTI

# Create the merged WA+OR boundary for clipping
echo "[clip] Building merged WA+OR clip boundary..."
run_sql "
    DROP TABLE IF EXISTS _clip_boundary;
    CREATE TABLE _clip_boundary AS
    SELECT ST_Union(geom) AS geom FROM _state_boundaries;
    CREATE INDEX ON _clip_boundary USING GIST (geom);
"

# ---------------------------------------------------------------------------
# Clip parcels — remove any outside WA/OR boundaries
# ---------------------------------------------------------------------------
echo "[clip] Clipping parcels to WA/OR boundaries..."
BEFORE=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM parcels;")

run_sql "
    DELETE FROM parcels p
    WHERE NOT EXISTS (
        SELECT 1 FROM _clip_boundary b
        WHERE ST_Intersects(p.geom, b.geom)
    )
    AND p.geom IS NOT NULL;
"

AFTER=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM parcels;")
echo "[clip] Parcels: ${BEFORE} → ${AFTER} (removed $(( $(echo ${BEFORE}) - $(echo ${AFTER}) )))"

# ---------------------------------------------------------------------------
# Clip public lands — only keep features intersecting WA/OR
# ---------------------------------------------------------------------------
echo "[clip] Clipping public lands to WA/OR boundaries..."
BEFORE=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM public_lands;")

run_sql "
    DELETE FROM public_lands pl
    WHERE NOT EXISTS (
        SELECT 1 FROM _clip_boundary b
        WHERE ST_Intersects(pl.geom, b.geom)
    )
    AND pl.geom IS NOT NULL;
"

AFTER=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM public_lands;")
echo "[clip] Public lands: ${BEFORE} → ${AFTER} (removed $(( $(echo ${BEFORE}) - $(echo ${AFTER}) )))"

# ---------------------------------------------------------------------------
# Clip roads and trails
# ---------------------------------------------------------------------------
echo "[clip] Clipping roads/trails to WA/OR boundaries..."
BEFORE=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM roads_trails;")

run_sql "
    DELETE FROM roads_trails rt
    WHERE NOT EXISTS (
        SELECT 1 FROM _clip_boundary b
        WHERE ST_Intersects(rt.geom, b.geom)
    )
    AND rt.geom IS NOT NULL;
"

AFTER=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM roads_trails;")
echo "[clip] Roads/trails: ${BEFORE} → ${AFTER} (removed $(( $(echo ${BEFORE}) - $(echo ${AFTER}) )))"

# ---------------------------------------------------------------------------
# Clip recreation sites
# ---------------------------------------------------------------------------
echo "[clip] Clipping recreation sites to WA/OR boundaries..."
BEFORE=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM recreation_sites;")

run_sql "
    DELETE FROM recreation_sites rs
    WHERE NOT EXISTS (
        SELECT 1 FROM _clip_boundary b
        WHERE ST_Intersects(rs.geom, b.geom)
    )
    AND rs.geom IS NOT NULL;
"

AFTER=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM recreation_sites;")
echo "[clip] Recreation sites: ${BEFORE} → ${AFTER} (removed $(( $(echo ${BEFORE}) - $(echo ${AFTER}) )))"

# ---------------------------------------------------------------------------
# Clip PLSS grid
# ---------------------------------------------------------------------------
echo "[clip] Clipping PLSS grid to WA/OR boundaries..."
BEFORE=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM plss_grid;")

run_sql "
    DELETE FROM plss_grid pg
    WHERE NOT EXISTS (
        SELECT 1 FROM _clip_boundary b
        WHERE ST_Intersects(pg.geom, b.geom)
    )
    AND pg.geom IS NOT NULL;
"

AFTER=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM plss_grid;")
echo "[clip] PLSS grid: ${BEFORE} → ${AFTER} (removed $(( $(echo ${BEFORE}) - $(echo ${AFTER}) )))"

# ---------------------------------------------------------------------------
# Assign state field to roads_trails and recreation_sites based on boundary
# ---------------------------------------------------------------------------
echo "[clip] Assigning state field based on spatial location..."
run_sql "
    UPDATE roads_trails rt SET state = sb.stusps
    FROM _state_boundaries sb
    WHERE ST_Intersects(ST_Centroid(rt.geom), sb.geom)
      AND (rt.state IS NULL OR rt.state = '');

    UPDATE recreation_sites rs SET state = sb.stusps
    FROM _state_boundaries sb
    WHERE ST_Intersects(rs.geom, sb.geom)
      AND (rs.state IS NULL OR rs.state = '');
"

# ---------------------------------------------------------------------------
# Cleanup temp tables
# ---------------------------------------------------------------------------
echo "[clip] Cleaning up temp tables..."
run_sql "
    DROP TABLE IF EXISTS _state_boundaries;
    DROP TABLE IF EXISTS _clip_boundary;
"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[clip] Clipping complete."
echo "================================================================="
psql "${PSQL_CONN}" -c "
    SELECT 'parcels' AS layer, state, COUNT(*) FROM parcels GROUP BY state
    UNION ALL
    SELECT 'public_lands', state, COUNT(*) FROM public_lands GROUP BY state
    UNION ALL
    SELECT 'roads_trails', state, COUNT(*) FROM roads_trails GROUP BY state
    UNION ALL
    SELECT 'recreation_sites', state, COUNT(*) FROM recreation_sites GROUP BY state
    UNION ALL
    SELECT 'plss_grid', state, COUNT(*) FROM plss_grid GROUP BY state
    ORDER BY 1, 2;
"
