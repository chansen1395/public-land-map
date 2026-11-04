#!/usr/bin/env bash
# Import PNW public lands into PostGIS public_lands.
# Sources:
# - Washington: WA DNR Managed Lands + Major Public Lands Non-DNR + wilderness + tribal layers
# - Oregon: Oregon PAD-US 4.1 FeatureServer
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "${SCRIPT_DIR}/../.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${SCRIPT_DIR}/../.env"
    set +a
fi

CONTAINER_NAME="${CONTAINER_NAME:-plm-postgis}"
PG_DB="${PG_DB:-publiclandmap}"
PG_USER="${PG_USER:-publiclandmap}"
PG_PASS="${PG_PASSWORD:?Set PG_PASSWORD (copy backend/.env.example to backend/.env)}"
HOST_CACHE_DIR="${HOST_CACHE_DIR:-${SCRIPT_DIR}/../data/public-lands-cache}"
CONTAINER_CACHE_DIR="/tmp/pnw_public_lands"
# The password reaches ogr2ogr and psql through PGPASSWORD (exported here, passed to the container with
# "docker exec -e PGPASSWORD"), never on a command line.
export PGPASSWORD="${PG_PASS}"
PG_CONN="PG:dbname=${PG_DB} user=${PG_USER}"

mkdir -p "$HOST_CACHE_DIR/wa" "$HOST_CACHE_DIR/or"

require_container() {
    if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
        echo "ERROR: Docker container '$CONTAINER_NAME' is not running. Start backend/docker-compose.yml first." >&2
        exit 1
    fi
}

