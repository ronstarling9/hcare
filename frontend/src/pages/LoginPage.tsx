import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { loginApi } from '../api/auth'
import { useAuthStore } from '../store/authStore'

interface LoginFormValues {
  email: string
  password: string
}

export function LoginPage() {
  const { t } = useTranslation('auth')
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((s) => s.login)
  const [serverError, setServerError] = useState<string | null>(null)

  // After login, redirect to where the user was trying to go, or /schedule
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/schedule'

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>()

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null)
    try {
      const data = await loginApi(values.email, values.password)
      login(data.token, data.userId, data.agencyId, data.role)
      navigate(from, { replace: true })
    } catch {
      setServerError(t('invalidCredentials'))
    }
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center bg-dark"
    >
      <div
        className="w-full max-w-sm p-8 bg-dark-mid"
        style={{ border: '1px solid #3a3a4a' }}
      >
        {/* Logo / App name */}
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-white">{t('appName')}</h1>
          <p className="mt-1 text-sm text-text-secondary">
            {t('tagline')}
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {/* Email */}
          <div className="mb-4">
            <label
              htmlFor="email"
              className="block text-sm font-medium mb-1 text-text-secondary"
            >
              {t('emailLabel')}
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              className="w-full px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-[#1a9afa] bg-dark"
              style={{ border: '1px solid #3a3a4a' }}
              {...register('email', {
                required: t('emailRequired'),
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: t('emailInvalid'),
                },
              })}
            />
            {errors.email && (
              <p className="mt-1 text-xs text-red-600">
                {errors.email.message}
              </p>
            )}
          </div>

          {/* Password */}
          <div className="mb-6">
            <label
              htmlFor="password"
              className="block text-sm font-medium mb-1 text-text-secondary"
            >
              {t('passwordLabel')}
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              className="w-full px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-[#1a9afa] bg-dark"
              style={{ border: '1px solid #3a3a4a' }}
              {...register('password', { required: t('passwordRequired') })}
            />
            {errors.password && (
              <p className="mt-1 text-xs text-red-600">
                {errors.password.message}
              </p>
            )}
          </div>

          {/* Server error */}
          {serverError && (
            <div
              className="mb-4 px-3 py-2 text-sm bg-red-600/10 text-red-600 border border-red-600"
              role="alert"
            >
              {serverError}
            </div>
          )}

          {/* Submit — no border-radius per design spec */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-2 text-sm font-bold text-white transition-opacity disabled:opacity-50 bg-blue"
          >
            {isSubmitting ? t('signingIn') : t('signIn')}
          </button>
        </form>
      </div>
    </div>
  )
}
