// src/screens/today/TodayScreen.tsx
import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Linking, RefreshControl, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useToday } from '@/hooks/useToday';
import { useVisitStore } from '@/store/visitStore';
import { useAuthStore } from '@/store/authStore';
import { useOfflineSync } from '@/hooks/useOfflineSync';
import { ShiftCard } from './ShiftCard';
import { ActiveVisitBanner } from './ActiveVisitBanner';
import { OfflineBanner } from '@/components/OfflineBanner';
import { Toast } from '@/components/Toast';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Shift } from '@/types/domain';
import type { SyncEventResult } from '@/types/api';

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

export function TodayScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { upcoming, completed, cancelled, inProgress, weekShifts, nextShiftId, isLoading, refetch } = useToday();
  const { activeVisitId, activeClientName, clockInTime } = useVisitStore();
  const name = useAuthStore(s => s.name);
  const [weekExpanded, setWeekExpanded] = useState(false);
  const isVisitActive = !!activeVisitId;
  const { isOnline, syncFailed, syncPending, syncSuccess, retrySync } = useOfflineSync();
  const conflicts = useVisitStore(s => s.conflicts);

  const today = new Date();
  const dateStr = today.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  const shiftCount = upcoming.length + (inProgress ? 1 : 0);

  const handleOpenMaps = (address: string) => {
    const encoded = encodeURIComponent(address);
    // Apple Maps works on iOS; Google Maps universal link works on Android.
    const url = Platform.OS === 'ios'
      ? `http://maps.apple.com/?q=${encoded}`
      : `https://maps.google.com/?q=${encoded}`;
    Linking.openURL(url);
  };

  const handleCarePlan = (shift: Shift) => {
    navigation.navigate('CarePlan', { shiftId: shift.id });
  };

  const allShifts = [...(inProgress ? [inProgress] : []), ...upcoming, ...completed, ...cancelled];

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        {isVisitActive ? (
          <ActiveVisitBanner
            clientName={activeClientName!}
            clockInTime={clockInTime!}
            onContinue={() => navigation.navigate('Visit', { visitId: activeVisitId })}
          />
        ) : (
          <>
            <Text style={styles.greeting}>{greeting()}, {name?.split(' ')[0]}</Text>
            <Text style={styles.dateStr}>{dateStr} \u00B7 {shiftCount} shift{shiftCount !== 1 ? 's' : ''} today</Text>
          </>
        )}
      </View>

      <OfflineBanner isOnline={isOnline} syncFailed={syncFailed} syncPending={syncPending} onRetry={retrySync} />
      <Toast visible={syncSuccess} message="Visit data synced \u2713" />

      {conflicts.map(c => (
        c.conflict && (
          <TouchableOpacity
            key={c.visitId}
            style={styles.conflictBanner}
            onPress={() => navigation.navigate('ConflictDetail', { conflict: c.conflict })}
          >
            <Text style={styles.conflictText}>
              Visit not recorded \u2014 {c.conflict.clientName} shift was reassigned. Tap for details.
            </Text>
          </TouchableOpacity>
        )
      ))}

      <ScrollView
        style={styles.list}
        contentContainerStyle={{ padding: 16, paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refetch} />}
      >
        {/* Section: Today */}
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>
          {isVisitActive ? 'LATER TODAY' : 'UPCOMING'}
        </Text>

        {allShifts.length === 0 && !isLoading && (
          <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 24 }]}>No shifts today</Text>
        )}

        {allShifts.map(shift => (
          <ShiftCard
            key={shift.id}
            shift={shift}
            isNext={shift.id === nextShiftId && !isVisitActive}
            onPressMaps={() => handleOpenMaps(shift.clientAddress)}
            onPressCarePlan={() => handleCarePlan(shift)}
          />
        ))}

        {/* Section: This Week (collapsed by default) */}
        {weekShifts.length > 0 && (
          <>
            <TouchableOpacity style={styles.weekToggle} onPress={() => setWeekExpanded(v => !v)}>
              <Text style={[Typography.sectionLabel, styles.sectionLabel]}>
                {weekExpanded ? 'THIS WEEK' : `Show this week (${weekShifts.length} shift${weekShifts.length !== 1 ? 's' : ''})`}
              </Text>
              <Text style={styles.chevron}>{weekExpanded ? '\u25B2' : '\u25BC'}</Text>
            </TouchableOpacity>
            {weekExpanded && weekShifts.map(shift => (
              <View key={shift.id} style={{ opacity: 0.6 }}>
                <ShiftCard shift={shift} />
              </View>
            ))}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root:       { flex: 1, backgroundColor: Colors.surface },
  header:     { backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  greeting:   { ...Typography.screenTitle, color: Colors.textPrimary },
  dateStr:    { ...Typography.body, color: Colors.textSecondary, marginTop: 2 },
  list:       { flex: 1 },
  sectionLabel: { color: Colors.textSecondary, marginBottom: 8 },
  weekToggle: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 8 },
  chevron:    { fontSize: 10, color: Colors.textMuted },
  conflictBanner: { backgroundColor: '#fef2f2', borderWidth: 1, borderColor: Colors.red, borderRadius: 8, padding: 10, marginHorizontal: 16, marginBottom: 8 },
  conflictText: { ...Typography.body, color: Colors.red, lineHeight: 20 },
});
