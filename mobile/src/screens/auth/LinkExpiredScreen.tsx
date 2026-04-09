// src/screens/auth/LinkExpiredScreen.tsx
import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuth } from '@/hooks/useAuth';

export function LinkExpiredScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const { sendLink } = useAuth();

  const handleSend = async () => {
    if (!email.trim()) return;
    setLoading(true);
    try { await sendLink(email.trim()); } catch { /* anti-enumeration */ }
    setSent(true);
    setLoading(false);
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.header}>
        <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
      </View>
      <View style={styles.body}>
        <Text style={{ fontSize: 28, textAlign: 'center', marginBottom: 10 }}>\u26A0\uFE0F</Text>
        <Text style={[Typography.cardTitle, styles.title]}>Link expired</Text>
        <Text style={[Typography.body, styles.sub]}>
          Sign-in links expire after 24 hours. Enter your email and we'll send a fresh one.
        </Text>
        <TextInput
          style={styles.input}
          placeholder="your@email.com"
          placeholderTextColor={Colors.textMuted}
          keyboardType="email-address"
          autoCapitalize="none"
          value={email}
          onChangeText={setEmail}
        />
        <TouchableOpacity style={[styles.btn, loading && styles.btnDisabled]} onPress={handleSend} disabled={loading}>
          <Text style={styles.btnText}>{loading ? 'Sending\u2026' : 'Send New Sign-In Link'}</Text>
        </TouchableOpacity>
        {sent && (
          <Text style={[Typography.body, { color: Colors.green, textAlign: 'center', marginTop: 12 }]}>
            If that email matches your account, a link has been sent.
          </Text>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root:   { flex: 1, backgroundColor: Colors.white },
  header: { backgroundColor: Colors.dark, paddingTop: 60, paddingBottom: 28, alignItems: 'center' },
  logo:   { fontSize: 28, fontWeight: '700', color: Colors.white },
  body:   { padding: 24, alignItems: 'center' },
  title:  { color: Colors.textPrimary, marginBottom: 8, textAlign: 'center' },
  sub:    { color: Colors.textSecondary, lineHeight: 22, marginBottom: 20, textAlign: 'center' },
  input:  { width: '100%', backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, ...Typography.body, marginBottom: 12 },
  btn:    { width: '100%', backgroundColor: Colors.blue, borderRadius: 8, paddingVertical: 12, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
