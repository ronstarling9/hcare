import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listClients, getClient } from '../api/clients'
import type { ClientResponse } from '../types/api'

export function useClients() {
  const query = useQuery({
    queryKey: ['clients', 'all'],
    queryFn: () => listClients(0, 100),
    staleTime: 60_000,
  })

  const clientMap = useMemo<Map<string, ClientResponse>>(() => {
    if (!query.data?.content) return new Map()
    return new Map(query.data.content.map((c) => [c.id, c]))
  }, [query.data])

  return {
    ...query,
    clients: query.data?.content ?? [],
    clientMap,
  }
}

export function useClientDetail(id: string | null) {
  return useQuery({
    queryKey: ['client', id],
    queryFn: () => getClient(id!),
    enabled: Boolean(id),
  })
}
