# Architecture

Public Land Map has three parts: an Android app, a backend made of PostGIS, Martin and a Ktor API, and a set of shell scripts that load public data into the backend. This page describes how they fit together and where the known gaps are.

```
Android app  --vector tiles-->  Martin  -->  PostGIS  <--  data pipeline scripts
     |                                           ^
     +----------  JSON API  ---->  Ktor API  ----+
```

## Android app

The app is a single Gradle module, `app`, written in Kotlin with Jetpack Compose. It follows a plain layered layout under `app/src/main/java/com/connorhansen/publiclandmap`:

- `presentation`: Compose screens and view models. There are four tabs (Map, Marks, Tools, Settings) plus screens for waypoints, tracks, offline regions, measuring and the compass.
- `domain`: models and a few use cases (measuring, recording a track, saving a waypoint).
- `data`: Room entities and DAOs, Retrofit APIs and DTOs, mappers, repositories, and the offline tile code.
- `di`: Hilt modules.
- `service`: two foreground services, one for track recording and one for region downloads.
- `util`: distance math, coordinate formatting, GPX export and the agency color rules.

### How the map is drawn

`MapLibreView` builds a MapLibre style as JSON each time the basemap, the visible layers or the connectivity state changes. The style has:

- One raster basemap source: USGS Topo, USGS Imagery, OpenStreetMap, or imagery with an OpenTopoMap overlay for Hybrid.
- Vector sources for `public_lands`, `parcels` and the other overlays, read from Martin at `TILE_BASE_URL`. Martin publishes the PostGIS tables of the same names.
- Fill and line layers for each overlay. Public land polygons are filled with a color chosen by a MapLibre `match` expression on `agency_category`, falling back to `managing_agency`. The same agency rules exist in Kotlin in `PublicLandColors`, which also drives the legend.

Parcels only draw from zoom 12, so the map never tries to render millions of small polygons at state scale.

### Tap for detail

A tap runs `queryRenderedFeatures` against the visible layers. The feature's properties go to `MapViewModel`, which opens a bottom sheet. It then asks the Ktor API for the full record (parcel or public land, plus regulations when there are any) and fills in the sheet when the answer arrives. If the API is unreachable, the sheet shows the properties that came with the tile.

### Search

The search bar runs three lookups at once: public lands by name and parcels by parcel number through the Ktor API, and places and addresses through the public Nominatim service. Owner names are shown on the parcel sheet but are never searched, and mailing addresses are not stored anywhere. Results land in one dropdown, and choosing one flies the camera there.

### Local data

Room holds waypoints, tracks and track points, plus caches of parcels, public lands and regulations and a list of downloaded offline regions. Settings (units, coordinate format) live in DataStore. Waypoints and tracks never leave the device unless you export GPX.

### Offline maps

`OfflineScreen` lists regions from `/api/v1/offline/regions`. `DownloadService` fetches a region's MBTiles file into the app's private storage. When MBTiles files are present, `LocalTileServer` starts a small HTTP server on `127.0.0.1` with a random port that answers tile requests from those files. If the phone is offline, the style is rebuilt to read overlays from that local server instead of Martin. The offline bundles hold only the vector overlays. Basemap tiles still come from the network, so offline use shows the overlays over whatever MapLibre has cached. This path is the least tested part of the app.

## Backend

### PostGIS

`backend/sql/init.sql` creates the schema on first container start: `parcels`, `public_lands`, `regulations`, `recreation_sites`, `roads_trails`, `plss_grid`, `offline_regions` and `data_metadata`. Geometries are stored in EPSG:4326 with GiST indexes.

### Martin

Martin serves vector tiles straight from the PostGIS tables, and any `.mbtiles` files in `backend/tiles`. Its config is `backend/martin/config.yaml`; the connection string comes from the environment.

### Ktor API

`backend/api-server` is a small read-only Ktor service. It uses HikariCP for the connection pool and kotlinx.serialization for JSON. Every list endpoint takes a `bbox=west,south,east,north` parameter and pages with a cursor.

| Endpoint | Purpose |
|---|---|
| `GET /health` | Reports `healthy`, or `degraded` with a 503 when the database is unreachable |
| `GET /api/v1/parcels` | Parcels in a bbox, optional `state` of WA or OR |
| `GET /api/v1/parcels/search` | Search by parcel number (not by owner name or address) |
| `GET /api/v1/parcels/{id}` | One parcel |
| `GET /api/v1/public-lands` | Public lands in a bbox |
| `GET /api/v1/public-lands/search` | Search by name or agency |
| `GET /api/v1/public-lands/{id}` | One public land unit |
| `GET /api/v1/public-lands/{id}/regulations` | Regulations tied to a unit |
| `GET /api/v1/regulations` | Regulations in a bbox, optional `type` |
| `GET /api/v1/offline/regions` | Downloadable regions |
| `GET /api/v1/offline/regions/{id}/metadata` | One region's metadata |
| `GET /api/v1/offline/regions/{id}/download` | The region's MBTiles file |
| `GET /api/v1/metadata/last-updated` | When each dataset was last loaded |

The API has no authentication and allows any CORS origin. It is meant to run on a developer machine or a private network.

### Data pipeline

`backend/data-pipeline` holds numbered shell scripts that `run-pipeline.sh` runs in order: download parcels, public lands, PLSS and Forest Service data (01 to 05); import and normalize them in PostGIS (10 to 12); build tilesets with tippecanoe and per-county offline bundles (20 and 21). Every download script skips work if its output file already exists.

`import-public-lands.sh` is a second, separate loader for public lands. It pulls Washington DNR and Oregon PAD-US layers through ArcGIS REST services directly into the `public_lands` table. It is the loader I used for the screenshots. The PAD-US path in steps 03 and 10 overlaps with it, and I have not reconciled the two.

## Known gaps

- No loader fills the `regulations` table, so the regulation layers are empty.
- Offline downloads and the local tile server have not been tested on a device.
- Repository SQL, view models and UI have no automated tests.
- Basemap and search services are public, rate-limited services.
- The Oregon pipeline has not been run end to end.
