// src/screens/messages/MessageThreadScreen.tsx
import React, { useState } from 'react';
import { View, Text, FlatList, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThread } from '@/hooks/useMessages';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

export function MessageThreadScreen({ route, navigation }: any) {
  const insets = useSafeAreaInsets();
  const threadId = route?.params?.threadId as string;
  const { data, reply } = useThread(threadId);
  const [replyText, setReplyText] = useState('');
  const [sendError, setSendError] = useState(false);

  const handleSend = async () => {
    if (!replyText.trim()) return;
    setSendError(false);
    try {
      await reply.mutateAsync(replyText.trim());
      setReplyText('');
    } catch {
      setSendError(true);
      setTimeout(() => setSendError(false), 3000);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={[styles.header, { paddingTop: insets.top }]}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={[Typography.body, { color: Colors.blue }]}>\u2190 Back</Text>
        </TouchableOpacity>
        <Text style={[Typography.bodyMedium, { color: Colors.textPrimary, flex: 1, textAlign: 'center' }]} numberOfLines={1}>
          {data?.thread.subject ?? ''}
        </Text>
        <View style={{ width: 40 }} />
      </View>

      <FlatList
        data={data?.messages ?? []}
        keyExtractor={m => m.id}
        contentContainerStyle={{ padding: 16, paddingBottom: 8 }}
        renderItem={({ item: msg }) => {
          const isAgency = msg.senderType === 'AGENCY';
          return (
            <View style={[styles.bubble, isAgency ? styles.bubbleAgency : styles.bubbleCg]}>
              <Text style={[Typography.body, { color: isAgency ? Colors.textPrimary : Colors.white, lineHeight: 20 }]}>
                {msg.body}
              </Text>
              <Text style={[Typography.timestamp, { color: isAgency ? Colors.textMuted : 'rgba(255,255,255,0.7)', marginTop: 4 }]}>
                {new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </Text>
            </View>
          );
        }}
      />

      {sendError && (
        <Text style={styles.sendError}>Message not sent \u2014 please try again</Text>
      )}

      <View style={[styles.replyBar, { paddingBottom: insets.bottom + 8 }]}>
        <TextInput
          style={styles.replyInput}
          placeholder="Reply\u2026"
          placeholderTextColor={Colors.textMuted}
          value={replyText}
          onChangeText={setReplyText}
        />
        <TouchableOpacity
          style={[styles.sendBtn, !replyText.trim() && styles.sendBtnDisabled]}
          onPress={handleSend}
          disabled={!replyText.trim() || reply.isPending}
        >
          <Text style={styles.sendText}>Send</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root:          { flex: 1, backgroundColor: Colors.surface },
  header:        { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.white, padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  bubble:        { maxWidth: '80%', borderRadius: 12, padding: 12, marginBottom: 10 },
  bubbleAgency:  { backgroundColor: Colors.white, borderWidth: 1, borderColor: Colors.border, alignSelf: 'flex-start' },
  bubbleCg:      { backgroundColor: Colors.blue, alignSelf: 'flex-end' },
  replyBar:      { flexDirection: 'row', alignItems: 'center', backgroundColor: Colors.white, borderTopWidth: 1, borderTopColor: Colors.border, paddingHorizontal: 12, paddingTop: 8 },
  replyInput:    { flex: 1, ...Typography.body, color: Colors.textPrimary, backgroundColor: Colors.surface, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8, marginRight: 8 },
  sendBtn:       { backgroundColor: Colors.blue, borderRadius: 20, paddingHorizontal: 16, paddingVertical: 8 },
  sendBtnDisabled: { opacity: 0.4 },
  sendText:      { ...Typography.body, color: Colors.white, fontWeight: '700' },
  sendError:     { ...Typography.body, color: Colors.red, textAlign: 'center', padding: 8 },
});
