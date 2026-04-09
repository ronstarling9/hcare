// src/screens/onboarding/NotificationsScreen.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { ProgressDots } from '@/components/ProgressDots';

export function NotificationsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();

  const handleEnable = async () => {
    await Notifications.requestPermissionsAsync();
    navigation.navigate('Location');
  };

  return (
    <View style={[styles.root, { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 16 }]}>
      <ProgressDots current={1} total={3} />
      <Text style={{ fontSize: 32, marginBottom: 12 }}>\uD83D\uDD14</Text>
      <Text style={[Typography.screenTitle, styles.title]}>Stay in the loop</Text>
      <Text style={[Typography.body, styles.desc]}>
        Enable notifications to get alerted when open shifts are available and receive messages from your agency.
      </Text>
      <TouchableOpacity style={styles.btn} onPress={handleEnable}>
        <Text style={styles.btnText}>Enable Notifications</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => navigation.navigate('Location')}>
        <Text style={styles.skip}>Not now</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root:  { flex: 1, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', padding: 28 },
  title: { color: Colors.textPrimary, marginBottom: 16, textAlign: 'center' },
  desc:  { color: Colors.textSecondary, lineHeight: 22, textAlign: 'center', marginBottom: 28 },
  btn:   { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 13, alignItems: 'center', marginBottom: 14 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
  skip:  { ...Typography.body, color: Colors.textMuted, textAlign: 'center' },
});
