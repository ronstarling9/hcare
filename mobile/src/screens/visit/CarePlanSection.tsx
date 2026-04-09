// src/screens/visit/CarePlanSection.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { CarePlan } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props { carePlan: CarePlan; clientId: string }

export function CarePlanSection({ carePlan, clientId }: Props) {
  const [expanded, setExpanded] = useState(carePlan.updatedSinceLastVisit);
  const key = `careplan_expanded_${clientId}`;

  useEffect(() => {
    if (!carePlan.updatedSinceLastVisit) {
      AsyncStorage.getItem(key).then(v => { if (v !== null) setExpanded(v === 'true'); });
    }
  }, [clientId]);

  const toggle = () => {
    const next = !expanded;
    setExpanded(next);
    AsyncStorage.setItem(key, String(next));
  };

  return (
    <View style={styles.section}>
      <TouchableOpacity style={styles.header} onPress={toggle}>
        <View style={styles.headerLeft}>
          <Text style={[Typography.sectionLabel, { color: Colors.textSecondary }]}>CARE PLAN</Text>
          {carePlan.updatedSinceLastVisit && (
            <View style={styles.updatedPill}>
              <Text style={styles.updatedText}>Updated since your last visit</Text>
            </View>
          )}
        </View>
        <Text style={{ color: Colors.textMuted }}>{expanded ? '\u25B2' : '\u25BC'}</Text>
      </TouchableOpacity>
      {expanded && (
        <View style={styles.body}>
          {carePlan.diagnoses.length > 0 && (
            <>
              <Text style={styles.label}>DIAGNOSES</Text>
              {carePlan.diagnoses.map((d, i) => <Text key={i} style={styles.item}>\u2022 {d}</Text>)}
            </>
          )}
          {carePlan.allergies.length > 0 && (
            <>
              <Text style={[styles.label, { marginTop: 8 }]}>ALLERGIES</Text>
              {carePlan.allergies.map((a, i) => <Text key={i} style={[styles.item, { color: Colors.red }]}>\u26A0 {a}</Text>)}
            </>
          )}
          {carePlan.caregiverNotes.length > 0 && (
            <>
              <Text style={[styles.label, { marginTop: 8 }]}>NOTES</Text>
              <Text style={styles.item}>{carePlan.caregiverNotes}</Text>
            </>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  section:     { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, marginBottom: 12 },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 14 },
  headerLeft:  { flexDirection: 'row', alignItems: 'center', gap: 8, flex: 1 },
  updatedPill: { backgroundColor: '#fef9c3', borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  updatedText: { fontSize: 10, fontWeight: '700', color: '#854d0e' },
  body:        { padding: 14, paddingTop: 0 },
  label:       { fontSize: 10, fontWeight: '700', color: Colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.1, marginBottom: 4 },
  item:        { ...Typography.body, color: Colors.textPrimary, lineHeight: 20 },
});
