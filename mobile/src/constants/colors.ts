// src/constants/colors.ts
export const Colors = {
  // Brand
  dark:          '#1a1a24',
  blue:          '#1a9afa',
  surface:       '#f6f6fa',
  white:         '#ffffff',
  border:        '#eaeaf2',

  // Text
  textPrimary:   '#1a1a24',
  textSecondary: '#747480',
  textMuted:     '#94a3b8',

  // EVV / status semantic
  green:         '#16a34a',
  amber:         '#ca8a04',
  red:           '#dc2626',
  grey:          '#94a3b8',
} as const;

export type ColorKey = keyof typeof Colors;
