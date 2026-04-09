// src/screens/onboarding/WelcomeScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuthStore } from '@/store/authStore';
import { ProgressDots } from '@/components/ProgressDots';

export function WelcomeScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const name = useAuthStore(s => s.name);
  const agencyName = useAuthStore(s => s.agencyName);

  return (
    <View style={[styles.root, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ProgressDots current={0} total={3} />
      <Text style={{ fontSize: 32, marginBottom: 12 }}>\uD83D\uDC4B</Text>
      <Text style={[Typography.screenTitle, styles.title]}>Welcome, {name}!</Text>
      <Text style={[Typography.body, styles.sub]}>{agencyName}</Text>
      <View style={styles.divider} />
      <Text style={[Typography.body, styles.desc]}>
        Let's get two quick things set up so the app works properly for you.
      </Text>
      <TouchableOpacity style={styles.btn} onPress={() => navigation.navigate('Notifications')}>
        <Text style={styles.btnText}>Get Started \u2192</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root:    { flex: 1, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', padding: 28 },
  title:   { color: Colors.textPrimary, marginBottom: 4 },
  sub:     { color: Colors.textSecondary, marginBottom: 4 },
  divider: { height: 1, width: '100%', backgroundColor: Colors.border, marginVertical: 20 },
  desc:    { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 28 },
  btn:     { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 13, alignItems: 'center' },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
