import { useQuery } from '@tanstack/react-query'
import { getPayerAuthorizations } from '../api/payers'

export function usePayerAuthorizations(payerId: string | null, page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['payerAuthorizations', payerId, page, size],
    queryFn: () => getPayerAuthorizations(payerId!, page, size),
    enabled: !!payerId,
    staleTime: 120_000,
  })

  return {
    ...query,
    authorizations: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}
