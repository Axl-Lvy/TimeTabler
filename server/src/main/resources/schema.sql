-- Idempotent: applied on every boot.
CREATE TABLE IF NOT EXISTS venue (
  id uuid PRIMARY KEY,
  name text NOT NULL,
  normalized_name text NOT NULL UNIQUE,
  address text
);

CREATE SEQUENCE IF NOT EXISTS event_revision_seq;

CREATE TABLE IF NOT EXISTS event (
  id uuid PRIMARY KEY,
  revision bigint NOT NULL,
  night_date date NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz,
  venue_id uuid NOT NULL REFERENCES venue (id),
  title text NOT NULL,
  poster_url text,
  lineup text[] NOT NULL DEFAULT '{}',
  archived boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS event_revision_idx ON event (revision);

CREATE TABLE IF NOT EXISTS listing (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id),
  source text NOT NULL,
  external_id text NOT NULL,
  url text NOT NULL,
  raw_title text NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz,
  status text NOT NULL,
  poster_url text,
  lineup text[] NOT NULL DEFAULT '{}',
  content_hash text NOT NULL,
  last_seen_at timestamptz NOT NULL,
  UNIQUE (source, external_id)
);

CREATE INDEX IF NOT EXISTS listing_event_idx ON listing (event_id);