extract_count() {
    sed -n 's/.*"count"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}

download_page() {
    local url="$1"
    local outfile="$2"
    mkdir -p "$(dirname "$outfile")"
    if [[ -s "$outfile" ]] && ! grep -q '"error"' "$outfile"; then
        echo "  cached $(basename "$outfile") ($(wc -c < "$outfile") bytes)"
        return
    fi

    curl -s --fail --max-time 240 -o "$outfile" "$url"
    if grep -q '"error"' "$outfile"; then
        echo "ERROR: ArcGIS returned an error for $outfile" >&2
        head -20 "$outfile" >&2
        exit 1
    fi
    echo "  downloaded $(basename "$outfile") ($(wc -c < "$outfile") bytes)"
}

download_paged_layer() {
    local label="$1"
    local base_url="$2"
    local out_dir="$3"
    local fields="$4"
    local page_size="$5"
    local order_by="$6"
    local count
    mkdir -p "$out_dir"
    count=$(curl -s --fail --max-time 60 "${base_url}/query?where=1%3D1&returnCountOnly=true&f=json" | extract_count)
    if [[ -z "$count" ]]; then
        echo "ERROR: Could not read feature count for $label" >&2
        exit 1
    fi

    echo "=== Downloading $label ($count features) ==="
    local offset=0
    local page=1
    while [[ "$offset" -lt "$count" ]]; do
        local outfile="$out_dir/page_${page}.json"
        local url="${base_url}/query?where=1%3D1&outFields=${fields}&outSR=4326&returnGeometry=true&f=json&resultOffset=${offset}&resultRecordCount=${page_size}"
        if [[ -n "$order_by" ]]; then
            url="${url}&orderByFields=${order_by}"
        fi
        echo "$label page $page (offset=$offset)"
        download_page "$url" "$outfile"
        offset=$((offset + page_size))
        page=$((page + 1))
    done
}

import_json_pages() {
    local label="$1"
    local table="$2"
    local container_dir="$3"
    local page_count="$4"

    echo "=== Importing $label into $table ==="
    for page in $(seq 1 "$page_count"); do
        local mode="-append"
        if [[ "$page" -eq 1 ]]; then
            mode="-overwrite"
        fi
        MSYS_NO_PATHCONV=1 docker exec -e PGPASSWORD "$CONTAINER_NAME" ogr2ogr -f "PostgreSQL" \
            "$PG_CONN" \
            "${container_dir}/page_${page}.json" \
            -nln "$table" \
            "$mode" \
            -nlt MULTIPOLYGON \
            -t_srs EPSG:4326 \
            -lco GEOMETRY_NAME=geom
    done
}

import_single_json() {
    local label="$1"
    local table="$2"
    local container_file="$3"

    echo "=== Importing $label into $table ==="
    MSYS_NO_PATHCONV=1 docker exec -e PGPASSWORD "$CONTAINER_NAME" ogr2ogr -f "PostgreSQL" \
        "$PG_CONN" \
        "$container_file" \
        -nln "$table" \
        -append \
        -nlt MULTIPOLYGON \
        -t_srs EPSG:4326
}

require_container

WA_BASE="https://gis.dnr.wa.gov/site3/rest/services/Public_Boundaries/WADNR_PUBLIC_Major_Public_Lands_NonDNR/MapServer"
WA_FIELDS="NAME%2COWNER%2CMANAGER%2CMANAGEMENT_TYPE"
download_paged_layer "WA Non-DNR Major Public Lands" "${WA_BASE}/1" "$HOST_CACHE_DIR/wa/main" "$WA_FIELDS" 1000 "OBJECTID"
mkdir -p "$HOST_CACHE_DIR/wa/extra"
download_page "${WA_BASE}/0/query?where=1%3D1&outFields=${WA_FIELDS}&outSR=4326&returnGeometry=true&f=json" "$HOST_CACHE_DIR/wa/extra/wilderness.json"
download_page "${WA_BASE}/2/query?where=1%3D1&outFields=${WA_FIELDS}&outSR=4326&returnGeometry=true&f=json" "$HOST_CACHE_DIR/wa/extra/tribal.json"

WA_DNR_BASE="https://gis.dnr.wa.gov/site3/rest/services/Public_Boundaries/WADNR_PUBLIC_Managed_Lands/MapServer"
WA_DNR_SURFACE_FIELDS="OBJECTID%2CPARCEL_LABEL_NM%2CPARCEL_NM%2CSURFACE_TRUST_NM%2CCOUNTY_NM%2CREGION_NM"
WA_DNR_CONSERVATION_FIELDS="OBJECTID%2CJURISDICT_LABEL_NM%2CJURISDICT_NM%2CJURISDICT_TYPE_CD"
download_paged_layer "WA DNR Managed Surface Lands" "${WA_DNR_BASE}/1" "$HOST_CACHE_DIR/wa/dnr_surface" "$WA_DNR_SURFACE_FIELDS" 1000 "OBJECTID"
download_paged_layer "WA DNR NAPs and NRCAs" "${WA_DNR_BASE}/8" "$HOST_CACHE_DIR/wa/dnr_conservation" "$WA_DNR_CONSERVATION_FIELDS" 1000 "OBJECTID"

OR_BASE="https://services1.arcgis.com/CD5mKowwN6nIaqd8/arcgis/rest/services/library_pln_or_PADUS_2024/FeatureServer/75"
OR_FIELDS="OBJECTID%2CUnit_Nm%2CLoc_Nm%2CLoc_Ds%2CLoc_Mang%2CMang_Name%2Cd_Mang_Name%2Cd_Mang_Type%2Cd_Des_Tp%2Cd_Pub_Access%2Cd_State_Nm%2CGIS_Acres%2CPub_Access%2CDes_Tp%2CCategory%2COwn_Type%2Cd_Own_Type"
download_paged_layer "OR PAD-US 4.1" "$OR_BASE" "$HOST_CACHE_DIR/or/padus" "$OR_FIELDS" 2000 "OBJECTID"

echo "=== Copying cache into $CONTAINER_NAME ==="
MSYS_NO_PATHCONV=1 docker exec "$CONTAINER_NAME" rm -rf "$CONTAINER_CACHE_DIR"
docker cp "$HOST_CACHE_DIR" "$CONTAINER_NAME:$CONTAINER_CACHE_DIR"

WA_PAGE_COUNT=$(find "$HOST_CACHE_DIR/wa/main" -name 'page_*.json' | wc -l | tr -d ' ')
WA_DNR_SURFACE_PAGE_COUNT=$(find "$HOST_CACHE_DIR/wa/dnr_surface" -name 'page_*.json' | wc -l | tr -d ' ')
WA_DNR_CONSERVATION_PAGE_COUNT=$(find "$HOST_CACHE_DIR/wa/dnr_conservation" -name 'page_*.json' | wc -l | tr -d ' ')
OR_PAGE_COUNT=$(find "$HOST_CACHE_DIR/or/padus" -name 'page_*.json' | wc -l | tr -d ' ')

import_json_pages "WA Non-DNR public lands" "_tmp_wa_public_lands" "$CONTAINER_CACHE_DIR/wa/main" "$WA_PAGE_COUNT"
import_single_json "WA wilderness areas" "_tmp_wa_public_lands" "$CONTAINER_CACHE_DIR/wa/extra/wilderness.json"
import_single_json "WA tribal lands" "_tmp_wa_public_lands" "$CONTAINER_CACHE_DIR/wa/extra/tribal.json"
import_json_pages "WA DNR managed surface lands" "_tmp_wa_dnr_surface" "$CONTAINER_CACHE_DIR/wa/dnr_surface" "$WA_DNR_SURFACE_PAGE_COUNT"
import_json_pages "WA DNR NAPs and NRCAs" "_tmp_wa_dnr_conservation" "$CONTAINER_CACHE_DIR/wa/dnr_conservation" "$WA_DNR_CONSERVATION_PAGE_COUNT"
import_json_pages "OR PAD-US public lands" "_tmp_or_public_lands" "$CONTAINER_CACHE_DIR/or/padus" "$OR_PAGE_COUNT"

echo "=== Normalizing into public_lands ==="
MSYS_NO_PATHCONV=1 docker exec -i -e PGPASSWORD "$CONTAINER_NAME" psql -U "$PG_USER" -d "$PG_DB" <<'SQL'
ALTER TABLE public_lands ADD COLUMN IF NOT EXISTS agency_category TEXT;
CREATE INDEX IF NOT EXISTS idx_pl_agency_category ON public_lands(agency_category);

TRUNCATE public_lands;

WITH raw_public_lands AS (
    SELECT
        'WA-NON-DNR-' || ogc_fid AS id,
        COALESCE(NULLIF(TRIM(name), ''), manager || ' Land') AS unit_name,
        COALESCE(NULLIF(TRIM(manager), ''), NULLIF(TRIM(owner), ''), 'Unknown') AS managing_agency,
        COALESCE(NULLIF(TRIM(management_type), ''), 'Unknown') AS designation_type,
        CASE
            WHEN CONCAT_WS(' ', manager, owner, management_type) ILIKE '%Defense%'
                OR CONCAT_WS(' ', manager, owner, management_type) ILIKE '%Energy%'
                THEN 'Closed'
            ELSE 'Unknown'
        END AS public_access,
        'WA' AS state,
        NULLIF(TRIM(owner), '') AS source_owner_type,
        NULL::text AS source_manager_type,
        ST_Multi(ST_CollectionExtract(ST_MakeValid(geom), 3)) AS geom
    FROM _tmp_wa_public_lands
    WHERE geom IS NOT NULL

    UNION ALL

    SELECT
        'WA-DNR-SURFACE-' || ogc_fid AS id,
        COALESCE(
            NULLIF(TRIM(parcel_nm), ''),
            NULLIF(TRIM(parcel_label_nm), ''),
            NULLIF(TRIM(surface_trust_nm), '') || ' DNR Land',
            'WA DNR Managed Land'
        ) AS unit_name,
        'WA Department of Natural Resources' AS managing_agency,
        COALESCE(NULLIF(TRIM(surface_trust_nm), ''), 'DNR Managed Surface Land') AS designation_type,
        'Unknown' AS public_access,
        'WA' AS state,
        'State' AS source_owner_type,
        'State' AS source_manager_type,
        ST_Multi(ST_CollectionExtract(ST_MakeValid(geom), 3)) AS geom
    FROM _tmp_wa_dnr_surface
    WHERE geom IS NOT NULL

    UNION ALL

    SELECT
        'WA-DNR-CONSERVATION-' || ogc_fid AS id,
        COALESCE(NULLIF(TRIM(jurisdict_nm), ''), NULLIF(TRIM(jurisdict_label_nm), ''), 'WA DNR Conservation Area') AS unit_name,
        'WA Department of Natural Resources' AS managing_agency,
        CASE
            WHEN jurisdict_type_cd::text = '49009' THEN 'Natural Area Preserve'
            WHEN jurisdict_type_cd::text = '49010' THEN 'Natural Resource Conservation Area'
            ELSE 'DNR Conservation Area'
        END AS designation_type,
        'Unknown' AS public_access,
        'WA' AS state,
        'State' AS source_owner_type,
        'State' AS source_manager_type,
        ST_Multi(ST_CollectionExtract(ST_MakeValid(geom), 3)) AS geom
    FROM _tmp_wa_dnr_conservation
    WHERE geom IS NOT NULL

    UNION ALL

    SELECT
        'OR-PADUS-' || ogc_fid AS id,
        COALESCE(NULLIF(TRIM(unit_nm), ''), NULLIF(TRIM(loc_nm), ''), NULLIF(TRIM(loc_ds), ''), COALESCE(NULLIF(TRIM(d_mang_name), ''), NULLIF(TRIM(mang_name), ''), 'Public Land') || ' Land') AS unit_name,
        COALESCE(NULLIF(TRIM(d_mang_name), ''), NULLIF(TRIM(mang_name), ''), NULLIF(TRIM(loc_mang), ''), 'Unknown') AS managing_agency,
        COALESCE(NULLIF(TRIM(d_des_tp), ''), NULLIF(TRIM(des_tp), ''), NULLIF(TRIM(category), ''), 'Unknown') AS designation_type,
        CASE COALESCE(NULLIF(TRIM(d_pub_access), ''), NULLIF(TRIM(pub_access), ''), 'Unknown')
            WHEN 'Open Access' THEN 'Open'
            WHEN 'OA' THEN 'Open'
            WHEN 'Restricted Access' THEN 'Restricted'
            WHEN 'RA' THEN 'Restricted'
            WHEN 'Closed' THEN 'Closed'
            WHEN 'XA' THEN 'Closed'
            ELSE 'Unknown'
        END AS public_access,
        'OR' AS state,
        COALESCE(NULLIF(TRIM(d_own_type), ''), NULLIF(TRIM(own_type), '')) AS source_owner_type,
        COALESCE(NULLIF(TRIM(d_mang_type), ''), NULLIF(TRIM(category), '')) AS source_manager_type,
        ST_Multi(ST_CollectionExtract(ST_MakeValid(geom), 3)) AS geom
    FROM _tmp_or_public_lands
    WHERE geom IS NOT NULL
            AND (
                    COALESCE(d_own_type, own_type, '') ~* '(Federal|State|Local|Regional|American Indian|Joint|Territorial|Designation)'
                    OR COALESCE(d_mang_type, '') ~* '(Federal|State|Local|Regional|American Indian|Joint|Territorial|Designation)'
                    OR COALESCE(d_pub_access, pub_access, '') IN ('Open Access', 'OA', 'Restricted Access', 'RA')
            )
            AND NOT (
                    COALESCE(d_own_type, own_type, '') ILIKE '%Private%'
                    AND COALESCE(d_pub_access, pub_access, '') NOT IN ('Open Access', 'OA', 'Restricted Access', 'RA')
            )
), classified_source AS (
    SELECT
        *,
        CONCAT_WS(' ', unit_name, managing_agency, designation_type, public_access, source_owner_type, source_manager_type) AS classification_text
    FROM raw_public_lands
    WHERE geom IS NOT NULL AND NOT ST_IsEmpty(geom)
)
INSERT INTO public_lands (id, unit_name, managing_agency, agency_category, designation_type, public_access, state, gis_acres, centroid_lat, centroid_lng, geom, last_updated)
SELECT
    id,
    unit_name,
    managing_agency,
    CASE
        WHEN classification_text ILIKE '%Bureau of Land Management%' OR classification_text ~* '(^|[^A-Z])BLM([^A-Z]|$)' THEN 'BLM'
        WHEN classification_text ILIKE '%Forest Service%' OR classification_text ~* '(^|[^A-Z])USFS([^A-Z]|$)' THEN 'USFS'
        WHEN classification_text ILIKE '%National Park%' OR classification_text ~* '(^|[^A-Z])NPS([^A-Z]|$)' THEN 'National Park Service'
        WHEN classification_text ILIKE '%Fish and Wildlife%' OR classification_text ILIKE '%USFWS%' THEN 'Fish & Wildlife Service'
        WHEN classification_text ILIKE '%Army Corps%' OR classification_text ILIKE '%Corps of Engineers%' OR classification_text ILIKE '%Bureau of Reclamation%' THEN 'Army Corps / Reclamation'
        WHEN classification_text ILIKE '%Department of Energy%' OR classification_text ILIKE '%Dept of Energy%' OR classification_text ILIKE '%Defense%' OR classification_text ILIKE '%Military%' OR classification_text ILIKE '%Air Force%' OR classification_text ILIKE '%Navy%' THEN 'DOE / Defense'
        WHEN classification_text ILIKE '%Department of Natural Resources%' OR classification_text ILIKE '%DNR%' OR classification_text ILIKE '%State Forest%' OR classification_text ILIKE '%Common School%' OR classification_text ILIKE '%Department of Land%' THEN 'DNR / State Trust'
        WHEN classification_text ILIKE '%State Park%' THEN 'State Parks'
        WHEN classification_text ILIKE '%State Fish%' OR classification_text ILIKE '%State Wildlife%' OR classification_text ILIKE '%WDFW%' OR classification_text ILIKE '%ODFW%' THEN 'State Wildlife'
        WHEN classification_text ILIKE '%County%' OR classification_text ILIKE '%City%' OR classification_text ILIKE '%Municip%' OR classification_text ILIKE '%Local%' OR classification_text ILIKE '%Regional%' OR classification_text ILIKE '%Metro%' THEN 'Local Government'
        WHEN classification_text ILIKE '%Tribal%' OR classification_text ILIKE '%American Indian%' OR classification_text ILIKE '%Reservation%' OR classification_text ILIKE '%Indian Land%' THEN 'Tribal'
        WHEN classification_text ILIKE '%Wilderness%' OR classification_text ILIKE '%Conservation%' OR classification_text ILIKE '%Natural Area%' OR classification_text ILIKE '%NRCA%' OR classification_text ILIKE '%NAP%' THEN 'Wilderness / Conservation'
        WHEN public_access IN ('Open', 'Restricted') AND classification_text ILIKE '%Private%' THEN 'Open Access'
        WHEN classification_text ILIKE '%Federal%' THEN 'Other Federal'
        WHEN classification_text ILIKE '%State%' THEN 'Other State'
        WHEN public_access IN ('Open', 'Restricted') THEN 'Open Access'
        ELSE 'Other Public'
    END AS agency_category,
    designation_type,
    public_access,
    state,
    (ST_Area(geom::geography) / 4046.8564224)::real AS gis_acres,
    ST_Y(ST_PointOnSurface(geom)) AS centroid_lat,
    ST_X(ST_PointOnSurface(geom)) AS centroid_lng,
    geom,
    EXTRACT(EPOCH FROM NOW())::bigint
FROM classified_source;

ANALYZE public_lands;

SELECT state, COUNT(*) AS features, COUNT(DISTINCT managing_agency) AS agencies, COUNT(DISTINCT agency_category) AS categories
FROM public_lands
GROUP BY state
ORDER BY state;

SELECT state, agency_category, public_access, COUNT(*)
FROM public_lands
GROUP BY state, agency_category, public_access
ORDER BY state, agency_category, public_access;
SQL

echo "=== Restarting Martin ==="
docker restart plm-martin >/dev/null

echo "=== DONE: PNW public lands loaded into public_lands ==="
