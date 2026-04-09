// src/constants/typography.ts
// All values are density-independent points (pt), not CSS px.
// The app must respect iOS Dynamic Type and Android font scaling.
// Bounds: floor 0.8× (legible), ceiling 1.5× (layout usable).
export const Typography = {
  screenTitle:   { fontSize: 17, fontWeight: '700' as const },
  sectionLabel:  { fontSize: 11, fontWeight: '700' as const, letterSpacing: 0.1, textTransform: 'uppercase' as const },
  cardTitle:     { fontSize: 15, fontWeight: '700' as const },
  body:          { fontSize: 14, fontWeight: '400' as const },
  bodyMedium:    { fontSize: 14, fontWeight: '600' as const },
  timestamp:     { fontSize: 13, fontWeight: '400' as const },
} as const;
