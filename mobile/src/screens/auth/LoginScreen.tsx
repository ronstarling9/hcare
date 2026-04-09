// src/screens/auth/LoginScreen.tsx
import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';
import { useAuth } from '@/hooks/useAuth';

export function LoginScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const { sendLink } = useAuth();

  const handleSend = async () => {
    if (!email.trim()) return;
    setLoading(true);
    try {
      await sendLink(email.trim());
      setSent(true);
    } catch {
      // Anti-enumeration: always show success
      setSent(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      {/* Branded header */}
      <View style={styles.header}>
        <Text style={styles.logo}>h<Text style={{ color: Colors.blue }}>.</Text>care</Text>
        <Text style={styles.logoSub}>Caregiver App</Text>
      </View>

      <View style={styles.body}>
        <Text style={[Typography.cardTitle, styles.title]}>Check your email</Text>
        <Text style={[Typography.body, styles.sub]}>
          Your agency sent you a sign-in link. Tap that link on your phone to get started.
        </Text>

        <View style={styles.divider} />

        <Text style={[Typography.body, { color: Colors.textMuted, marginBottom: 10 }]}>
          Can't find the email? Request a new sign-in link.
        </Text>

        <TextInput
          style={styles.input}
          placeholder="your@email.com"
          placeholderTextColor={Colors.textMuted}
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          value={email}
          onChangeText={setEmail}
        />

        <TouchableOpacity
          style={[styles.btn, loading && styles.btnDisabled]}
          onPress={handleSend}
          disabled={loading}
        >
          <Text style={styles.btnText}>
            {loading ? 'Sending\u2026' : 'Send New Sign-In Link'}
          </Text>
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
  root:    { flex: 1, backgroundColor: Colors.white },
  header:  { backgroundColor: Colors.dark, paddingTop: 60, paddingBottom: 32, alignItems: 'center' },
  logo:    { fontSize: 28, fontWeight: '700', color: Colors.white, letterSpacing: -0.5 },
  logoSub: { ...Typography.timestamp, color: Colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.1, marginTop: 4 },
  body:    { padding: 24 },
  title:   { color: Colors.textPrimary, marginBottom: 8 },
  sub:     { color: Colors.textSecondary, lineHeight: 22, marginBottom: 20 },
  divider: { height: 1, backgroundColor: Colors.border, marginBottom: 16 },
  input:   { backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, ...Typography.body, marginBottom: 12 },
  btn:     { backgroundColor: Colors.dark, borderRadius: 8, paddingVertical: 12, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { ...Typography.bodyMedium, color: Colors.white, fontWeight: '700' },
});
