import { useQuery } from '@tanstack/react-query'
import { getPortalDashboard } from '../api/portal'
import { usePortalAuthStore } from '../store/portalAuthStore'

export function usePortalDashboard() {
  const clientId = usePortalAuthStore((s) => s.clientId)

  return useQuery({
    queryKey: ['portal-dashboard', clientId],
    queryFn: getPortalDashboard,
    // Do not fire the query before PortalGuard has validated the session; without this guard
    // a null clientId on first render sends a request with no Authorization header, the backend
    // returns 401, and the portalClient 401 interceptor triggers a full navigation to
    // /portal/verify?reason=session_expired — a race condition PortalGuard would have handled.
    enabled: !!clientId,
    retry: false,
    // Do not show stale data silently — if cache is stale and network fails, show error state.
    staleTime: 60_000,        // 1 minute — fresh window
    gcTime: 60_000,           // purge cache after 1 minute to avoid stale display
    throwOnError: false,
    // Note: meta.onError was removed in React Query v5. 403 (access_revoked) and 410
    // (client_discharged) are handled in the component via useEffect watching isError/error.
  })
}
