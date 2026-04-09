import { apiClient } from './client';
import type { MessagesResponse, ThreadResponse, ReplyResponse } from '@/types/api';

export const messagesApi = {
  threads: () => apiClient.get<MessagesResponse>('/mobile/messages').then(r => r.data),
  thread:  (threadId: string) =>
             apiClient.get<ThreadResponse>(`/mobile/messages/${threadId}`).then(r => r.data),
  reply:   (threadId: string, body: string) =>
             apiClient.post<ReplyResponse>(`/mobile/messages/${threadId}/reply`, { body }).then(r => r.data),
};
