#!/usr/bin/env bash
# =============================================================================
# 02-download-or-parcels.sh
#
# Downloads Oregon tax lots from an ArcGIS REST layer that YOU choose.
#
# UNCONFIRMED SOURCE. The old default, a statewide ORMAP service on gis.oregongeology.com,
# returns 404 (checked 2026-11-04). I could not confirm any current public statewide
# Oregon tax lot service: the Oregon Parcel Database layer on services8.arcgis.com
# (or_cadastral_wm) also returns "Invalid URL", and the Oregon Department of Forestry
# StatewideTaxLots map service needs a token. ORMAP data comes from the county
# assessors and the Oregon Department of Revenue. So this script does nothing until you
# point it at a county or other public layer:
#
#   OR_PARCELS_URL=https://.../FeatureServer/0 \
#   OR_PARCEL_FIELD=TAXLOT OR_COUNTY_FIELD=COUNTY ./02-download-or-parcels.sh
#
#   OR_PARCELS_URL    URL of an ArcGIS REST layer (no trailing /query)
#   OR_PARCEL_FIELD   field holding the tax lot or map and tax lot number
#   OR_COUNTY_FIELD   field holding the county name
#   OR_WHERE          optional where clause (default 1=1)
#
# Only those two fields (plus the object id) are requested. Owner, situs and mailing
# address fields are never requested.
#
# Output: $DATA_DIR/or-parcels/or-parcels-merged.geojson  (EPSG:4326)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

OUT_DIR="${DATA_DIR}/or-parcels"
MERGED_FILE="${OUT_DIR}/or-parcels-merged.geojson"
PAGE_SIZE="${ARCGIS_PAGE_SIZE}"

if [[ -z "${OR_PARCELS_URL:-}" ]]; then
    echo "[or-parcels] SKIPPED: OR_PARCELS_URL is not set."
    echo "[or-parcels] No confirmed public statewide Oregon tax lot service exists; see the comments in this script."
    exit 0
fi

OR_PARCEL_FIELD="${OR_PARCEL_FIELD:?Set OR_PARCEL_FIELD to the tax lot field name}"
OR_COUNTY_FIELD="${OR_COUNTY_FIELD:?Set OR_COUNTY_FIELD to the county field name}"
OR_WHERE="${OR_WHERE:-1=1}"

# The importer builds SQL from these names, so accept plain identifiers only
for name in "${OR_PARCEL_FIELD}" "${OR_COUNTY_FIELD}"; do
    if [[ ! "${name}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
        echo "[or-parcels] ERROR: '${name}' is not a plain field name." >&2
        exit 1
    fi
done

mkdir -p "${OUT_DIR}"

if [[ -f "${MERGED_FILE}" ]]; then
    echo "[or-parcels] Already downloaded: ${MERGED_FILE}"
    echo "[or-parcels] Delete it to download again. Skipping."
    exit 0
fi

WHERE_ENC=$(python3 -c "import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1]))" "${OR_WHERE}")
FIELDS="${OR_PARCEL_FIELD},${OR_COUNTY_FIELD}"

echo "[or-parcels] Querying total record count..."
COUNT_URL="${OR_PARCELS_URL}/query?where=${WHERE_ENC}&returnCountOnly=true&f=json"
TOTAL_COUNT=$(curl --fail -sS "${COUNT_URL}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")
echo "[or-parcels] Total records: ${TOTAL_COUNT}"

if [[ "${TOTAL_COUNT}" -eq 0 ]]; then
    echo "[or-parcels] ERROR: the count query returned 0 records. Check OR_PARCELS_URL and OR_WHERE." >&2
    exit 1
fi

PARTIAL_FILE="${MERGED_FILE}.part"
rm -f "${PARTIAL_FILE}"

echo "[or-parcels] Downloading with ogr2ogr and ArcGIS paging..."
ogr2ogr \
    -f "GeoJSON" \
    "${PARTIAL_FILE}" \
    "${OR_PARCELS_URL}/query?where=${WHERE_ENC}&outFields=${FIELDS}&f=json" \
    -oo FEATURE_SERVER_PAGING=YES \
    -oo PAGE_SIZE="${PAGE_SIZE}" \
    -progress \
    --config OGR_WKT_PRECISION 7 \
    -t_srs EPSG:4326 \
    -lco RFC7946=YES

if [[ ! -s "${PARTIAL_FILE}" ]]; then
    echo "[or-parcels] ERROR: output file is empty or missing." >&2
    rm -f "${PARTIAL_FILE}"
    exit 1
fi

mv "${PARTIAL_FILE}" "${MERGED_FILE}"
echo "[or-parcels] Download complete: ${MERGED_FILE} ($(du -h "${MERGED_FILE}" | cut -f1))"
# The importer (10-import-to-postgis.sh) needs the same two field names:
echo "[or-parcels] Run the importer with the same OR_PARCEL_FIELD and OR_COUNTY_FIELD set."
