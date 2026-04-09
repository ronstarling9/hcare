// src/screens/visit/AdlTaskList.tsx
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { AdlTask } from '@/types/domain';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  tasks: AdlTask[];
  onToggle: (taskId: string, completed: boolean) => void;
}

export function AdlTaskList({ tasks, onToggle }: Props) {
  const pending   = tasks.filter(t => !t.completed);
  const completed = tasks.filter(t => t.completed);
  const ordered   = [...pending, ...completed];

  return (
    <View style={styles.section}>
      <Text style={[Typography.sectionLabel, styles.label]}>
        ADL TASKS {tasks.filter(t => t.completed).length} / {tasks.length}
      </Text>
      {ordered.map(task => (
        <TouchableOpacity key={task.id} style={styles.row} onPress={() => onToggle(task.id, task.completed)} activeOpacity={0.7}>
          <View style={[styles.checkbox, task.completed && styles.checkboxDone]}>
            {task.completed && <Text style={styles.checkmark}>\u2713</Text>}
          </View>
          <View style={styles.taskInfo}>
            <Text style={[Typography.body, styles.taskName, task.completed && styles.strikethrough]}>
              {task.name}
            </Text>
            {task.instructions && !task.completed && (
              <Text style={[Typography.timestamp, { color: Colors.textMuted, marginTop: 2 }]}>
                {task.instructions}
              </Text>
            )}
          </View>
        </TouchableOpacity>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  section:    { backgroundColor: Colors.white, borderRadius: 10, borderWidth: 1, borderColor: Colors.border, padding: 14, marginBottom: 12 },
  label:      { color: Colors.textSecondary, marginBottom: 10 },
  row:        { flexDirection: 'row', alignItems: 'flex-start', paddingVertical: 10, borderTopWidth: 1, borderTopColor: Colors.border },
  checkbox:   { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: Colors.border, alignItems: 'center', justifyContent: 'center', marginRight: 12, marginTop: 1 },
  checkboxDone: { backgroundColor: Colors.green, borderColor: Colors.green },
  checkmark:  { color: Colors.white, fontSize: 12, fontWeight: '700' },
  taskInfo:   { flex: 1 },
  taskName:   { color: Colors.textPrimary },
  strikethrough: { textDecorationLine: 'line-through', color: Colors.textMuted },
});
