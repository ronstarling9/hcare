import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listClients, getClient, listAuthorizations } from '../api/clients'
import type { ClientResponse } from '../types/api'

/**
 * Paginated client list — for ClientsPage.
 * Does not expose a clientMap. Use useAllClients() when you need a Map<id, client>.
 */
export function useClients(page = 0, size = 20) {
  const query = useQuery({
    queryKey: ['clients', page, size],
    queryFn: () => listClients(page, size),
    staleTime: 60_000,
  })
  return {
    ...query,
    clients: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
  }
}

/** All clients in one shot — for lookup maps in Schedule screen */
export function useAllClients() {
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

export function useClientAuthorizations(clientId: string | null) {
  return useQuery({
    queryKey: ['client-authorizations', clientId],
    queryFn: () => listAuthorizations(clientId!, 0, 50),
    enabled: Boolean(clientId),
  })
}
