// src/hooks/useOfflineSync.ts
import { useEffect, useRef, useState, useCallback } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { useMutation } from '@tanstack/react-query';
import { syncApi } from '@/api/sync';
import { getPendingEvents, markSynced } from '@/db/events';
import { useVisitStore } from '@/store/visitStore';
import type { SyncEventResult } from '@/types/api';
import * as Device from 'expo-device';

export function useOfflineSync() {
  const [isOnline, setIsOnline] = useState(true);
  const [syncPending, setSyncPending] = useState(false);
  const [syncSuccess, setSyncSuccess] = useState(false);
  const wasOffline = useRef(false);
  // Conflicts are written to visitStore so ConflictDetailScreen can dismiss
  // them directly without prop-drilling — read via useVisitStore(s => s.conflicts).
  const setConflicts = useVisitStore(s => s.setConflicts);

  // Stable ref so the NetInfo listener always calls the latest mutation,
  // avoiding a stale closure on the empty-dependency useEffect below.
  const syncMutationRef = useRef<ReturnType<typeof useMutation<{ results: SyncEventResult[] }>>['mutateAsync'] | null>(null);

  const syncMutation = useMutation({
    mutationFn: async () => {
      const events = await getPendingEvents();
      if (events.length === 0) return { results: [] as SyncEventResult[] };

      // expo-device may return null on simulators — fall back to a fixed string.
      const deviceId = Device.modelId ?? Device.deviceName ?? 'unknown-device';

      const res = await syncApi.batch({ deviceId, events });

      // The BFF returns one result per visitId (not per event).
      // Mark synced: all local events whose visitId received an OK or DUPLICATE result.
      // Events for CONFLICT_REASSIGNED visitIds are intentionally left unsynced so
      // the caregiver sees the conflict UI and the agency can investigate.
      const succeededVisitIds = new Set(
        res.results
          .filter(r => r.result === 'OK' || r.result === 'DUPLICATE')
          .map(r => r.visitId)
      );
      const rowIdsToMark = events
        .filter(e => succeededVisitIds.has(e.visitId))
        .map(e => e.rowId);
      if (rowIdsToMark.length > 0) {
        await markSynced(rowIdsToMark);
      }

      return res;
    },
    onSuccess: (res) => {
      const conflictResults = res.results.filter(r => r.result === 'CONFLICT_REASSIGNED');
      if (conflictResults.length > 0) {
        setConflicts(conflictResults);
      }
      if (res.results.some(r => r.result === 'OK')) {
        setSyncSuccess(true);
        setTimeout(() => setSyncSuccess(false), 3000);
      }
    },
  });

  // Keep the ref current on every render so the listener never holds a stale closure.
  syncMutationRef.current = syncMutation.mutateAsync;

  useEffect(() => {
    const unsub = NetInfo.addEventListener(async (state) => {
      const online = !!state.isConnected;
      setIsOnline(online);
      if (online && wasOffline.current) {
        wasOffline.current = false;
        setSyncPending(true);
        try {
          await syncMutationRef.current?.();
        } finally {
          setSyncPending(false);
        }
      }
      if (!online) wasOffline.current = true;
    });
    return unsub;
  }, []); // empty deps intentional — listener uses ref, not closure

  const retrySync = useCallback(async () => {
    setSyncPending(true);
    try { await syncMutationRef.current?.(); }
    finally { setSyncPending(false); }
  }, []); // stable — reads through ref, no deps needed

  return {
    isOnline,
    syncPending,
    syncSuccess,
    syncFailed: syncMutation.isError,
    retrySync,
    // conflicts intentionally omitted — read from useVisitStore(s => s.conflicts)
  };
}
