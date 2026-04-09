// src/screens/visit/CareNotes.tsx
import React, { useState } from 'react';
import { View, Text, TextInput, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  onBlur: (text: string) => void;
  initialValue?: string; // restored from visitStore so notes survive re-navigation
}

export function CareNotes({ onBlur, initialValue }: Props) {
  const [text, setText] = useState(initialValue ?? '');

  return (
    <View style={styles.section}>
      <Text style={[Typography.sectionLabel, styles.label]}>CARE NOTES</Text>
      <TextInput
        style={styles.input}
        multiline
        placeholder="Add visit notes\u2026"
        placeholderTextColor={Colors.textMuted}
        value={text}
        onChangeText={setText}
        onBlur={() => onBlur(text)}
        textAlignVertical="top"
        minHeight={80}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  section: { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, padding: 14, marginBottom: 12 },
  label:   { color: Colors.textSecondary, marginBottom: 8 },
  input:   { ...Typography.body, color: Colors.textPrimary, lineHeight: 22 },
});
