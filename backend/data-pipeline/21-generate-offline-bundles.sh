#!/usr/bin/env bash
# =============================================================================
# 21-generate-offline-bundles.sh
#
# Generates per-county MBTiles bundles for offline map downloads.
#
# For each county in WA and OR:
#   1. Query county bounding box from PostGIS (from parcels table)
#   2. Extract tiles from each existing tileset for that bbox
#   3. Merge all layers into a single county.mbtiles
#   4. Register the county bundle in the offline_regions table
#
# Uses tile-join (part of tippecanoe) to extract and merge tilesets by bbox.
#
# Prerequisites:
#   - tippecanoe (includes tile-join) installed
#   - 20-generate-tiles.sh has completed
#   - PostGIS running with data
#
# Output: $TILES_DIR/offline/<state>-<county>.mbtiles
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

OFFLINE_DIR="${TILES_DIR}/offline"
mkdir -p "${OFFLINE_DIR}"

# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------
run_sql() {
    psql "${PSQL_CONN}" -c "$1"
}

query_val() {
    psql "${PSQL_CONN}" -t -A -c "$1"
}

# ---------------------------------------------------------------------------
# Get list of counties with their bounding boxes
# We compute bbox from parcel centroids grouped by county + state
# ---------------------------------------------------------------------------
echo "[offline] Querying counties and bounding boxes from PostGIS..."

# Clear existing offline_regions for fresh generation
run_sql "TRUNCATE TABLE offline_regions;"

