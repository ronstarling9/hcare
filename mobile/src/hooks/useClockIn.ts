// src/hooks/useClockIn.ts
import { useMutation } from '@tanstack/react-query';
import * as Location from 'expo-location';
import { visitsApi } from '@/api/visits';
import { useVisitStore } from '@/store/visitStore';
import type { GpsStatus } from '@/store/visitStore';
import { insertEvent } from '@/db/events';
import NetInfo from '@react-native-community/netinfo';
import type { GpsCoordinate } from '@/types/domain';

export function useClockIn() {
  const { setActiveVisit } = useVisitStore();

  const mutation = useMutation({
    mutationFn: async ({ shiftId, clientId, clientName }: { shiftId: string; clientId: string; clientName: string }) => {
      const net = await NetInfo.fetch();
      const capturedOffline = !net.isConnected;

      let gpsCoordinate: GpsCoordinate | undefined;
      let gpsAvailable = false;
      try {
        const loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        gpsCoordinate = { lat: loc.coords.latitude, lng: loc.coords.longitude };
        gpsAvailable = true;
      } catch {
        // GPS unavailable — proceed without coordinate (spec 15a)
      }

      // Determine GPS status shown in GpsStatusBar for the duration of the visit.
      // OUTSIDE_RANGE can only be determined server-side (BFF compares GPS to client address);
      // the mobile app sets OK, OFFLINE, or UNAVAILABLE based on what it can observe locally.
      const gpsStatus: GpsStatus = capturedOffline ? 'OFFLINE' : gpsAvailable ? 'OK' : 'UNAVAILABLE';

      const occurredAt = new Date().toISOString();

      // Write to SQLite BEFORE calling the BFF (offline-first).
      // If the device is offline the API call is skipped, the event is queued,
      // and the caregiver can proceed with the visit; useOfflineSync drains the
      // queue on reconnect.
      await insertEvent({
        type: 'CLOCK_IN',
        visitId: shiftId,       // shiftId used as placeholder visitId until BFF confirms
        gpsCoordinate,
        capturedOffline,
        occurredAt,
      });

      if (capturedOffline) {
        // Offline path: use shiftId as a local visitId placeholder.
        // The sync batch will send this event to the BFF on reconnect.
        // Note: local-prefixed visitId distinguishes offline visits from BFF-assigned ones.
        const localVisitId = `local-${shiftId}`;
        setActiveVisit(localVisitId, shiftId, clientId, clientName, occurredAt, gpsStatus);
        return { visitId: localVisitId, clockInTime: occurredAt };
      }

      const res = await visitsApi.clockIn(shiftId, { gpsCoordinate, capturedOffline });
      setActiveVisit(res.visitId, shiftId, clientId, clientName, res.clockInTime, gpsStatus);
      return res;
    },
  });

  return mutation;
}
