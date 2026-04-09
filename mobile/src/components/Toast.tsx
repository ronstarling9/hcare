// src/components/Toast.tsx
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props { visible: boolean; message: string }

export function Toast({ visible, message }: Props) {
  if (!visible) return null;
  return (
    <View style={styles.toast}>
      <Text style={styles.text}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute', bottom: 100, alignSelf: 'center',
    backgroundColor: Colors.dark, borderRadius: 20, paddingHorizontal: 18, paddingVertical: 10,
    shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 8, elevation: 8,
  },
  text: { ...Typography.body, color: Colors.white, fontWeight: '600' },
});