# Query all county/state combos with their bbox
COUNTIES_CSV=$(query_val "
    SELECT
        state || '|' || county || '|' ||
        MIN(centroid_lat)::TEXT || '|' ||
        MIN(centroid_lng)::TEXT || '|' ||
        MAX(centroid_lat)::TEXT || '|' ||
        MAX(centroid_lng)::TEXT
    FROM parcels
    WHERE county IS NOT NULL AND county != ''
    GROUP BY state, county
    ORDER BY state, county;
")

if [[ -z "${COUNTIES_CSV}" ]]; then
    echo "[offline] ERROR: No counties found in parcels table."
    exit 1
fi

COUNTY_COUNT=$(echo "${COUNTIES_CSV}" | wc -l)
echo "[offline] Found ${COUNTY_COUNT} counties to process."

# ---------------------------------------------------------------------------
# Source tilesets to include in offline bundles
# ---------------------------------------------------------------------------
TILESET_FILES=()
for tileset in "public-lands.mbtiles" "plss.mbtiles" "roads-trails.mbtiles" "recreation-pois.mbtiles"; do
    TILESET_PATH="${TILES_DIR}/${tileset}"
    if [[ -f "${TILESET_PATH}" ]]; then
        TILESET_FILES+=("${TILESET_PATH}")
    else
        echo "[offline] WARNING: Missing tileset: ${tileset} (will skip in bundles)"
    fi
done

echo "[offline] Base tilesets for bundles: ${#TILESET_FILES[@]} files"

# ---------------------------------------------------------------------------
# Generate per-county bundles
# ---------------------------------------------------------------------------
PROCESSED=0
SKIPPED=0

while IFS= read -r line; do
    # Parse state|county|min_lat|min_lng|max_lat|max_lng
    IFS='|' read -r STATE COUNTY MIN_LAT MIN_LNG MAX_LAT MAX_LNG <<< "${line}"

    # Sanitize county name for filename (lowercase, replace spaces with hyphens)
    COUNTY_SLUG=$(echo "${COUNTY}" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd 'a-z0-9-')
    STATE_LOWER=$(echo "${STATE}" | tr '[:upper:]' '[:lower:]')
    BUNDLE_ID="${STATE_LOWER}-${COUNTY_SLUG}"
    BUNDLE_FILE="${OFFLINE_DIR}/${BUNDLE_ID}.mbtiles"

    # Skip if already generated
    if [[ -f "${BUNDLE_FILE}" ]]; then
        echo "[offline] Bundle exists: ${BUNDLE_ID} — skipping"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    echo "[offline] Generating ${BUNDLE_ID} (bbox: ${MIN_LNG},${MIN_LAT} → ${MAX_LNG},${MAX_LAT})..."

    # Add parcel tileset for this state
    PARCEL_TILESET="${TILES_DIR}/parcels-${STATE_LOWER}.mbtiles"
    INPUT_FILES=()
    for f in "${TILESET_FILES[@]}"; do
        INPUT_FILES+=("$f")
    done
    if [[ -f "${PARCEL_TILESET}" ]]; then
        INPUT_FILES+=("${PARCEL_TILESET}")
    fi

    if [[ ${#INPUT_FILES[@]} -eq 0 ]]; then
        echo "[offline] WARNING: No tilesets available for ${BUNDLE_ID}, skipping."
        continue
    fi

    # Use tile-join to extract tiles within the county bbox and merge
    # tile-join --bbox=min_lng,min_lat,max_lng,max_lat
    # Add a small buffer (~0.05 degrees ≈ 5km) around the county bbox
    BUFFER="0.05"
    BBOX_MIN_LNG=$(python3 -c "print(${MIN_LNG} - ${BUFFER})")
    BBOX_MIN_LAT=$(python3 -c "print(${MIN_LAT} - ${BUFFER})")
    BBOX_MAX_LNG=$(python3 -c "print(${MAX_LNG} + ${BUFFER})")
    BBOX_MAX_LAT=$(python3 -c "print(${MAX_LAT} + ${BUFFER})")

    tile-join \
        --output="${BUNDLE_FILE}" \
        --force \
        --no-tile-compression \
        --bbox="${BBOX_MIN_LNG},${BBOX_MIN_LAT},${BBOX_MAX_LNG},${BBOX_MAX_LAT}" \
        "${INPUT_FILES[@]}" \
        2>/dev/null || {
            echo "[offline] WARNING: tile-join failed for ${BUNDLE_ID}, skipping."
            rm -f "${BUNDLE_FILE}"
            continue
        }

    if [[ ! -f "${BUNDLE_FILE}" ]]; then
        echo "[offline] WARNING: No output for ${BUNDLE_ID}, skipping."
        continue
    fi

    # Record file size
    SIZE_BYTES=$(stat -f%z "${BUNDLE_FILE}" 2>/dev/null || stat --printf="%s" "${BUNDLE_FILE}" 2>/dev/null || echo "0")

    # Insert into offline_regions
    run_sql "
        INSERT INTO offline_regions (id, name, state, county, min_lat, min_lng, max_lat, max_lng,
                                      min_zoom, max_zoom, size_bytes, tile_file_path)
        VALUES (
            '${BUNDLE_ID}',
            '${COUNTY}, ${STATE}',
            '${STATE}',
            '${COUNTY}',
            ${MIN_LAT}, ${MIN_LNG}, ${MAX_LAT}, ${MAX_LNG},
            8, 15,
            ${SIZE_BYTES},
            'offline/${BUNDLE_ID}.mbtiles'
        )
        ON CONFLICT (id) DO UPDATE SET
            size_bytes = EXCLUDED.size_bytes,
            tile_file_path = EXCLUDED.tile_file_path;
    "

    SIZE_HUMAN=$(du -h "${BUNDLE_FILE}" | cut -f1)
    echo "[offline] Generated: ${BUNDLE_ID} (${SIZE_HUMAN})"
    PROCESSED=$((PROCESSED + 1))

done <<< "${COUNTIES_CSV}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[offline] Offline Bundle Generation Summary"
echo "================================================================="
echo "  Processed: ${PROCESSED} counties"
echo "  Skipped (existing): ${SKIPPED} counties"
echo "  Output directory: ${OFFLINE_DIR}/"
echo ""

TOTAL_SIZE=$(du -sh "${OFFLINE_DIR}" 2>/dev/null | cut -f1 || echo "0")
echo "  Total offline bundle size: ${TOTAL_SIZE}"

REGION_COUNT=$(query_val "SELECT COUNT(*) FROM offline_regions;")
echo "  Registered in offline_regions: ${REGION_COUNT} entries"

echo ""
echo "[offline] Bundle generation complete."
