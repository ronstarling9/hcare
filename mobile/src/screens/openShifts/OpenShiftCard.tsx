// src/screens/openShifts/OpenShiftCard.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { OpenShift } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  shift: OpenShift;
  isOnline: boolean;
  onAccept: () => void;
  onDecline: () => void;
  isAccepting: boolean;
  isDeclining: boolean;
}

function formatDateTime(iso: string) {
  const d = new Date(iso);
  return `${d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })} \u00B7 ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

export function OpenShiftCard({ shift, isOnline, onAccept, onDecline, isAccepting, isDeclining }: Props) {
  return (
    <View style={[styles.card, { borderLeftColor: shift.urgent ? Colors.red : Colors.textMuted }]}>
      {shift.urgent && (
        <View style={styles.urgentBadge}>
          <Text style={styles.urgentText}>URGENT</Text>
        </View>
      )}
      <Text style={[Typography.cardTitle, { color: Colors.textPrimary, marginBottom: 4 }]}>{shift.clientName}</Text>
      <Text style={[Typography.body, { color: Colors.textSecondary }]}>{formatDateTime(shift.scheduledStart)}</Text>
      <Text style={[Typography.body, { color: Colors.textSecondary }]}>{shift.serviceType}</Text>
      {shift.distance != null && (
        <Text style={[Typography.timestamp, { color: Colors.textMuted, marginTop: 2 }]}>{shift.distance.toFixed(1)} km away</Text>
      )}

      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.acceptBtn, (!isOnline || isAccepting) && styles.btnDisabled]}
          onPress={onAccept}
          disabled={!isOnline || isAccepting}
        >
          <Text style={styles.acceptText}>{isAccepting ? 'Accepting\u2026' : 'Accept Shift'}</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.declineBtn, isDeclining && styles.btnDisabled]}
          onPress={onDecline}
          disabled={isDeclining}
        >
          <Text style={styles.declineText}>{isDeclining ? '\u2026' : 'Decline'}</Text>
        </TouchableOpacity>
      </View>

      {!isOnline && (
        <Text style={[Typography.timestamp, { color: Colors.amber, marginTop: 6 }]}>
          Connect to the internet to accept shifts.
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card:       { backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border, borderLeftWidth: 3, borderRadius: 10, padding: 14, marginBottom: 10 },
  urgentBadge:{ alignSelf: 'flex-start', backgroundColor: '#fef2f2', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2, marginBottom: 6 },
  urgentText: { fontSize: 10, fontWeight: '700', color: Colors.red },
  actions:    { flexDirection: 'row', gap: 8, marginTop: 12 },
  acceptBtn:  { flex: 2, backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  acceptText: { ...Typography.body, color: Colors.white, fontWeight: '700' },
  declineBtn: { flex: 1, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  declineText:{ ...Typography.body, color: Colors.textSecondary },
  btnDisabled:{ opacity: 0.5 },
});
