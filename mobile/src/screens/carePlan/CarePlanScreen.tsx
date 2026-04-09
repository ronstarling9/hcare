// src/screens/carePlan/CarePlanScreen.tsx
import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { visitsApi } from '@/api/visits';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function CarePlanScreen({ route, navigation }: any) {
  const insets = useSafeAreaInsets();
  const shiftId = route?.params?.shiftId as string;

  const { data, isLoading } = useQuery({
    queryKey: ['carePlan', shiftId],
    queryFn: () => visitsApi.carePlan(shiftId).then(r => r.carePlan),
  });

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text onPress={() => navigation.goBack()} style={styles.back}>\u2190 Back</Text>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Care Plan</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: 16 }}>
        {isLoading && <Text style={[Typography.body, { color: Colors.textMuted }]}>Loading\u2026</Text>}
        {data && (
          <>
            {data.updatedSinceLastVisit && (
              <View style={styles.updatedBanner}>
                <Text style={styles.updatedText}>Updated since your last visit</Text>
              </View>
            )}
            <Section title="DIAGNOSES" items={data.diagnoses} />
            <Section title="ALLERGIES" items={data.allergies} color={Colors.red} prefix="\u26A0 " />
            <Section title="GOALS" items={data.goals} />
            {data.caregiverNotes.length > 0 && (
              <View style={styles.card}>
                <Text style={[Typography.sectionLabel, styles.sectionLabel]}>CAREGIVER NOTES</Text>
                <Text style={[Typography.body, { color: Colors.textPrimary, lineHeight: 22 }]}>{data.caregiverNotes}</Text>
              </View>
            )}
            {data.adlTasks.length > 0 && (
              <View style={styles.card}>
                <Text style={[Typography.sectionLabel, styles.sectionLabel]}>ADL TASKS ({data.adlTasks.length})</Text>
                {data.adlTasks.map(task => (
                  <View key={task.id} style={styles.taskRow}>
                    <Text style={[Typography.body, { color: Colors.textPrimary }]}>\u2022 {task.name}</Text>
                    {task.instructions && (
                      <Text style={[Typography.timestamp, { color: Colors.textMuted, marginLeft: 12, marginTop: 2 }]}>{task.instructions}</Text>
                    )}
                  </View>
                ))}
              </View>
            )}
          </>
        )}
      </ScrollView>
    </View>
  );
}

function Section({ title, items, color = Colors.textPrimary, prefix = '\u2022 ' }: { title: string; items: string[]; color?: string; prefix?: string }) {
  if (items.length === 0) return null;
  return (
    <View style={styles.card}>
      <Text style={[Typography.sectionLabel, { color: Colors.textSecondary, marginBottom: 8 }]}>{title}</Text>
      {items.map((item, i) => (
        <Text key={i} style={[Typography.body, { color, lineHeight: 22 }]}>{prefix}{item}</Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  root:         { flex: 1, backgroundColor: Colors.surface },
  header:       { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  back:         { ...Typography.body, color: Colors.blue, width: 40 },
  updatedBanner:{ backgroundColor: '#fef9c3', borderRadius: 8, padding: 10, marginBottom: 12 },
  updatedText:  { ...Typography.body, color: '#854d0e', fontWeight: '600' },
  card:         { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, padding: 14, marginBottom: 10 },
  sectionLabel: { color: Colors.textSecondary, marginBottom: 8 },
  taskRow:      { marginBottom: 8 },
});
