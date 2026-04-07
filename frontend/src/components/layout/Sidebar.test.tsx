import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Sidebar } from './Sidebar'

// i18next is initialized synchronously in src/test/setup.ts with real English strings.
// No mock needed — useTranslation returns actual translations in tests.

function renderSidebar(initialPath = '/schedule') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar userName="Ada Lovelace" userRole="ADMIN" />
    </MemoryRouter>
  )
}

describe('Sidebar', () => {
  it('renders h.care wordmark', () => {
    renderSidebar()
    expect(screen.getByText('h')).toBeInTheDocument()
    expect(screen.getByText('care')).toBeInTheDocument()
  })

  it('renders all nav items', () => {
    renderSidebar()
    expect(screen.getByText('Schedule')).toBeInTheDocument()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Clients')).toBeInTheDocument()
    expect(screen.getByText('Caregivers')).toBeInTheDocument()
    expect(screen.getByText('Payers')).toBeInTheDocument()
    expect(screen.getByText('EVV Status')).toBeInTheDocument()
  })

  it('renders user initials in footer', () => {
    renderSidebar()
    expect(screen.getByText('AL')).toBeInTheDocument()
  })

  it('renders user name and role in footer', () => {
    renderSidebar()
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('admin')).toBeInTheDocument()
  })

  it('does not show dashboard badge when redEvvCount is 0', () => {
    renderSidebar()
    expect(screen.queryByText(/^[0-9]$/)).not.toBeInTheDocument()
  })

  it('shows dashboard badge when redEvvCount > 0', () => {
    render(
      <MemoryRouter initialEntries={['/schedule']}>
        <Sidebar userName="Admin" userRole="ADMIN" redEvvCount={3} />
      </MemoryRouter>
    )
    expect(screen.getByText('3')).toBeInTheDocument()
  })
})
