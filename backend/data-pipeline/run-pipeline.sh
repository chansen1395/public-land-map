#!/usr/bin/env bash
# =============================================================================
# run-pipeline.sh — Master pipeline orchestrator
#
# Runs all data pipeline scripts in order. Supports:
#   - Starting from a specific step: ./run-pipeline.sh 10
#   - Running a single step: ./run-pipeline.sh 10 --only
#   - Full run from scratch: ./run-pipeline.sh
#
# Steps:
#   01  Download WA parcels
#   02  Download OR parcels
#   03  Download PAD-US (public lands)
#   04  Download PLSS grid
#   05  Download USFS roads/trails/recreation
#   10  Import all data to PostGIS
#   11  Normalize parcel data
#   12  Clip data to WA/OR boundaries
#   20  Generate vector tiles (tippecanoe)
#   21  Generate offline county bundles
#
# Prerequisites:
#   - Docker running (for PostGIS)
#   - GDAL/ogr2ogr installed
#   - tippecanoe installed (for tile generation steps)
#   - psql installed (for database operations)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIPELINE_DIR="${SCRIPT_DIR}"

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
START_FROM="${1:-0}"
ONLY_MODE="${2:-}"

# ---------------------------------------------------------------------------
# Define pipeline steps in order
# ---------------------------------------------------------------------------
declare -a STEPS=(
    "01:01-download-wa-parcels.sh:Download WA parcels"
    "02:02-download-or-parcels.sh:Download OR parcels"
    "03:03-download-pad-us.sh:Download PAD-US public lands"
    "04:04-download-plss.sh:Download PLSS grid"
    "05:05-download-usfs-roads-recreation.sh:Download USFS roads/trails/recreation"
    "10:10-import-to-postgis.sh:Import all data to PostGIS"
    "11:11-normalize-parcels.sh:Normalize parcel data"
    "12:12-clip-to-states.sh:Clip data to WA/OR boundaries"
    "20:20-generate-tiles.sh:Generate vector tiles"
    "21:21-generate-offline-bundles.sh:Generate offline bundles"
)

# ---------------------------------------------------------------------------
# Helper: Print usage
# ---------------------------------------------------------------------------
usage() {
    echo "Usage: $0 [start_step] [--only]"
    echo ""
    echo "Options:"
    echo "  start_step  Step number to start from (default: 0 = all)"
    echo "  --only      Run only the specified step"
    echo ""
    echo "Steps:"
    for step_def in "${STEPS[@]}"; do
        IFS=':' read -r num script desc <<< "${step_def}"
        printf "  %s  %s\n" "${num}" "${desc}"
    done
}

if [[ "${START_FROM}" == "-h" || "${START_FROM}" == "--help" ]]; then
    usage
    exit 0
fi

# ---------------------------------------------------------------------------
# Verify prerequisites
# ---------------------------------------------------------------------------
echo "================================================================="
echo " Public Land Map Data Pipeline"
echo "================================================================="
echo ""

MISSING_TOOLS=()
for tool in ogr2ogr psql curl python3; do
    if ! command -v "${tool}" &>/dev/null; then
        MISSING_TOOLS+=("${tool}")
    fi
done

# tippecanoe only needed for steps 20+
if [[ "${START_FROM}" -ge 20 ]] || [[ "${START_FROM}" -eq 0 ]]; then
    for tool in tippecanoe tile-join; do
        if ! command -v "${tool}" &>/dev/null; then
            MISSING_TOOLS+=("${tool}")
        fi
    done
fi

if [[ ${#MISSING_TOOLS[@]} -gt 0 ]]; then
    echo "ERROR: Missing required tools: ${MISSING_TOOLS[*]}"
    echo ""
    echo "Install with:"
    echo "  ogr2ogr/psql:  apt install gdal-bin postgresql-client (or brew install gdal postgresql)"
    echo "  tippecanoe:     https://github.com/felt/tippecanoe#installation"
    echo "  curl/python3:   Usually pre-installed"
    exit 1
fi

echo "All required tools found."
echo ""

# ---------------------------------------------------------------------------
# Run pipeline steps
# ---------------------------------------------------------------------------
TOTAL_START=$(date +%s)
FAILED=0

for step_def in "${STEPS[@]}"; do
    IFS=':' read -r num script desc <<< "${step_def}"

    # Skip steps before the start point
    if [[ "${num}" -lt "${START_FROM}" ]]; then
        continue
    fi

    # In --only mode, skip steps that don't match exactly
    if [[ "${ONLY_MODE}" == "--only" && "${num}" != "${START_FROM}" ]]; then
        continue
    fi

    echo ""
    echo "================================================================="
    echo " Step ${num}: ${desc}"
    echo "================================================================="
    echo ""

    STEP_START=$(date +%s)

    SCRIPT_PATH="${PIPELINE_DIR}/${script}"
    if [[ ! -f "${SCRIPT_PATH}" ]]; then
        echo "ERROR: Script not found: ${SCRIPT_PATH}"
        FAILED=$((FAILED + 1))
        continue
    fi

    # Run the step
    if bash "${SCRIPT_PATH}"; then
        STEP_END=$(date +%s)
        STEP_DURATION=$((STEP_END - STEP_START))
        echo ""
        echo "[pipeline] Step ${num} completed in ${STEP_DURATION}s"
    else
        STEP_END=$(date +%s)
        STEP_DURATION=$((STEP_END - STEP_START))
        echo ""
        echo "[pipeline] Step ${num} FAILED after ${STEP_DURATION}s"
        FAILED=$((FAILED + 1))

        # Download failures (01-05) are non-fatal — continue to next step
        # Import/tile failures (10+) are fatal — stop the pipeline
        if [[ "${num}" -ge 10 ]]; then
            echo "[pipeline] Fatal error in step ${num}. Stopping pipeline."
            break
        else
            echo "[pipeline] Non-fatal download error. Continuing..."
        fi
    fi
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
TOTAL_END=$(date +%s)
TOTAL_DURATION=$((TOTAL_END - TOTAL_START))
TOTAL_MINUTES=$((TOTAL_DURATION / 60))
TOTAL_SECONDS=$((TOTAL_DURATION % 60))

echo ""
echo "================================================================="
echo " Pipeline Complete"
echo "================================================================="
echo " Duration: ${TOTAL_MINUTES}m ${TOTAL_SECONDS}s"
echo " Failed steps: ${FAILED}"
echo "================================================================="

if [[ "${FAILED}" -gt 0 ]]; then
    echo " Some steps failed. Review output above for details."
    exit 1
fi
