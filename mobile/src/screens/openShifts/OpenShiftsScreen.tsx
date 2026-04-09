// src/screens/openShifts/OpenShiftsScreen.tsx
import React, { useState } from 'react';
import { View, Text, FlatList, StyleSheet, RefreshControl } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import NetInfo from '@react-native-community/netinfo';
import { useOpenShifts } from '@/hooks/useOpenShifts';
import { OpenShiftCard } from './OpenShiftCard';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function OpenShiftsScreen() {
  const insets = useSafeAreaInsets();
  const { shifts, isLoading, refetch, accept, decline } = useOpenShifts();
  const [isOnline, setIsOnline] = useState(true);

  React.useEffect(() => {
    const unsub = NetInfo.addEventListener(state => setIsOnline(!!state.isConnected));
    return unsub;
  }, []);

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Open Shifts</Text>
      </View>
      <FlatList
        data={shifts}
        keyExtractor={s => s.id}
        contentContainerStyle={{ padding: 16 }}
        refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refetch} />}
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 40 }]}>
              No open shifts right now. We'll notify you when one becomes available.
            </Text>
          ) : null
        }
        renderItem={({ item }) => (
          <OpenShiftCard
            shift={item}
            isOnline={isOnline}
            onAccept={() => accept.mutate(item.id)}
            onDecline={() => decline.mutate(item.id)}
            isAccepting={accept.isPending && accept.variables === item.id}
            isDeclining={decline.isPending && decline.variables === item.id}
          />
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root:   { flex: 1, backgroundColor: Colors.surface },
  header: { backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
});
