// src/screens/clockIn/ClockInSheet.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useToday } from '@/hooks/useToday';
import { useClockIn } from '@/hooks/useClockIn';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Shift } from '@/types/domain';

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function ClockInSheet({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { upcoming } = useToday();
  // Initialize to null — upcoming is [] at mount (React Query async).
  // useEffect sets the first shift once data loads, but only if the user
  // hasn't already made an explicit selection (selectedId !== null guard).
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (upcoming.length > 0 && selectedId === null) {
      setSelectedId(upcoming[0].id);
    }
  }, [upcoming]);
  const { mutate: clockIn, isPending } = useClockIn();

  const selected = upcoming.find(s => s.id === selectedId) ?? null;

  const handleClockIn = () => {
    if (!selected) return;
    clockIn(
      { shiftId: selected.id, clientId: selected.clientId, clientName: selected.clientName },
      { onSuccess: (res) => navigation.navigate('Visit', { visitId: res.visitId }) }
    );
  };

  return (
    <View style={[styles.root, { paddingBottom: insets.bottom + 16 }]}>
      {/* Drag handle */}
      <View style={styles.handle} />

      <Text style={[Typography.sectionLabel, styles.sectionLabel]}>CLOCK IN TO</Text>

      <FlatList
        data={upcoming}
        keyExtractor={s => s.id}
        style={{ maxHeight: 320 }}
        renderItem={({ item: shift }) => {
          const isSelected = shift.id === selectedId;
          return (
            <TouchableOpacity
              style={[styles.shiftRow, isSelected && styles.shiftRowSelected]}
              onPress={() => setSelectedId(shift.id)}
            >
              <View style={[styles.leftBorder, isSelected && styles.leftBorderSelected]} />
              <View style={styles.shiftInfo}>
                <Text style={[Typography.cardTitle, { color: Colors.textPrimary }]}>{shift.clientName}</Text>
                <Text style={[Typography.body, { color: Colors.textSecondary }]}>
                  {formatTime(shift.scheduledStart)} \u00B7 {shift.serviceType}
                </Text>
              </View>
              {isSelected && <Text style={styles.selectLabel}>SELECT</Text>}
            </TouchableOpacity>
          );
        }}
        ListEmptyComponent={
          <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 20 }]}>
            No upcoming shifts today
          </Text>
        }
      />

      {selected && (
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.btn, isPending && styles.btnDisabled]}
            onPress={handleClockIn}
            disabled={isPending}
          >
            <Text style={styles.btnText}>
              {isPending ? 'Clocking in\u2026' : `Clock In \u2014 ${selected.clientName}`}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.cancel}>Cancel</Text>
          </TouchableOpacity>
        </View>
      )}

      {!selected && (
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.footer}>
          <Text style={styles.cancel}>Cancel</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root:        { flex: 1, backgroundColor: Colors.white, borderTopLeftRadius: 20, borderTopRightRadius: 20, paddingTop: 12, paddingHorizontal: 20 },
  handle:      { width: 36, height: 4, backgroundColor: Colors.border, borderRadius: 2, alignSelf: 'center', marginBottom: 20 },
  sectionLabel:{ color: Colors.textSecondary, marginBottom: 12 },
  shiftRow:    { flexDirection: 'row', alignItems: 'center', borderRadius: 10, marginBottom: 8, borderWidth: 1, borderColor: Colors.border, overflow: 'hidden' },
  shiftRowSelected: { borderColor: Colors.blue, backgroundColor: '#f0f8ff' },
  leftBorder:  { width: 3, alignSelf: 'stretch', backgroundColor: Colors.border },
  leftBorderSelected: { backgroundColor: Colors.blue },
  shiftInfo:   { flex: 1, padding: 12 },
  selectLabel: { fontSize: 10, fontWeight: '700', color: Colors.blue, paddingRight: 12 },
  footer:      { marginTop: 16 },
  btn:         { backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 14, alignItems: 'center', marginBottom: 12 },
  btnDisabled: { opacity: 0.6 },
  btnText:     { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
  cancel:      { textAlign: 'center', ...Typography.body, color: Colors.textSecondary },
});
