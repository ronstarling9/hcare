// src/db/events.ts
import * as SQLite from 'expo-sqlite';
import { CREATE_EVENTS_TABLE } from './schema';
import type { SyncEvent } from '@/types/domain';

let db: SQLite.SQLiteDatabase | null = null;

export async function openEventStore() {
  db = await SQLite.openDatabaseAsync('hcare_events.db');
  await db.execAsync(CREATE_EVENTS_TABLE);
}

function getDb(): SQLite.SQLiteDatabase {
  if (!db) throw new Error('Event store not opened. Call openEventStore() first.');
  return db;
}

export async function insertEvent(event: SyncEvent): Promise<void> {
  const database = getDb();
  await database.runAsync(
    `INSERT INTO sync_events (type, visit_id, task_id, gps_lat, gps_lng, captured_offline, notes, occurred_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      event.type,
      event.visitId,
      event.taskId ?? null,
      event.gpsCoordinate?.lat ?? null,
      event.gpsCoordinate?.lng ?? null,
      event.capturedOffline ? 1 : 0,
      event.notes ?? null,
      event.occurredAt,
    ]
  );
}

export async function getPendingEvents(): Promise<(SyncEvent & { rowId: number })[]> {
  const database = getDb();
  const rows = await database.getAllAsync<any>(
    `SELECT * FROM sync_events WHERE synced = 0 ORDER BY occurred_at ASC`
  );
  return rows.map(row => ({
    rowId:           row.row_id,
    type:            row.type,
    visitId:         row.visit_id,
    taskId:          row.task_id ?? undefined,
    gpsCoordinate:   row.gps_lat != null ? { lat: row.gps_lat, lng: row.gps_lng } : undefined,
    capturedOffline: row.captured_offline === 1,
    notes:           row.notes ?? undefined,
    occurredAt:      row.occurred_at,
  }));
}

export async function markSynced(rowIds: number[]): Promise<void> {
  if (rowIds.length === 0) return;
  const database = getDb();
  const placeholders = rowIds.map(() => '?').join(',');
  await database.runAsync(
    `UPDATE sync_events SET synced = 1 WHERE row_id IN (${placeholders})`,
    rowIds
  );
}

export async function deleteByVisitId(visitId: string): Promise<void> {
  const database = getDb();
  await database.runAsync(`DELETE FROM sync_events WHERE visit_id = ?`, [visitId]);
}
