// src/hooks/useMessages.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { messagesApi } from '@/api/messages';

export function useThreads() {
  return useQuery({
    queryKey: ['messages'],
    queryFn: () => messagesApi.threads().then(r => r.threads),
  });
}

export function useThread(threadId: string) {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: ['messages', threadId],
    queryFn: () => messagesApi.thread(threadId),
    enabled: !!threadId,
  });

  const reply = useMutation({
    mutationFn: (body: string) => messagesApi.reply(threadId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['messages', threadId] }),
  });

  return { ...query, reply };
}
