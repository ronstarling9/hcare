// src/screens/onboarding/LocationScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Location from 'expo-location';
import { useAuthStore } from '@/store/authStore';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { ProgressDots } from '@/components/ProgressDots';

export function LocationScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const setFirstLoginComplete = () =>
    useAuthStore.setState({ firstLogin: false });

  const handleAllow = async () => {
    await Location.requestForegroundPermissionsAsync();
    setFirstLoginComplete();
  };

  return (
    <View style={[styles.root, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ProgressDots current={2} total={3} />
      <Text style={{ fontSize: 32, marginBottom: 12 }}>\uD83D\uDCCD</Text>
      <Text style={[Typography.screenTitle, styles.title]}>Location for EVV</Text>
      <Text style={[Typography.body, styles.desc]}>
        Your agency uses Electronic Visit Verification. Your GPS location is captured only at clock-in and clock-out — never tracked during a visit.
      </Text>
      <Text style={[Typography.body, { color: Colors.amber, lineHeight: 20, marginBottom: 20, textAlign: 'center' }]}>
        Declining location access may create compliance issues for your visit records. Your agency will need to verify visits manually.
      </Text>
      <TouchableOpacity style={styles.btn} onPress={handleAllow}>
        <Text style={styles.btnText}>Allow Location Access</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={setFirstLoginComplete}>
        <Text style={styles.skip}>Not now</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root:  { flex: 1, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', padding: 28 },
  title: { color: Colors.textPrimary, marginBottom: 16, textAlign: 'center' },
  desc:  { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 16 },
  btn:   { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 13, alignItems: 'center', marginBottom: 14 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
  skip:  { ...Typography.body, color: Colors.textMuted },
});
