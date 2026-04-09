// src/screens/settings/SettingsScreen.tsx
import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Linking } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import * as Location from 'expo-location';
import Constants from 'expo-constants';
import { useAuthStore } from '@/store/authStore';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function SettingsScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const agencyName = useAuthStore(s => s.agencyName);
  const [notifStatus, setNotifStatus] = useState('\u2014');
  const [locationStatus, setLocationStatus] = useState('\u2014');

  useEffect(() => {
    Notifications.getPermissionsAsync().then(p =>
      setNotifStatus(p.granted ? 'On' : 'Off')
    );
    Location.getForegroundPermissionsAsync().then(p => {
      if (p.status === 'granted') setLocationStatus('Always / When In Use');
      else if (p.status === 'denied') setLocationStatus('Denied');
      else setLocationStatus('Not set');
    });
  }, []);

  const openSettings = () => Linking.openSettings();

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={[Typography.body, { color: Colors.blue }]}>\u2190 Back</Text>
        </TouchableOpacity>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Settings</Text>
        <View style={{ width: 40 }} />
      </View>

      <View style={{ padding: 16 }}>
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>PERMISSIONS</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>Notifications</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>{notifStatus}</Text>
              <TouchableOpacity onPress={openSettings}>
                <Text style={[Typography.body, { color: Colors.blue }]}>Change \u2192</Text>
              </TouchableOpacity>
            </View>
          </View>
          <View style={styles.divider} />
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>Location access</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text style={[Typography.body, { color: Colors.textSecondary }]}>{locationStatus}</Text>
              <TouchableOpacity onPress={openSettings}>
                <Text style={[Typography.body, { color: Colors.blue }]}>Change \u2192</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>

        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>AGENCY</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>Agency</Text>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>{agencyName ?? '\u2014'}</Text>
          </View>
        </View>

        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>APP</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>App version</Text>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>
              {Constants.expoConfig?.version ?? '\u2014'}
            </Text>
          </View>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.row}>
            <Text style={[Typography.body, { color: Colors.blue }]}>Terms of Service</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.row}>
            <Text style={[Typography.body, { color: Colors.blue }]}>Privacy Policy</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root:        { flex: 1, backgroundColor: Colors.surface },
  header:      { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  sectionLabel:{ color: Colors.textSecondary, marginBottom: 8, marginTop: 12 },
  card:        { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, overflow: 'hidden', marginBottom: 4 },
  row:         { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 13 },
  divider:     { height: 1, backgroundColor: Colors.border },
});
