// src/hooks/useVisit.ts
import { useMutation, useQuery } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import { useVisitStore } from '@/store/visitStore';
import { insertEvent } from '@/db/events';
import NetInfo from '@react-native-community/netinfo';
import * as Location from 'expo-location';
import type { AdlTask } from '@/types/domain';

export function useVisit(shiftId: string) {
  const { activeVisitId, clearActiveVisit } = useVisitStore();

  const carePlanQuery = useQuery({
    queryKey: ['carePlan', shiftId],
    queryFn: () => visitsApi.carePlan(shiftId).then(r => r.carePlan),
    enabled: !!shiftId,
  });

  const completeTask = useMutation({
    mutationFn: async ({ taskId }: { taskId: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const offline = !net.isConnected;
      await insertEvent({ type: 'TASK_COMPLETE', visitId: activeVisitId, taskId, capturedOffline: offline, occurredAt: new Date().toISOString() });
      if (!offline) await visitsApi.completeTask(activeVisitId, taskId);
    },
  });

  const revertTask = useMutation({
    mutationFn: async ({ taskId }: { taskId: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const offline = !net.isConnected;
      await insertEvent({ type: 'TASK_REVERT', visitId: activeVisitId, taskId, capturedOffline: offline, occurredAt: new Date().toISOString() });
      if (!offline) await visitsApi.revertTask(activeVisitId, taskId);
    },
  });

  const saveNotes = useMutation({
    mutationFn: async ({ notes }: { notes: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const offline = !net.isConnected;
      await insertEvent({ type: 'NOTE_SAVE', visitId: activeVisitId, notes, capturedOffline: offline, occurredAt: new Date().toISOString() });
      if (!offline) await visitsApi.saveNotes(activeVisitId, notes);
    },
  });

  const clockOut = useMutation({
    mutationFn: async ({ notes }: { notes: string }) => {
      if (!activeVisitId) return;
      const net = await NetInfo.fetch();
      const capturedOffline = !net.isConnected;

      let gpsCoordinate: { lat: number; lng: number } | undefined;
      try {
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        gpsCoordinate = { lat: loc.coords.latitude, lng: loc.coords.longitude };
      } catch { /* GPS unavailable — spec 15b */ }

      await insertEvent({ type: 'CLOCK_OUT', visitId: activeVisitId!, gpsCoordinate, capturedOffline, occurredAt: new Date().toISOString() });
      if (!capturedOffline) {
        await visitsApi.clockOut(activeVisitId!, { gpsCoordinate, capturedOffline, notes });
      }
      clearActiveVisit();
    },
  });

  return { carePlanQuery, completeTask, revertTask, saveNotes, clockOut };
}
