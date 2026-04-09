// src/screens/auth/DeepLinkHandlerScreen.tsx
import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, ActivityIndicator, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuth, AuthError } from '@/hooks/useAuth';

export function DeepLinkHandlerScreen({ route, navigation }: any) {
  const { exchangeToken } = useAuth();
  const [networkError, setNetworkError] = useState(false);

  const tryExchange = useCallback(() => {
    const token = route?.params?.token as string | undefined;
    if (!token) {
      navigation.replace('LinkExpired');
      return;
    }
    setNetworkError(false);
    exchangeToken(token)
      .then(() => {
        // RootNavigator will re-render based on isAuthenticated — no explicit navigate needed
      })
      .catch((err) => {
        if (err instanceof AuthError && err.code === 'NETWORK_ERROR') {
          // Token may still be valid — let the caregiver retry once online
          setNetworkError(true);
        } else {
          navigation.replace('LinkExpired');
        }
      });
  }, []);

  useEffect(() => { tryExchange(); }, []);

  if (networkError) {
    return (
      <View style={styles.root}>
        <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
        <Text style={[Typography.body, { color: Colors.textMuted, marginTop: 32, textAlign: 'center', paddingHorizontal: 32 }]}>
          No internet connection.{'\n'}Check your connection and try again.
        </Text>
        <TouchableOpacity style={styles.retryBtn} onPress={tryExchange}>
          <Text style={styles.retryText}>Try Again</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
      <ActivityIndicator size="large" color={Colors.blue} style={{ marginTop: 32 }} />
      <Text style={[Typography.body, { color: Colors.textSecondary, marginTop: 16 }]}>Signing you in\u2026</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root:      { flex: 1, backgroundColor: Colors.dark, alignItems: 'center', justifyContent: 'center' },
  logo:      { fontSize: 36, fontWeight: '700', color: Colors.white },
  retryBtn:  { marginTop: 24, backgroundColor: Colors.blue, borderRadius: 8, paddingHorizontal: 24, paddingVertical: 12 },
  retryText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
