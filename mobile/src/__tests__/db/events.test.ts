import { openEventStore, insertEvent, getPendingEvents, markSynced } from '@/db/events';
import type { SyncEvent } from '@/types/domain';

beforeAll(async () => {
  await openEventStore();
});

describe('SQLite event log', () => {
  const event: SyncEvent = {
    type: 'CLOCK_IN',
    visitId: 'v-test',
    capturedOffline: true,
    occurredAt: new Date().toISOString(),
  };

  it('inserts and retrieves a pending event', async () => {
    await insertEvent(event);
    const pending = await getPendingEvents();
    const found = pending.find(e => e.visitId === 'v-test' && e.type === 'CLOCK_IN');
    expect(found).toBeTruthy();
  });

  it('marks an event synced so it no longer appears in pending', async () => {
    await insertEvent({ ...event, visitId: 'v-sync-test' });
    const before = await getPendingEvents();
    const toSync = before.filter(e => e.visitId === 'v-sync-test');
    await markSynced(toSync.map(e => (e as any).rowId));
    const after = await getPendingEvents();
    expect(after.find(e => e.visitId === 'v-sync-test')).toBeUndefined();
  });
});
