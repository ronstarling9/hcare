import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { ReactNode } from 'react'
import { Shell } from './components/layout/Shell'
import { SchedulePage } from './components/schedule/SchedulePage'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { ClientsPage } from './components/clients/ClientsPage'
import { CaregiversPage } from './components/caregivers/CaregiversPage'
import { PayersPage } from './components/payers/PayersPage'
import { EvvStatusPage } from './components/evv/EvvStatusPage'
import { LoginPage } from './pages/LoginPage'
import { useAuthStore } from './store/authStore'
import PortalGuard from './components/portal/PortalGuard'
import PortalLayout from './components/portal/PortalLayout'
import PortalVerifyPage from './pages/PortalVerifyPage'
import PortalDashboardPage from './pages/PortalDashboardPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()
  if (!token) {
    // Preserve the intended destination so LoginPage can redirect back after login
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

function SettingsPlaceholder() {
  const { t } = useTranslation('nav')
  return <div className="p-8 text-text-secondary">{t('settingsComingSoon')}</div>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/schedule" replace />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/caregivers" element={<CaregiversPage />} />
        <Route path="/payers" element={<PayersPage />} />
        <Route path="/evv" element={<EvvStatusPage />} />
        <Route path="/settings" element={<SettingsPlaceholder />} />
      </Route>
      <Route path="/portal/verify" element={<PortalVerifyPage />} />
      <Route
        path="/portal/dashboard"
        element={
          <PortalGuard>
            <PortalLayout>
              <PortalDashboardPage />
            </PortalLayout>
          </PortalGuard>
        }
      />
    </Routes>
  )
}
