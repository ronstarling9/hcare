// src/screens/conflict/ConflictDetailScreen.tsx
import React, { useCallback } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { deleteByVisitId } from '@/db/events';
import { useVisitStore } from '@/store/visitStore';
import type { ConflictDetail } from '@/types/domain';

export function ConflictDetailScreen({ route, navigation }: any) {
  const insets = useSafeAreaInsets();
  const conflict = route?.params?.conflict as ConflictDetail | undefined;
  const removeConflict = useVisitStore(s => s.removeConflict);

  // On navigate away, delete the SQLite events for this conflict so future syncs
  // don't re-surface it, and clear the in-memory banner immediately (Option A).
  useFocusEffect(
    useCallback(() => {
      return () => {
        if (conflict?.visitId) {
          deleteByVisitId(conflict.visitId).catch(console.error);
          removeConflict(conflict.visitId);
        }
      };
    }, [conflict?.visitId, removeConflict])
  );

  const formatDateTime = (iso: string) =>
    new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={[Typography.body, { color: Colors.blue }]}>\u2190 Back</Text>
        </TouchableOpacity>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Visit Not Recorded</Text>
        <View style={{ width: 40 }} />
      </View>

      <View style={styles.body}>
        <Text style={{ fontSize: 40, textAlign: 'center', marginBottom: 16 }}>\u26A0\uFE0F</Text>
        <Text style={[Typography.cardTitle, styles.title]}>This visit was not recorded</Text>
        <Text style={[Typography.body, styles.explanation]}>
          The shift for {conflict?.clientName ?? 'this client'} on {conflict ? new Date(conflict.shiftDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric' }) : 'this date'} was reassigned to another caregiver while you were offline. Your visit data could not be saved.
        </Text>

        {conflict && (
          <View style={styles.detailCard}>
            <Text style={[Typography.sectionLabel, { color: Colors.textSecondary, marginBottom: 10 }]}>YOUR VISIT TIMES</Text>
            <View style={styles.detailRow}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>Clocked in</Text>
              <Text style={[Typography.body, { color: Colors.textPrimary, fontWeight: '600' }]}>{formatDateTime(conflict.caregiverClockIn)}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>Clocked out</Text>
              <Text style={[Typography.body, { color: Colors.textPrimary, fontWeight: '600' }]}>{formatDateTime(conflict.caregiverClockOut)}</Text>
            </View>
          </View>
        )}

        <Text style={[Typography.body, styles.guidance]}>
          Contact your agency to resolve this visit and ensure you are compensated for time worked.
        </Text>

        <TouchableOpacity
          style={styles.contactBtn}
          onPress={() => navigation.navigate('Messages')}
        >
          <Text style={styles.contactBtnText}>Contact Agency</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root:       { flex: 1, backgroundColor: Colors.white },
  header:     { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  body:       { padding: 24 },
  title:      { color: Colors.textPrimary, textAlign: 'center', marginBottom: 12 },
  explanation:{ color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 20 },
  detailCard: { backgroundColor: Colors.surface, borderRadius: 10, padding: 14, marginBottom: 20 },
  detailRow:  { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
  guidance:   { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 24 },
  contactBtn: { backgroundColor: Colors.dark, borderRadius: 8, paddingVertical: 14, alignItems: 'center' },
  contactBtnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
