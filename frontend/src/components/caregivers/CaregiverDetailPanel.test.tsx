import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { CaregiverDetailPanel } from './CaregiverDetailPanel'
import {
  useCaregiverDetail,
  useCaregiverCredentials,
  useCaregiverBackgroundChecks,
  useCaregiverShiftHistory,
  useVerifyCredential,
} from '../../hooks/useCaregivers'
import { usePanelStore } from '../../store/panelStore'
import { useAuthStore } from '../../store/authStore'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}))

vi.mock('../../hooks/useCaregivers')
vi.mock('../../store/panelStore')
vi.mock('../../store/authStore')

const MOCK_CAREGIVER = {
  id: 'caregiver-1',
  firstName: 'Maria',
  lastName: 'Santos',
  email: 'maria@sunrise.dev',
  phone: null,
  address: null,
  hireDate: null,
  hasPet: false,
  status: 'ACTIVE',
  createdAt: '2026-01-10T08:00:00',
}

describe('CaregiverDetailPanel — initialTab type guard', () => {
  beforeEach(() => {
    vi.mocked(usePanelStore).mockReturnValue({
      closePanel: vi.fn(),
    } as ReturnType<typeof usePanelStore>)
    // useAuthStore uses a selector: useAuthStore((s) => s.role)
    vi.mocked(useAuthStore).mockImplementation((selector?: (s: any) => any) =>
      selector ? selector({ role: 'ADMIN' }) : { role: 'ADMIN' }
    )
    vi.mocked(useCaregiverDetail).mockReturnValue({
      data: MOCK_CAREGIVER,
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useCaregiverDetail>)
    vi.mocked(useCaregiverCredentials).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50, first: true, last: true },
    } as unknown as ReturnType<typeof useCaregiverCredentials>)
    vi.mocked(useCaregiverBackgroundChecks).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50, first: true, last: true },
    } as unknown as ReturnType<typeof useCaregiverBackgroundChecks>)
    vi.mocked(useCaregiverShiftHistory).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true },
    } as unknown as ReturnType<typeof useCaregiverShiftHistory>)
    vi.mocked(useVerifyCredential).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      variables: undefined,
    } as unknown as ReturnType<typeof useVerifyCredential>)
  })

  it('opens on the credentials tab when initialTab is "credentials"', () => {
    render(
      <CaregiverDetailPanel
        caregiverId="caregiver-1"
        backLabel="← Caregivers"
        initialTab="credentials"
      />
    )
    // credentials tab is active — empty state renders t('noCredentials')
    expect(screen.getByText('noCredentials')).toBeInTheDocument()
    // overview tab content is not rendered
    expect(screen.queryByText('fieldPhone')).not.toBeInTheDocument()
  })

  it('falls back to overview tab when initialTab is an unrecognised string', () => {
    render(
      <CaregiverDetailPanel
        caregiverId="caregiver-1"
        backLabel="← Caregivers"
        initialTab="bogus"
      />
    )
    // overview tab is active — renders t('fieldPhone') in the overview grid
    expect(screen.getByText('fieldPhone')).toBeInTheDocument()
    expect(screen.queryByText('noCredentials')).not.toBeInTheDocument()
  })

  it('falls back to overview tab when initialTab is undefined', () => {
    render(
      <CaregiverDetailPanel
        caregiverId="caregiver-1"
        backLabel="← Caregivers"
      />
    )
    expect(screen.getByText('fieldPhone')).toBeInTheDocument()
    expect(screen.queryByText('noCredentials')).not.toBeInTheDocument()
  })
})
