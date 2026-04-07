import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import * as authApi from '../api/auth'
import { useAuthStore } from '../store/authStore'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) =>
      ({
        appName: 'hcare',
        tagline: 'Agency admin portal',
        emailLabel: 'Email',
        passwordLabel: 'Password',
        signIn: 'Sign in',
        signingIn: 'Signing in…',
        emailRequired: 'Email is required',
        emailInvalid: 'Enter a valid email address',
        passwordRequired: 'Password is required',
        invalidCredentials: 'Invalid email or password. Please try again.',
      }[key] ?? key),
  }),
}))

vi.mock('../api/auth')

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, userId: null, agencyId: null, role: null })
    vi.clearAllMocks()
  })

  it('renders email and password fields and a submit button', () => {
    renderLoginPage()
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows validation errors when submitted empty', async () => {
    renderLoginPage()
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText(/email is required/i)).toBeInTheDocument()
    expect(screen.getByText(/password is required/i)).toBeInTheDocument()
  })

  it('shows invalid email error for bad email format', async () => {
    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/email/i), 'notanemail')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText(/valid email/i)).toBeInTheDocument()
  })

  it('calls loginApi and sets auth store on successful login', async () => {
    vi.mocked(authApi.loginApi).mockResolvedValue({
      token: 'tok',
      userId: 'u1',
      agencyId: 'a1',
      role: 'ADMIN',
    })
    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/email/i), 'admin@test.com')
    await userEvent.type(screen.getByLabelText(/password/i), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => {
      expect(useAuthStore.getState().token).toBe('tok')
    })
  })

  it('shows server error message on login failure', async () => {
    vi.mocked(authApi.loginApi).mockRejectedValue(new Error('401'))
    renderLoginPage()
    await userEvent.type(screen.getByLabelText(/email/i), 'admin@test.com')
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument()
  })
})
