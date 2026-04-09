// src/screens/visit/GpsStatusBar.tsx
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

type GpsStatus = 'OK' | 'OUTSIDE_RANGE' | 'OFFLINE' | 'UNAVAILABLE';

interface Props { status: GpsStatus; distance?: number }

export function GpsStatusBar({ status, distance }: Props) {
  if (status === 'OK') {
    return (
      <View style={[styles.bar, { backgroundColor: '#f0fdf4', borderColor: Colors.green }]}>
        <Text style={[Typography.body, { color: Colors.green }]}>
          GPS captured{distance != null ? ` \u00B7 ${distance.toFixed(0)}m from client` : ''}
        </Text>
      </View>
    );
  }
  if (status === 'OUTSIDE_RANGE') {
    return (
      <View style={[styles.bar, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
        <Text style={[Typography.body, { color: Colors.textPrimary }]}>
          GPS outside expected range \u2014 your agency will review this visit.
        </Text>
      </View>
    );
  }
  if (status === 'OFFLINE') {
    return (
      <View style={[styles.bar, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
        <Text style={[Typography.body, { color: Colors.textPrimary }]}>
          Offline \u2014 GPS captured on device, will sync on reconnect
        </Text>
      </View>
    );
  }
  // UNAVAILABLE
  return (
    <View style={[styles.bar, { backgroundColor: '#fffbeb', borderColor: Colors.amber }]}>
      <Text style={[Typography.body, { color: Colors.textPrimary }]}>
        Location unavailable \u2014 your agency will verify this visit manually.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { padding: 10, borderRadius: 8, borderWidth: 1, marginBottom: 12 },
});
