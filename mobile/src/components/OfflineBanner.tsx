// src/components/OfflineBanner.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  isOnline: boolean;
  syncFailed: boolean;
  syncPending: boolean;
  onRetry: () => void;
}

export function OfflineBanner({ isOnline, syncFailed, syncPending, onRetry }: Props) {
  if (isOnline && !syncFailed) return null;

  if (syncFailed) {
    return (
      <View style={[styles.banner, { backgroundColor: '#fef2f2', borderColor: Colors.red }]}>
        <Text style={[Typography.body, { color: Colors.textPrimary, flex: 1 }]}>
          Sync failed \u2014 tap to retry
        </Text>
        <TouchableOpacity onPress={onRetry} disabled={syncPending}>
          <Text style={[Typography.body, { color: Colors.blue, fontWeight: '700' }]}>
            {syncPending ? 'Retrying\u2026' : 'Retry'}
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={[styles.banner, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
      <Text style={[Typography.body, { color: Colors.textPrimary }]}>
        Offline \u2014 data saved locally, will sync on reconnect
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: { flexDirection: 'row', alignItems: 'center', padding: 10, borderWidth: 1, borderRadius: 8, marginHorizontal: 16, marginBottom: 8 },
});
