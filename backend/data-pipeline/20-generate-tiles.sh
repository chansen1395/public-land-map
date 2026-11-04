#!/usr/bin/env bash
# =============================================================================
# 20-generate-tiles.sh
#
# Generates MBTiles vector tile sets from PostGIS data using tippecanoe.
#
# Steps for each layer:
#   1. Export from PostGIS → GeoJSON (via ogr2ogr)
#   2. Run tippecanoe to generate .mbtiles with configured zoom levels
#   3. Copy .mbtiles to $TILES_DIR for Martin to serve
#
# Zoom level strategy:
#   - Parcels: z12-z15 (high detail, only visible at close zoom)
#   - Public lands: z4-z15 (visible from national to local scale)
#   - PLSS grid: z10-z15 (visible at county/township level)
#   - Roads/trails: z8-z15 (visible at regional to local scale)
#   - Recreation POIs: z6-z15 (visible from state to local scale)
#
# Prerequisites:
#   - tippecanoe installed (https://github.com/felt/tippecanoe)
#   - ogr2ogr / GDAL installed
#   - PostGIS running with imported data
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

EXPORT_DIR="${DATA_DIR}/exports"
mkdir -p "${EXPORT_DIR}" "${TILES_DIR}"

# ---------------------------------------------------------------------------
# Helper: Export a PostGIS table to GeoJSON
# ---------------------------------------------------------------------------
export_table() {
    local table="$1"
    local output="$2"
    local sql="${3:-}"

    if [[ -f "${output}" ]]; then
        echo "[tiles] Export already exists: ${output}"
        return 0
    fi

    echo "[tiles] Exporting ${table} → ${output}..."

    if [[ -n "${sql}" ]]; then
        ogr2ogr \
            -f "GeoJSON" "${output}" \
            "${PG_CONN}" \
            -sql "${sql}" \
            -lco RFC7946=YES \
            -progress
    else
        ogr2ogr \
            -f "GeoJSON" "${output}" \
            "${PG_CONN}" \
            "${table}" \
            -lco RFC7946=YES \
            -progress
    fi

    if [[ ! -s "${output}" ]]; then
        echo "[tiles] WARNING: Export produced empty file for ${table}."
        return 1
    fi
    echo "[tiles] Exported: $(du -h "${output}" | cut -f1)"
}

# ---------------------------------------------------------------------------
# Helper: Run tippecanoe with common options
# ---------------------------------------------------------------------------
generate_tileset() {
    local input="$1"
    local output="$2"
    local layer_name="$3"
    local min_zoom="$4"
    local max_zoom="$5"
    shift 5
    local extra_args=("$@")

    if [[ -f "${output}" ]]; then
        echo "[tiles] Tileset already exists: ${output}"
        return 0
    fi

    if [[ ! -f "${input}" ]]; then
        echo "[tiles] WARNING: Input file not found: ${input}"
        return 1
    fi

    echo "[tiles] Generating ${output}..."
    echo "[tiles]   Layer: ${layer_name}, zoom: z${min_zoom}-z${max_zoom}"

    tippecanoe \
        -o "${output}" \
        -l "${layer_name}" \
        --minimum-zoom="${min_zoom}" \
        --maximum-zoom="${max_zoom}" \
        --force \
        --no-tile-compression \
        "${extra_args[@]}" \
        "${input}"

    if [[ ! -s "${output}" ]]; then
        echo "[tiles] ERROR: tippecanoe produced empty file."
        return 1
    fi
    echo "[tiles] Generated: $(du -h "${output}" | cut -f1)"
}

# =============================================================================
# 1. PARCELS — WA
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] 1/6 — WA Parcels"
echo "================================================================="

export_table "parcels" "${EXPORT_DIR}/parcels-wa.geojson" \
    "SELECT id, parcel_number, owner_name, county, acreage, land_use_desc, geom FROM parcels WHERE state='WA' AND geom IS NOT NULL"

generate_tileset \
    "${EXPORT_DIR}/parcels-wa.geojson" \
    "${TILES_DIR}/parcels-wa.mbtiles" \
    "parcels-wa" 12 15 \
    --drop-densest-as-needed \
    --extend-zooms-if-still-dropping \
    --include='{"id","parcel_number","owner_name","county","acreage","land_use_desc"}'

