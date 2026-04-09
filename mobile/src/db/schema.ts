// src/db/schema.ts
export const CREATE_EVENTS_TABLE = `
  CREATE TABLE IF NOT EXISTS sync_events (
    row_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    type          TEXT NOT NULL,
    visit_id      TEXT NOT NULL,
    task_id       TEXT,
    gps_lat       REAL,
    gps_lng       REAL,
    captured_offline INTEGER NOT NULL DEFAULT 1,
    notes         TEXT,
    occurred_at   TEXT NOT NULL,
    synced        INTEGER NOT NULL DEFAULT 0
  );
`;
