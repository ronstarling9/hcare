// src/screens/today/ActiveVisitBanner.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Shift } from '@/types/domain';

function formatElapsed(clockInTime: string) {
  const elapsed = Math.floor((Date.now() - new Date(clockInTime).getTime()) / 1000);
  const h = Math.floor(elapsed / 3600);
  const m = Math.floor((elapsed % 3600) / 60);
  const s = elapsed % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

interface Props {
  clientName: string;
  clockInTime: string;
  onContinue: () => void;
}

export function ActiveVisitBanner({ clientName, clockInTime, onContinue }: Props) {
  const [elapsed, setElapsed] = useState(formatElapsed(clockInTime));

  useEffect(() => {
    const id = setInterval(() => setElapsed(formatElapsed(clockInTime)), 1000);
    return () => clearInterval(id);
  }, [clockInTime]);

  return (
    <View style={styles.banner}>
      <View>
        <Text style={styles.clientName}>{clientName}</Text>
        <Text style={styles.timer}>{elapsed}</Text>
      </View>
      <TouchableOpacity style={styles.btn} onPress={onContinue}>
        <Text style={styles.btnText}>Continue Visit \u2192</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  banner:     { backgroundColor: Colors.blue, padding: 14, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderRadius: 10, marginBottom: 12 },
  clientName: { ...Typography.bodyMedium, color: Colors.white },
  timer:      { fontSize: 18, fontWeight: '700', color: Colors.white, fontVariant: ['tabular-nums'] },
  btn:        { backgroundColor: 'rgba(255,255,255,0.2)', paddingVertical: 8, paddingHorizontal: 12, borderRadius: 8 },
  btnText:    { ...Typography.body, color: Colors.white, fontWeight: '700' },
});
