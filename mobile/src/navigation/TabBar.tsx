// src/navigation/TabBar.tsx
import React from 'react';
import { View, TouchableOpacity, Text, StyleSheet } from 'react-native';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Colors } from '@/constants/colors';
import { useVisitStore } from '@/store/visitStore';

const TAB_LABELS: Record<string, string> = {
  Today:      'Today',
  OpenShifts: 'Open Shifts',
  ClockIn:    'Clock In',
  Messages:   'Messages',
  Profile:    'Profile',
};

export function TabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const { activeVisitId } = useVisitStore();
  const isVisitActive = !!activeVisitId;

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      {state.routes.map((route, index) => {
        const isFocused = state.index === index;
        const isCenter = route.name === 'ClockIn';

        const onPress = () => {
          const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
          if (!isFocused && !event.defaultPrevented) {
            navigation.navigate(route.name);
          }
        };

        if (isCenter) {
          const handleFabPress = () => {
            if (isVisitActive) {
              // Return to the active visit — Visit is in the parent stack
              navigation.getParent()?.navigate('Visit' as never, { visitId: activeVisitId } as never);
            } else {
              // Present the clock-in sheet as a modal from the parent (root) stack
              // so it floats above the tab bar as a proper bottom sheet
              navigation.getParent()?.navigate('ClockInSheet' as never);
            }
          };

          return (
            <TouchableOpacity
              key={route.key}
              style={styles.fabContainer}
              onPress={handleFabPress}
              accessibilityRole="button"
              accessibilityLabel={isVisitActive ? 'Visit Active — tap to return' : 'Clock In'}
            >
              <View style={[styles.fab, isVisitActive && styles.fabActive]}>
                <Text style={styles.fabIcon}>{isVisitActive ? '\u23F9' : '\u23F1'}</Text>
              </View>
              <Text style={[styles.fabLabel, isVisitActive && styles.fabLabelActive]}>
                {isVisitActive ? 'Visit Active' : 'Clock In'}
              </Text>
            </TouchableOpacity>
          );
        }

        return (
          <TouchableOpacity key={route.key} style={styles.tab} onPress={onPress} accessibilityRole="tab">
            <Text style={[styles.label, isFocused && styles.labelActive]}>
              {TAB_LABELS[route.name] ?? route.name}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.white,
    borderTopWidth: 1,
    borderTopColor: Colors.border,
    paddingTop: 6,
    minHeight: 56,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 4,
    minHeight: 44, // minimum touch target
  },
  label: {
    fontSize: 11,
    color: Colors.textMuted,
    fontWeight: '500',
    textAlign: 'center',
  },
  labelActive: { color: Colors.blue, fontWeight: '700' },
  fabContainer: {
    flex: 1,
    alignItems: 'center',
    marginTop: -20,
  },
  fab: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Colors.blue,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: Colors.blue,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.45,
    shadowRadius: 6,
    elevation: 6,
  },
  fabActive: { backgroundColor: Colors.red },
  fabIcon: { fontSize: 20 },
  fabLabel: { fontSize: 11, color: Colors.blue, fontWeight: '700', marginTop: 2 },
  fabLabelActive: { color: Colors.red },
});
