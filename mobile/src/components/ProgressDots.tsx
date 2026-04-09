// src/components/ProgressDots.tsx
// Shared step indicator used by all three onboarding screens.
// Extracted to avoid defining an identical component three times.
import React from 'react';
import { View } from 'react-native';
import { Colors } from '@/constants/colors';

interface Props { current: number; total: number }

export function ProgressDots({ current, total }: Props) {
  return (
    <View style={{ flexDirection: 'row', gap: 5, marginBottom: 24 }}>
      {Array.from({ length: total }).map((_, i) => (
        <View
          key={i}
          style={{
            width: i === current ? 20 : 8,
            height: 4,
            borderRadius: 2,
            backgroundColor: i === current ? Colors.blue : Colors.border,
          }}
        />
      ))}
    </View>
  );
}
