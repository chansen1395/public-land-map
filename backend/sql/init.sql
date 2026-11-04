-- =============================================================================
-- Public Land Map PostGIS Schema Initialization
-- Run automatically on first container start via docker-entrypoint-initdb.d
-- =============================================================================

-- Enable PostGIS extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- =============================================================================
-- PARCELS — County-level parcel/tax-lot boundaries for WA and OR
-- =============================================================================
CREATE TABLE parcels (
    id TEXT PRIMARY KEY,
    parcel_number TEXT NOT NULL,
    owner_name TEXT,
    county TEXT NOT NULL,
    state TEXT NOT NULL CHECK(state IN ('WA','OR')),
    acreage REAL,
    land_use_code TEXT,
    land_use_desc TEXT,
    centroid_lat DOUBLE PRECISION NOT NULL,
    centroid_lng DOUBLE PRECISION NOT NULL,
    geom GEOMETRY(MultiPolygon, 4326),
    last_updated BIGINT
);

CREATE INDEX idx_parcels_geom ON parcels USING GIST (geom);
CREATE INDEX idx_parcels_county ON parcels(county, state);
CREATE INDEX idx_parcels_parcel_num ON parcels(parcel_number);

-- =============================================================================
-- PUBLIC LANDS — Federal, state, and local public land boundaries (from PAD-US)
-- =============================================================================
CREATE TABLE public_lands (
    id TEXT PRIMARY KEY,
    unit_name TEXT NOT NULL,
    managing_agency TEXT NOT NULL,
    agency_category TEXT NOT NULL DEFAULT 'Other Public',
    designation_type TEXT NOT NULL,
    public_access TEXT CHECK(public_access IN ('Open','Restricted','Closed','Unknown')),
    state TEXT NOT NULL,
    gis_acres REAL,
    centroid_lat DOUBLE PRECISION,
    centroid_lng DOUBLE PRECISION,
    geom GEOMETRY(MultiPolygon, 4326),
    last_updated BIGINT
);

CREATE INDEX idx_pl_geom ON public_lands USING GIST (geom);
CREATE INDEX idx_pl_agency ON public_lands(managing_agency);
CREATE INDEX idx_pl_agency_category ON public_lands(agency_category);
CREATE INDEX idx_pl_state ON public_lands(state);

-- =============================================================================
-- REGULATIONS — Access, safety, fire, and seasonal restrictions tied to geographic areas
-- =============================================================================
CREATE TABLE regulations (
    id SERIAL PRIMARY KEY,
    area_id TEXT,
    area_name TEXT,
    regulation_type TEXT NOT NULL,
    managing_agency TEXT,
    state TEXT,
    title TEXT NOT NULL,
    description TEXT,
    restriction_level TEXT,
    start_date DATE,
    end_date DATE,
    geom GEOMETRY(MultiPolygon, 4326),
    source_url TEXT,
    last_updated BIGINT
);

CREATE INDEX idx_reg_geom ON regulations USING GIST (geom);
CREATE INDEX idx_reg_type ON regulations(regulation_type);
CREATE INDEX idx_reg_area ON regulations(area_id);

-- =============================================================================
-- RECREATION SITES — Campgrounds, trailheads, boat ramps, viewpoints, etc.
-- =============================================================================
CREATE TABLE recreation_sites (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    site_type TEXT NOT NULL,
    managing_agency TEXT,
    state TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    geom GEOMETRY(Point, 4326),
    description TEXT,
    amenities TEXT,
    fee TEXT,
    reservation_url TEXT,
    last_updated BIGINT
);

COMMENT ON COLUMN recreation_sites.site_type IS 'One of: campground, trailhead, picnic_area, boat_ramp, viewpoint';

CREATE INDEX idx_rec_geom ON recreation_sites USING GIST (geom);
CREATE INDEX idx_rec_type ON recreation_sites(site_type);

-- =============================================================================
-- ROADS & TRAILS — USFS roads) and trails network
-- =============================================================================
CREATE TABLE roads_trails (
    id TEXT PRIMARY KEY,
    name TEXT,
    route_type TEXT NOT NULL,
    status TEXT,
    surface_type TEXT,
    allowed_uses TEXT,
    managing_agency TEXT,
    state TEXT,
    geom GEOMETRY(MultiLineString, 4326),
    last_updated BIGINT
);

COMMENT ON COLUMN roads_trails.route_type IS 'One of: road, trail';
COMMENT ON COLUMN roads_trails.status IS 'One of: open, seasonal, closed';
COMMENT ON COLUMN roads_trails.allowed_uses IS 'Comma-separated: motor_vehicle,hiking,biking,equestrian';

CREATE INDEX idx_rt_geom ON roads_trails USING GIST (geom);
CREATE INDEX idx_rt_type ON roads_trails(route_type);

-- =============================================================================
-- PLSS GRID — Public Land Survey System (township/range/section grid)
-- =============================================================================
CREATE TABLE plss_grid (
    id TEXT PRIMARY KEY,
    township TEXT,
    range TEXT,
    section TEXT,
    meridian TEXT,
    state TEXT,
    geom GEOMETRY(MultiPolygon, 4326)
);

CREATE INDEX idx_plss_geom ON plss_grid USING GIST (geom);

-- =============================================================================
-- OFFLINE REGIONS — Pre-computed county tile bundles for offline download
-- =============================================================================
CREATE TABLE offline_regions (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    state TEXT NOT NULL,
    county TEXT,
    min_lat DOUBLE PRECISION NOT NULL,
    min_lng DOUBLE PRECISION NOT NULL,
    max_lat DOUBLE PRECISION NOT NULL,
    max_lng DOUBLE PRECISION NOT NULL,
    min_zoom INTEGER DEFAULT 8,
    max_zoom INTEGER DEFAULT 15,
    size_bytes BIGINT,
    tile_file_path TEXT
);

-- =============================================================================
-- DATA METADATA — Track data freshness and pipeline versions
-- =============================================================================
CREATE TABLE data_metadata (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

INSERT INTO data_metadata (key, value) VALUES ('last_updated', NOW()::TEXT);
INSERT INTO data_metadata (key, value) VALUES ('wa_parcels_version', '0');
INSERT INTO data_metadata (key, value) VALUES ('or_parcels_version', '0');
INSERT INTO data_metadata (key, value) VALUES ('pad_us_version', '0');