# =============================================================================
# 2. PARCELS — OR
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] 2/6 — OR Parcels"
echo "================================================================="

export_table "parcels" "${EXPORT_DIR}/parcels-or.geojson" \
    "SELECT id, parcel_number, owner_name, county, acreage, land_use_desc, geom FROM parcels WHERE state='OR' AND geom IS NOT NULL"

generate_tileset \
    "${EXPORT_DIR}/parcels-or.geojson" \
    "${TILES_DIR}/parcels-or.mbtiles" \
    "parcels-or" 12 15 \
    --drop-densest-as-needed \
    --extend-zooms-if-still-dropping \
    --include='{"id","parcel_number","owner_name","county","acreage","land_use_desc"}'

# =============================================================================
# 3. PUBLIC LANDS
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] 3/6 — Public Lands"
echo "================================================================="

export_table "public_lands" "${EXPORT_DIR}/public-lands.geojson" \
    "SELECT id, unit_name, managing_agency, agency_category, designation_type, public_access, state, gis_acres, geom FROM public_lands WHERE geom IS NOT NULL"

generate_tileset \
    "${EXPORT_DIR}/public-lands.geojson" \
    "${TILES_DIR}/public-lands.mbtiles" \
    "public-lands" 4 15 \
    --simplification=10 \
    --simplify-only-low-zooms \
    --detect-shared-borders \
    --include='{"id","unit_name","managing_agency","agency_category","designation_type","public_access","gis_acres"}'

# =============================================================================
# 4. PLSS GRID
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] 4/6 — PLSS Grid"
echo "================================================================="

export_table "plss_grid" "${EXPORT_DIR}/plss-grid.geojson" \
    "SELECT id, township, range, section, meridian, state, geom FROM plss_grid WHERE geom IS NOT NULL"

generate_tileset \
    "${EXPORT_DIR}/plss-grid.geojson" \
    "${TILES_DIR}/plss.mbtiles" \
    "plss" 10 15 \
    --detect-shared-borders \
    --include='{"id","township","range","section","meridian"}'

# =============================================================================
# 5. ROADS & TRAILS
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] 5/6 — Roads & Trails"
echo "================================================================="

export_table "roads_trails" "${EXPORT_DIR}/roads-trails.geojson" \
    "SELECT id, name, route_type, status, surface_type, allowed_uses, managing_agency, state, geom FROM roads_trails WHERE geom IS NOT NULL"

generate_tileset \
    "${EXPORT_DIR}/roads-trails.geojson" \
    "${TILES_DIR}/roads-trails.mbtiles" \
    "roads-trails" 8 15 \
    --simplification=5 \
    --simplify-only-low-zooms \
    --drop-densest-as-needed \
    --include='{"id","name","route_type","status","allowed_uses"}'

# =============================================================================
# 6. RECREATION POIs
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] 6/6 — Recreation POIs"
echo "================================================================="

export_table "recreation_sites" "${EXPORT_DIR}/recreation-pois.geojson" \
    "SELECT id, name, site_type, managing_agency, state, latitude, longitude, description, amenities, fee, geom FROM recreation_sites WHERE geom IS NOT NULL"

generate_tileset \
    "${EXPORT_DIR}/recreation-pois.geojson" \
    "${TILES_DIR}/recreation-pois.mbtiles" \
    "recreation-pois" 6 15 \
    --cluster-distance=50 \
    --include='{"id","name","site_type","managing_agency","fee"}'

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "================================================================="
echo "[tiles] Tile Generation Summary"
echo "================================================================="
echo "Generated MBTiles files:"
for f in "${TILES_DIR}"/*.mbtiles; do
    if [[ -f "$f" ]]; then
        SIZE=$(du -h "$f" | cut -f1)
        NAME=$(basename "$f")
        echo "  ${NAME}: ${SIZE}"
    fi
done
echo ""
echo "[tiles] All tile generation complete."
echo "[tiles] Files are in: ${TILES_DIR}/"
echo "[tiles] Martin will auto-discover and serve these."
