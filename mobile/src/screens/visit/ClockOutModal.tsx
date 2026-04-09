// src/screens/visit/ClockOutModal.tsx
import React from 'react';
import { Modal, View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '@/constants/colors';
import { Typography } from '@/constants/typography';

interface Props {
  visible: boolean;
  remainingTasks: number;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ClockOutModal({ visible, remainingTasks, onConfirm, onCancel }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.dialog}>
          <Text style={[Typography.cardTitle, styles.title]}>Tasks remaining</Text>
          <Text style={[Typography.body, styles.body]}>
            You have {remainingTasks} task{remainingTasks !== 1 ? 's' : ''} remaining. Clock out anyway?
          </Text>
          <View style={styles.actions}>
            <TouchableOpacity style={styles.cancelBtn} onPress={onCancel}>
              <Text style={styles.cancelText}>Go Back</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.confirmBtn} onPress={onConfirm}>
              <Text style={styles.confirmText}>Clock Out</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay:    { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'center', alignItems: 'center' },
  dialog:     { backgroundColor: Colors.white, borderRadius: 14, padding: 24, width: '82%' },
  title:      { color: Colors.textPrimary, marginBottom: 8 },
  body:       { color: Colors.textSecondary, lineHeight: 22, marginBottom: 20 },
  actions:    { flexDirection: 'row', gap: 10 },
  cancelBtn:  { flex: 1, paddingVertical: 12, alignItems: 'center', backgroundColor: Colors.surface, borderRadius: 8 },
  cancelText: { ...Typography.body, color: Colors.textSecondary, fontWeight: '600' },
  confirmBtn: { flex: 1, paddingVertical: 12, alignItems: 'center', backgroundColor: Colors.dark, borderRadius: 8 },
  confirmText:{ ...Typography.body, color: Colors.white, fontWeight: '700' },
});
