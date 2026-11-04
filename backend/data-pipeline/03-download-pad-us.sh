#!/usr/bin/env bash
# =============================================================================
# 03-download-pad-us.sh
#
# Downloads PAD-US 4.1 (Protected Areas Database of the United States) "Fee" polygons
# for Washington and Oregon from the USGS National Map ArcGIS service, plus the
# service's published code-to-description tables.
#
# Source:  https://edits.nationalmap.gov/arcgis/rest/services/PAD-US/PAD_US_4_1/MapServer/0
#          (USGS GAP, doi:10.5066/P96WBCHS; US government work, credit USGS)
# Checked: 2026-11-04 (WA 6552 features, OR 6100 features)
# Output:  $DATA_DIR/pad-us/pad-us-wa-or.gpkg   (layer "padus", EPSG:4326)
#          $DATA_DIR/pad-us/padus-domains.csv   (field, code, description)
#
# Why not the ScienceBase file: the PAD-US 4.0 file URL this script used to call now
# returns 404, and the 4.1 state downloads on ScienceBase (item 6759abcfd34edfeb8710a004)
# are served through a web page that curl cannot fetch (HTTP 200 with an HTML body).
# If you want the full geodatabase, download it by hand from
# https://www.usgs.gov/programs/gap-analysis-project/science/pad-us-data-download
# The field names below are the same.
#
# Values such as Mang_Name are short codes (BLM, USFS, ...). The domains file
# turns them back into descriptions during import.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/00-env.sh"

PADUS_URL="https://edits.nationalmap.gov/arcgis/rest/services/PAD-US/PAD_US_4_1/MapServer/0"
PADUS_FIELDS="OBJECTID,Unit_Nm,Loc_Nm,Mang_Type,Mang_Name,Des_Tp,Loc_Ds,Pub_Access,State_Nm,GIS_Acres"
OUT_DIR="${DATA_DIR}/pad-us"
OUT_FILE="${OUT_DIR}/pad-us-wa-or.gpkg"
DOMAINS_FILE="${OUT_DIR}/padus-domains.csv"
PAGE_SIZE="${ARCGIS_PAGE_SIZE}"

mkdir -p "${OUT_DIR}"

# ---------------------------------------------------------------------------
# Code tables from the layer metadata
# ---------------------------------------------------------------------------
if [[ ! -s "${DOMAINS_FILE}" ]]; then
    echo "[pad-us] Fetching code tables from the layer metadata..."
    curl --fail -sS "${PADUS_URL}?f=json" | python3 -c '
import csv, json, sys
layer = json.load(sys.stdin)
if "fields" not in layer:
    sys.exit("layer metadata has no fields: " + json.dumps(layer)[:200])
wanted = {"Mang_Name", "Mang_Type", "Des_Tp", "Pub_Access", "State_Nm"}
writer = csv.writer(sys.stdout)
writer.writerow(["field", "code", "description"])
for field in layer["fields"]:
    if field["name"] in wanted and field.get("domain"):
        for value in field["domain"]["codedValues"]:
            writer.writerow([field["name"], value["code"], value["name"]])
' > "${DOMAINS_FILE}.part"
    mv "${DOMAINS_FILE}.part" "${DOMAINS_FILE}"
    echo "[pad-us] Wrote $(($(wc -l < "${DOMAINS_FILE}") - 1)) codes to ${DOMAINS_FILE}"
fi

if [[ -f "${OUT_FILE}" ]]; then
    echo "[pad-us] Already downloaded: ${OUT_FILE}"
    exit 0
fi

# ---------------------------------------------------------------------------
# Feature download, one state at a time
# ---------------------------------------------------------------------------
PARTIAL_FILE="${OUT_FILE}.part"
rm -f "${PARTIAL_FILE}"

for state in WA OR; do
    COUNT=$(curl --fail -sS "${PADUS_URL}/query?where=State_Nm%3D%27${state}%27&returnCountOnly=true&f=json" \
        | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")
    echo "[pad-us] ${state}: ${COUNT} features"
    if [[ "${COUNT}" -eq 0 ]]; then
        echo "[pad-us] ERROR: the count query returned 0 for ${state}. The service URL may have changed." >&2
        rm -f "${PARTIAL_FILE}"
        exit 1
    fi

    APPEND_ARGS=()
    if [[ -f "${PARTIAL_FILE}" ]]; then
        APPEND_ARGS=(-append -update)
    fi

    ogr2ogr \
        -f "GPKG" \
        "${PARTIAL_FILE}" \
        "${PADUS_URL}/query?where=State_Nm%3D%27${state}%27&outFields=${PADUS_FIELDS}&f=json" \
        ${APPEND_ARGS[@]+"${APPEND_ARGS[@]}"} \
        -oo FEATURE_SERVER_PAGING=YES \
        -oo PAGE_SIZE="${PAGE_SIZE}" \
        -nln padus \
        -nlt PROMOTE_TO_MULTI \
        -t_srs EPSG:4326 \
        -progress
done

if [[ ! -s "${PARTIAL_FILE}" ]]; then
    echo "[pad-us] ERROR: output file is empty or missing." >&2
    rm -f "${PARTIAL_FILE}"
    exit 1
fi

mv "${PARTIAL_FILE}" "${OUT_FILE}"
echo "[pad-us] Done: ${OUT_FILE} ($(du -h "${OUT_FILE}" | cut -f1))"
