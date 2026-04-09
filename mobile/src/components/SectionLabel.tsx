// src/components/SectionLabel.tsx
// Reusable uppercase section header. Screens that previously inlined
// [Typography.sectionLabel, { color: Colors.textSecondary }] should use this instead.
import React from 'react';
import { Text, StyleSheet, type StyleProp, type TextStyle } from 'react-native';
import { Typography } from '@/constants/typography';
import { Colors } from '@/constants/colors';

interface Props { children: string; style?: StyleProp<TextStyle> }

export function SectionLabel({ children, style }: Props) {
  return <Text style={[styles.label, style]}>{children}</Text>;
}

const styles = StyleSheet.create({
  label: { ...Typography.sectionLabel, color: Colors.textSecondary },
});
