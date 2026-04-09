// src/screens/visit/VisitScreen.tsx
import React, { useState, useEffect, useRef } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useMutation } from '@tanstack/react-query';
import { useVisit } from '@/hooks/useVisit';
import { useVisitStore } from '@/store/visitStore';
import { visitsApi } from '@/api/visits';
import { deleteByVisitId } from '@/db/events';
import { GpsStatusBar } from './GpsStatusBar';
import { CarePlanSection } from './CarePlanSection';
import { AdlTaskList } from './AdlTaskList';
import { CareNotes } from './CareNotes';
import { ClockOutModal } from './ClockOutModal';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { AdlTask } from '@/types/domain';

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatElapsed(clockInTime: string) {
  const elapsed = Math.floor((Date.now() - new Date(clockInTime).getTime()) / 1000);
  const h = Math.floor(elapsed / 3600);
  const m = Math.floor((elapsed % 3600) / 60);
  const s = elapsed % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export function VisitScreen({ navigation, route }: any) {
  const insets = useSafeAreaInsets();
  // shiftId always comes from visitStore — route.params.shiftId is not used.
  const { activeShiftId, activeClientId, activeClientName, clockInTime, gpsStatus, activeVisitNotes, setVisitNotes, clearActiveVisit } = useVisitStore();
  const activeVisitId = useVisitStore(s => s.activeVisitId);
  const shiftId = activeShiftId ?? '';
  const { carePlanQuery, completeTask, revertTask, saveNotes, clockOut } = useVisit(shiftId);

  // Void clock-in: calls BFF DELETE, cleans up SQLite queue, clears store.
  // The BFF rejects voids older than 5 minutes — onError surfaces that to the caregiver.
  const voidClockIn = useMutation({
    mutationFn: () => visitsApi.voidClockIn(activeVisitId!),
    onSuccess: async () => {
      await deleteByVisitId(activeVisitId!).catch(console.error);
      clearActiveVisit();
      navigation.navigate('Today');
    },
    onError: () => {
      Alert.alert(
        'Could Not Void',
        'The void window may have expired (5 minutes from clock-in). Please contact your agency to resolve this visit.',
      );
    },
  });

  const [tasks, setTasks] = useState<AdlTask[]>([]);
  const [showClockOutModal, setShowClockOutModal] = useState(false);
  const [elapsed, setElapsed] = useState(clockInTime ? formatElapsed(clockInTime) : '00:00:00');

  useEffect(() => {
    if (carePlanQuery.data) setTasks(carePlanQuery.data.adlTasks);
  }, [carePlanQuery.data]);

  useEffect(() => {
    if (!clockInTime) return;
    const id = setInterval(() => setElapsed(formatElapsed(clockInTime)), 1000);
    return () => clearInterval(id);
  }, [clockInTime]);

  const handleToggleTask = (taskId: string, completed: boolean) => {
    setTasks(prev => prev.map(t => t.id === taskId ? { ...t, completed: !completed } : t));
    if (completed) {
      revertTask.mutate({ taskId });
    } else {
      completeTask.mutate({ taskId });
    }
  };

  const handleClockOutPress = () => {
    const pending = tasks.filter(t => !t.completed);
    // Show confirmation modal only when >50% of tasks are still incomplete.
    // Below 50%, the caregiver has done most of the work — a warning interruption
    // adds friction without meaningful benefit. This threshold matches agency
    // operations guidance for ADL task compliance.
    if (pending.length > 0 && tasks.filter(t => t.completed).length / tasks.length < 0.5) {
      setShowClockOutModal(true);
    } else {
      doClockOut();
    }
  };

  const doClockOut = () => {
    setShowClockOutModal(false);
    clockOut.mutate(
      { notes: activeVisitNotes ?? '' },
      { onSuccess: () => navigation.navigate('Today') }
    );
  };

  const carePlan = carePlanQuery.data;
  const remainingTasks = tasks.filter(t => !t.completed).length;

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      {/* Sticky mini-header */}
      <View style={styles.miniHeader}>
        <TouchableOpacity onPress={() => navigation.navigate('Today')}>
          <Text style={styles.back}>\u2190 Today</Text>
        </TouchableOpacity>
        <Text style={styles.miniTimer}>{elapsed}</Text>
        <TouchableOpacity onPress={() => Alert.alert('Wrong shift?', 'This will void your clock-in if it was created less than 5 minutes ago.', [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Void Clock-In',
            style: 'destructive',
            onPress: () => voidClockIn.mutate(),
          },
        ])}>
          <Text style={styles.overflow}>\u26A0 Wrong shift?</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>
        {/* Hero */}
        <View style={styles.hero}>
          <Text style={styles.heroLabel}>IN PROGRESS</Text>
          <Text style={styles.heroClient}>{activeClientName}</Text>
          <Text style={styles.heroMeta}>{carePlan?.caregiverNotes ? 'Personal Care' : 'Visit'}</Text>
          <View style={styles.heroTimes}>
            <Text style={styles.heroTimeLabel}>Clock-in {clockInTime ? formatTime(clockInTime) : '\u2014'}</Text>
            <Text style={styles.heroElapsed}>{elapsed}</Text>
          </View>
        </View>

        {/* GPS status — dynamic: determined at clock-in by useClockIn, stored in visitStore */}
        <GpsStatusBar status={gpsStatus ?? 'UNAVAILABLE'} />

        {/* Care plan — clientId from visitStore (not carePlan.id) so collapse preference
            persists per client across care plan version changes */}
        {carePlan && (
          <CarePlanSection carePlan={carePlan} clientId={activeClientId ?? ''} />
        )}

        {/* ADL tasks */}
        {tasks.length > 0 && (
          <AdlTaskList tasks={tasks} onToggle={handleToggleTask} />
        )}

        {/* Care notes — initialValue from visitStore so text survives re-navigation */}
        <CareNotes
          initialValue={activeVisitNotes ?? ''}
          onBlur={(text) => {
            setVisitNotes(text);
            saveNotes.mutate({ notes: text });
          }}
        />

        {/* Clock Out — disabled while care plan loads so handleClockOutPress always has
            accurate task data. Tapping before care plan loads would see tasks=[] and
            skip the incomplete-task confirmation, creating an EVV record with no ADL data. */}
        <TouchableOpacity
          style={[styles.clockOutBtn, (clockOut.isPending || carePlanQuery.isLoading) && styles.clockOutBtnDisabled]}
          onPress={handleClockOutPress}
          disabled={clockOut.isPending || carePlanQuery.isLoading}
        >
          <Text style={styles.clockOutText}>
            {clockOut.isPending ? 'Clocking out\u2026' : carePlanQuery.isLoading ? 'Loading\u2026' : 'Clock Out'}
          </Text>
          <Text style={styles.clockOutSub}>GPS will be captured</Text>
        </TouchableOpacity>
      </ScrollView>

      <ClockOutModal
        visible={showClockOutModal}
        remainingTasks={remainingTasks}
        onConfirm={doClockOut}
        onCancel={() => setShowClockOutModal(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root:       { flex: 1, backgroundColor: Colors.surface },
  miniHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: Colors.white, paddingHorizontal: 16, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: Colors.border },
  back:       { ...Typography.body, color: Colors.blue },
  miniTimer:  { ...Typography.bodyMedium, color: Colors.blue, fontVariant: ['tabular-nums'] },
  overflow:   { fontSize: 12, color: Colors.amber },
  hero:       { backgroundColor: Colors.blue, borderRadius: 12, padding: 16, marginBottom: 12 },
  heroLabel:  { fontSize: 10, fontWeight: '700', color: 'rgba(255,255,255,0.7)', textTransform: 'uppercase', letterSpacing: 0.1, marginBottom: 4 },
  heroClient: { ...Typography.screenTitle, color: Colors.white, marginBottom: 4 },
  heroMeta:   { ...Typography.body, color: 'rgba(255,255,255,0.8)', marginBottom: 12 },
  heroTimes:  { flexDirection: 'row', justifyContent: 'space-between' },
  heroTimeLabel: { ...Typography.timestamp, color: 'rgba(255,255,255,0.8)' },
  heroElapsed:{ fontSize: 20, fontWeight: '700', color: Colors.white, fontVariant: ['tabular-nums'] },
  clockOutBtn:{ backgroundColor: Colors.dark, borderRadius: 10, paddingVertical: 16, alignItems: 'center', marginTop: 8 },
  clockOutBtnDisabled: { opacity: 0.6 },
  clockOutText: { ...Typography.cardTitle, color: Colors.white },
  clockOutSub:  { ...Typography.timestamp, color: 'rgba(255,255,255,0.6)', marginTop: 4 },
});
