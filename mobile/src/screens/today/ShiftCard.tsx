// src/screens/today/ShiftCard.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import type { Shift } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  shift: Shift;
  isNext?: boolean;
  onPressMaps?: () => void;
  onPressCarePlan?: () => void;
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDuration(start: string, end: string) {
  const hrs = (new Date(end).getTime() - new Date(start).getTime()) / 3_600_000;
  return `${hrs.toFixed(1)}h`;
}

const LEFT_BORDER_COLOR: Record<string, string> = {
  UPCOMING:    Colors.blue,
  IN_PROGRESS: Colors.blue,
  COMPLETED:   Colors.green,
  CANCELLED:   Colors.textMuted,
};

export function ShiftCard({ shift, isNext, onPressMaps, onPressCarePlan }: Props) {
  const borderColor = LEFT_BORDER_COLOR[shift.status] ?? Colors.border;
  const isCancelled = shift.status === 'CANCELLED';
  const isCompleted = shift.status === 'COMPLETED';

  const handleCancelledPress = () => {
    Alert.alert('Shift Cancelled', 'This shift was cancelled by your agency.');
  };

  const cardContent = (
    <View style={[styles.card, { borderLeftColor: borderColor }]}>
      <View style={styles.row}>
        <Text style={[styles.clientName, isCancelled && styles.strikethrough]} numberOfLines={1}>
          {shift.clientName}
        </Text>
        <View style={styles.badges}>
          {isNext && <View style={styles.badgeNext}><Text style={styles.badgeNextText}>NEXT</Text></View>}
          {isCancelled && <View style={styles.badgeCancelled}><Text style={styles.badgeCancelledText}>CANCELLED</Text></View>}
          {isCompleted && <View style={styles.badgeCompleted}><Text style={styles.badgeCompletedText}>DONE</Text></View>}
        </View>
      </View>
      <Text style={styles.meta}>
        {formatTime(shift.scheduledStart)} \u2013 {formatTime(shift.scheduledEnd)} \u00B7 {formatDuration(shift.scheduledStart, shift.scheduledEnd)}
      </Text>
      <Text style={styles.meta}>{shift.serviceType}</Text>

      {/* Expanded actions — only on the next upcoming card */}
      {isNext && !isCancelled && (
        <View style={styles.actions}>
          <TouchableOpacity style={styles.actionBtn} onPress={onPressMaps}>
            <Text style={styles.actionBtnText}>Maps</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.actionBtn} onPress={onPressCarePlan}>
            <Text style={styles.actionBtnText}>Care Plan</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );

  if (isCancelled) {
    return <TouchableOpacity onPress={handleCancelledPress} activeOpacity={0.7}>{cardContent}</TouchableOpacity>;
  }

  return cardContent;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.white,
    borderWidth: 1,
    borderColor: Colors.border,
    borderLeftWidth: 3,
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  row:          { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  clientName:   { ...Typography.cardTitle, color: Colors.textPrimary, flex: 1 },
  strikethrough:{ textDecorationLine: 'line-through', color: Colors.textMuted },
  meta:         { ...Typography.body, color: Colors.textSecondary, marginBottom: 2 },
  badges:       { flexDirection: 'row', gap: 4 },
  badgeNext:    { backgroundColor: Colors.blue, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  badgeNextText:{ fontSize: 10, fontWeight: '700', color: Colors.white },
  badgeCancelled:    { backgroundColor: Colors.surface, borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2, borderWidth: 1, borderColor: Colors.border },
  badgeCancelledText:{ fontSize: 10, fontWeight: '700', color: Colors.textMuted },
  badgeCompleted:    { backgroundColor: '#f0fdf4', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  badgeCompletedText:{ fontSize: 10, fontWeight: '700', color: Colors.green },
  actions:  { flexDirection: 'row', gap: 8, marginTop: 12 },
  actionBtn:{ flex: 1, backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingVertical: 8, alignItems: 'center' },
  actionBtnText: { ...Typography.body, color: Colors.textSecondary, fontWeight: '600' },
});
