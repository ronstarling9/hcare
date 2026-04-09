// src/screens/profile/ProfileScreen.tsx
import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useProfile } from '@/hooks/useProfile';
import { useAuth } from '@/hooks/useAuth';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import type { Credential } from '@/types/domain';

const CREDENTIAL_COLOR: Record<string, string> = {
  VALID:          Colors.green,
  EXPIRING_SOON:  Colors.amber,
  EXPIRED:        Colors.red,
};

function CredentialRow({ credential }: { credential: Credential }) {
  const color = CREDENTIAL_COLOR[credential.status] ?? Colors.textMuted;
  const expiry = new Date(credential.expiryDate).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  const label = credential.status === 'EXPIRED' ? 'Expired' :
                credential.status === 'EXPIRING_SOON' ? `Expires ${expiry}` :
                `Valid \u00B7 ${expiry}`;

  return (
    <View style={styles.credentialRow}>
      <Text style={[Typography.body, { color: Colors.textPrimary }]}>{credential.name}</Text>
      <Text style={[Typography.timestamp, { color }]}>{label}</Text>
    </View>
  );
}

export function ProfileScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { profile, stats, isLoading } = useProfile();
  const { logout } = useAuth();

  const initials = profile?.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2) ?? '??';

  const handleSignOut = () => {
    Alert.alert(
      'Sign out of hcare?',
      "You'll need a sign-in link to log back in.",
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Sign Out', style: 'destructive', onPress: () => logout() },
      ]
    );
  };

  if (isLoading) {
    return <View style={[styles.root, { paddingTop: insets.top }]} />;
  }

  return (
    <ScrollView style={[styles.root, { paddingTop: insets.top }]} contentContainerStyle={{ paddingBottom: 40 }}>
      {/* Avatar + name */}
      <View style={styles.avatarSection}>
        <View style={styles.avatar}>
          <Text style={styles.initials}>{initials}</Text>
        </View>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary, marginTop: 10 }]}>{profile?.name}</Text>
        <Text style={[Typography.body, { color: Colors.textSecondary }]}>
          {profile?.agencyName} \u00B7 {profile?.primaryCredentialType}
        </Text>
      </View>

      <View style={styles.content}>
        {/* Credentials */}
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>CREDENTIALS</Text>
        <View style={styles.card}>
          {profile?.credentials.map((c, i) => (
            <React.Fragment key={c.name}>
              {i > 0 && <View style={styles.divider} />}
              <CredentialRow credential={c} />
            </React.Fragment>
          ))}
        </View>

        {/* This Month */}
        <Text style={[Typography.sectionLabel, styles.sectionLabel]}>THIS MONTH</Text>
        <View style={[styles.card, styles.statsRow]}>
          <View style={styles.statCell}>
            <Text style={styles.statNumber}>{stats?.shiftsCompleted ?? '\u2014'}</Text>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>Shifts</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.statCell}>
            <Text style={styles.statNumber}>{stats?.hoursWorked ?? '\u2014'}h</Text>
            <Text style={[Typography.body, { color: Colors.textSecondary }]}>Hours</Text>
          </View>
        </View>

        {/* Nav rows */}
        <View style={styles.card}>
          <TouchableOpacity style={styles.navRow} onPress={() => navigation.navigate('Settings')}>
            <Text style={[Typography.body, { color: Colors.textPrimary }]}>Settings</Text>
            <Text style={{ color: Colors.textMuted }}>\u2192</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.navRow} onPress={handleSignOut}>
            <Text style={[Typography.body, { color: Colors.red }]}>Sign Out</Text>
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root:        { flex: 1, backgroundColor: Colors.surface },
  avatarSection:{ alignItems: 'center', padding: 24, backgroundColor: Colors.white, borderBottomWidth: 1, borderBottomColor: Colors.border },
  avatar:      { width: 52, height: 52, borderRadius: 26, backgroundColor: Colors.blue, alignItems: 'center', justifyContent: 'center' },
  initials:    { fontSize: 20, fontWeight: '700', color: Colors.white },
  content:     { padding: 16 },
  sectionLabel:{ color: Colors.textSecondary, marginBottom: 8, marginTop: 12 },
  card:        { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, marginBottom: 12, overflow: 'hidden' },
  credentialRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 12 },
  divider:     { height: 1, backgroundColor: Colors.border },
  statsRow:    { flexDirection: 'row' },
  statCell:    { flex: 1, alignItems: 'center', paddingVertical: 14 },
  statNumber:  { fontSize: 22, fontWeight: '700', color: Colors.textPrimary },
  statDivider: { width: 1, backgroundColor: Colors.border },
  navRow:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 13 },
});
