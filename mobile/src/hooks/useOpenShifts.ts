// src/hooks/useOpenShifts.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { shiftsApi } from '@/api/shifts';

export function useOpenShifts() {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: ['shifts', 'open'],
    queryFn: () => shiftsApi.open().then(r => r.shifts),
  });

  const accept = useMutation({
    mutationFn: (shiftId: string) => shiftsApi.accept(shiftId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shifts', 'open'] }),
  });

  const decline = useMutation({
    mutationFn: (shiftId: string) => shiftsApi.decline(shiftId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shifts', 'open'] }),
  });

  return { shifts: query.data ?? [], isLoading: query.isLoading, refetch: query.refetch, accept, decline };
}
