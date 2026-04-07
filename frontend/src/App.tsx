import { Navigate, Route, Routes } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Shell } from './components/layout/Shell'
import { SchedulePage } from './components/schedule/SchedulePage'
import { DashboardPage } from './components/dashboard/DashboardPage'
import { ClientsPage } from './components/clients/ClientsPage'
import { CaregiversPage } from './components/caregivers/CaregiversPage'
import { PayersPage } from './components/payers/PayersPage'
import { EvvStatusPage } from './components/evv/EvvStatusPage'

function SettingsPlaceholder() {
  const { t } = useTranslation('nav')
  return <div className="p-8 text-text-secondary">{t('settingsComingSoon')}</div>
}

export default function App() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Navigate to="/schedule" replace />} />
        <Route path="/schedule" element={<SchedulePage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/caregivers" element={<CaregiversPage />} />
        <Route path="/payers" element={<PayersPage />} />
        <Route path="/evv" element={<EvvStatusPage />} />
        <Route path="/settings" element={<SettingsPlaceholder />} />
      </Route>
    </Routes>
  )
}
