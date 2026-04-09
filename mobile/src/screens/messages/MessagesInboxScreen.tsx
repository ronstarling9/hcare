// src/screens/messages/MessagesInboxScreen.tsx
import React from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThreads } from '@/hooks/useMessages';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

function formatTimestamp(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  const dayDiff = Math.floor((now.getTime() - d.getTime()) / 86_400_000);
  if (dayDiff === 0) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  if (dayDiff === 1) return 'Yesterday';
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function MessagesInboxScreen({ navigation }: any) {
  const insets = useSafeAreaInsets();
  const { data: threads, isLoading } = useThreads();

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text style={[Typography.screenTitle, { color: Colors.textPrimary }]}>Messages</Text>
      </View>
      <FlatList
        data={threads ?? []}
        keyExtractor={t => t.id}
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[Typography.body, { color: Colors.textMuted, textAlign: 'center', marginTop: 40 }]}>No messages</Text>
          ) : null
        }
        renderItem={({ item: thread }) => (
          <TouchableOpacity
            style={styles.row}
            onPress={() => navigation.navigate('MessageThread', { threadId: thread.id })}
          >
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>SC</Text>
            </View>
            <View style={styles.body}>
              <View style={styles.rowTop}>
                <Text style={[styles.subject, thread.unread && styles.subjectUnread]}>{thread.subject}</Text>
                <Text style={[Typography.timestamp, { color: Colors.textMuted }]}>{formatTimestamp(thread.timestamp)}</Text>
              </View>
              <Text style={[Typography.body, { color: Colors.textMuted }]} numberOfLines={1}>{thread.previewText}</Text>
            </View>
            {thread.unread && <View style={styles.unreadDot} />}
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root:          { flex: 1, backgroundColor: Colors.white },
  header:        { padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  row:           { flexDirection: 'row', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: Colors.border },
  avatar:        { width: 40, height: 40, borderRadius: 20, backgroundColor: Colors.dark, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  avatarText:    { fontSize: 13, fontWeight: '700', color: Colors.white },
  body:          { flex: 1 },
  rowTop:        { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 3 },
  subject:       { ...Typography.body, color: Colors.textSecondary, flex: 1, marginRight: 8 },
  subjectUnread: { color: Colors.textPrimary, fontWeight: '700' },
  unreadDot:     { width: 8, height: 8, borderRadius: 4, backgroundColor: Colors.blue },
});
