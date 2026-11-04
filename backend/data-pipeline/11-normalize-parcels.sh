#!/usr/bin/env bash
# =============================================================================
# 11-normalize-parcels.sh
#
# Post-import cleanup and normalization for parcel data:
#   1. Trim whitespace from text fields
#   2. Normalize county names to title case
#   3. Map land_use_code to land_use_desc using a lookup table
#   4. Remove exact duplicates (same parcel_number + county + state)
#   5. Update data_metadata timestamps
#
# Prerequisites:
#   - PostGIS running
#   - 10-import-to-postgis.sh has completed
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

echo "================================================================="
echo "[normalize] Starting parcel data normalization..."
echo "================================================================="

# ---------------------------------------------------------------------------
# 1. Trim whitespace from all text fields
# ---------------------------------------------------------------------------
echo "[normalize] Step 1: Trimming whitespace..."
run_sql "
    UPDATE parcels SET
        parcel_number = TRIM(parcel_number),
        owner_name    = TRIM(owner_name),
        county        = TRIM(county),
        land_use_code = TRIM(land_use_code),
        land_use_desc = TRIM(land_use_desc)
    WHERE
        parcel_number != TRIM(parcel_number)
        OR owner_name != TRIM(owner_name)
        OR county != TRIM(county)
        OR land_use_code != TRIM(land_use_code)
        OR land_use_desc != TRIM(land_use_desc);
"

# ---------------------------------------------------------------------------
# 2. Normalize county names to consistent title case
# ---------------------------------------------------------------------------
echo "[normalize] Step 2: Normalizing county names..."
run_sql "
    UPDATE parcels SET county = INITCAP(LOWER(county))
    WHERE county IS NOT NULL AND county != INITCAP(LOWER(county));
"

# ---------------------------------------------------------------------------
# 3. Normalize empty strings to NULL for cleaner data
# ---------------------------------------------------------------------------
echo "[normalize] Step 3: Converting empty strings to NULL..."
run_sql "
    UPDATE parcels SET
        owner_name    = NULLIF(owner_name, ''),
        land_use_code = NULLIF(land_use_code, ''),
        land_use_desc = NULLIF(land_use_desc, '');
"

# ---------------------------------------------------------------------------
# 4. Map common land_use_code values to descriptions
#    These are generalized codes — exact mappings depend on county assessor data
# ---------------------------------------------------------------------------
echo "[normalize] Step 4: Mapping land use codes to descriptions..."
run_sql "
    UPDATE parcels SET land_use_desc = CASE
        WHEN land_use_code IN ('100','110','111','SFR') THEN 'Single Family Residential'
        WHEN land_use_code IN ('200','210','MFR','APT') THEN 'Multi-Family Residential'
        WHEN land_use_code IN ('300','310','COM','RET') THEN 'Commercial'
        WHEN land_use_code IN ('400','410','IND','MFG') THEN 'Industrial'
        WHEN land_use_code IN ('500','510','AGR','AG','FARM') THEN 'Agricultural'
        WHEN land_use_code IN ('600','610','TIM','FOR','FOREST') THEN 'Timber/Forest'
        WHEN land_use_code IN ('700','710','VAC','VACANT') THEN 'Vacant Land'
        WHEN land_use_code IN ('800','810','GOV','PUB','EXEMPT') THEN 'Government/Public'
        WHEN land_use_code IN ('900','910','REC','PARK') THEN 'Recreation/Park'
        WHEN land_use_code IN ('MH','MHP','MOBILE') THEN 'Mobile Home'
        WHEN land_use_code IN ('MIN','MINING') THEN 'Mining/Resources'
        WHEN land_use_code IN ('UTIL','UTL') THEN 'Utility'
        WHEN land_use_code IN ('TRANS','RR','RAIL') THEN 'Transportation'
        WHEN land_use_code IN ('WAT','WATER') THEN 'Water/Riparian'
        ELSE land_use_desc  -- Keep existing if no match
    END
    WHERE land_use_code IS NOT NULL AND land_use_desc IS NULL;
"

# ---------------------------------------------------------------------------
# 5. Deduplicate: keep one record per (parcel_number, county, state)
#    When duplicates exist, keep the one with the largest acreage (most likely correct)
# ---------------------------------------------------------------------------
echo "[normalize] Step 5: Deduplicating parcels..."

DUPE_COUNT=$(psql "${PSQL_CONN}" -t -c "
    SELECT COUNT(*) FROM (
        SELECT parcel_number, county, state, COUNT(*) AS cnt
        FROM parcels
        WHERE parcel_number != ''
        GROUP BY parcel_number, county, state
        HAVING COUNT(*) > 1
    ) dupes;
")
DUPE_COUNT=$(echo "${DUPE_COUNT}" | tr -d ' ')
echo "[normalize] Found ${DUPE_COUNT} groups of duplicate parcels."

if [[ "${DUPE_COUNT}" -gt 0 ]]; then
    run_sql "
        DELETE FROM parcels
        WHERE id IN (
            SELECT id FROM (
                SELECT id,
                    ROW_NUMBER() OVER (
                        PARTITION BY parcel_number, county, state
                        ORDER BY COALESCE(acreage,0) DESC, id
                    ) AS rn
                FROM parcels
                WHERE parcel_number != ''
            ) ranked
            WHERE rn > 1
        );
    "
    REMAINING=$(psql "${PSQL_CONN}" -t -c "SELECT COUNT(*) FROM parcels;")
    echo "[normalize] After dedup: ${REMAINING} parcels remaining."
fi

# ---------------------------------------------------------------------------
# 6. Ensure centroid values are populated
# ---------------------------------------------------------------------------
echo "[normalize] Step 6: Recomputing missing centroids..."
run_sql "
    UPDATE parcels SET
        centroid_lat = ST_Y(ST_Centroid(geom)),
        centroid_lng = ST_X(ST_Centroid(geom))
    WHERE geom IS NOT NULL
      AND (centroid_lat = 0 OR centroid_lng = 0
           OR centroid_lat IS NULL OR centroid_lng IS NULL);
"

# ---------------------------------------------------------------------------
# 7. Validate geometry — fix any invalid geometries
# ---------------------------------------------------------------------------
echo "[normalize] Step 7: Fixing invalid geometries..."
run_sql "
    UPDATE parcels SET geom = ST_MakeValid(geom)
    WHERE NOT ST_IsValid(geom);
"

# ---------------------------------------------------------------------------
# 8. Update metadata
# ---------------------------------------------------------------------------
echo "[normalize] Step 8: Updating metadata..."
run_sql "
    UPDATE data_metadata
    SET value = NOW()::TEXT, updated_at = NOW()
    WHERE key = 'last_updated';
"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "[normalize] Normalization Summary"
echo "================================================================="
psql "${PSQL_CONN}" -c "
    SELECT state, COUNT(*) AS total_parcels,
           COUNT(owner_name) AS with_owner,
           COUNT(land_use_desc) AS with_land_use,
           ROUND(AVG(acreage)::NUMERIC, 2) AS avg_acres
    FROM parcels
    GROUP BY state
    ORDER BY state;
"

echo "[normalize] Normalization complete."
