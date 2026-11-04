#!/usr/bin/env bash
# =============================================================================
# 01-download-wa-parcels.sh
#
# Downloads Washington statewide tax parcels from the Washington Geoportal
# "Current Parcels" feature service as GeoJSON. The public layer carries no owner
# field, and this script does not request situs or mailing address fields.
#
# Source:  https://services.arcgis.com/jsIt88o09Q0r1j8h/arcgis/rest/services/Current_Parcels/FeatureServer/0
#          (WaTech and Washington State counties; item 2b603a599a0842a3b2284c04c8927f35)
# Checked: 2026-11-04 (layer "Parcels_2026", about 3.3 million features)
# Output:  $DATA_DIR/wa-parcels/wa-parcels-merged.geojson  (EPSG:4326)
#
# Fields kept: OBJECTID, FIPS_NR, COUNTY_NM, PARCEL_ID_NR, LANDUSE_CD
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

WA_PARCELS_URL="https://services.arcgis.com/jsIt88o09Q0r1j8h/arcgis/rest/services/Current_Parcels/FeatureServer/0"
WA_FIELDS="OBJECTID,FIPS_NR,COUNTY_NM,PARCEL_ID_NR,LANDUSE_CD"
OUT_DIR="${DATA_DIR}/wa-parcels"
MERGED_FILE="${OUT_DIR}/wa-parcels-merged.geojson"
PAGE_SIZE="${ARCGIS_PAGE_SIZE}"

mkdir -p "${OUT_DIR}"

if [[ -f "${MERGED_FILE}" ]]; then
    echo "[wa-parcels] Already downloaded: ${MERGED_FILE}"
    echo "[wa-parcels] Delete it to download again. Skipping."
    exit 0
fi

# ArcGIS answers some bad requests with HTTP 200 and an error body, so check the count as well as the status.
echo "[wa-parcels] Querying total record count..."
COUNT_URL="${WA_PARCELS_URL}/query?where=1%3D1&returnCountOnly=true&f=json"
TOTAL_COUNT=$(curl --fail -sS "${COUNT_URL}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")
echo "[wa-parcels] Total records: ${TOTAL_COUNT}"

if [[ "${TOTAL_COUNT}" -eq 0 ]]; then
    echo "[wa-parcels] ERROR: the count query returned 0 records. The service URL may have changed." >&2
    exit 1
fi

# Write to a temporary name so a failed run never leaves a partial file that looks complete
PARTIAL_FILE="${MERGED_FILE}.part"
rm -f "${PARTIAL_FILE}"

echo "[wa-parcels] Downloading with ogr2ogr and ArcGIS paging. This takes a long time for ${TOTAL_COUNT} records."
ogr2ogr \
    -f "GeoJSON" \
    "${PARTIAL_FILE}" \
    "${WA_PARCELS_URL}/query?where=1%3D1&outFields=${WA_FIELDS}&f=json" \
    -oo FEATURE_SERVER_PAGING=YES \
    -oo PAGE_SIZE="${PAGE_SIZE}" \
    -progress \
    --config OGR_WKT_PRECISION 7 \
    -t_srs EPSG:4326 \
    -lco RFC7946=YES

if [[ ! -s "${PARTIAL_FILE}" ]]; then
    echo "[wa-parcels] ERROR: output file is empty or missing." >&2
    rm -f "${PARTIAL_FILE}"
    exit 1
fi

mv "${PARTIAL_FILE}" "${MERGED_FILE}"
echo "[wa-parcels] Download complete: ${MERGED_FILE} ($(du -h "${MERGED_FILE}" | cut -f1))"
