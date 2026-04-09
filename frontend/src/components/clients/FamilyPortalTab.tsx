import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { listFamilyPortalUsers, type FamilyPortalUserResponse } from '../../api/clients'
import { inviteFamilyPortalUser, removeFamilyPortalUser } from '../../api/portal'

interface Props {
  clientId: string
}

export default function FamilyPortalTab({ clientId }: Props) {
  const { t } = useTranslation('portal')
  const qc = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [prefilledEmail, setPrefilledEmail] = useState<string | null>(null)
  const [confirmRemove, setConfirmRemove] = useState<FamilyPortalUserResponse | null>(null)
  const [inviteUrl, setInviteUrl] = useState<string | null>(null)
  const [expiresAt, setExpiresAt] = useState<string | null>(null)
  const [email, setEmail] = useState('')
  const [copied, setCopied] = useState(false)
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [removeError, setRemoveError] = useState<string | null>(null)
  const isReInvite = prefilledEmail !== null

  const { data, isLoading: usersLoading, isError: usersError } = useQuery({
    queryKey: ['family-portal-users', clientId],
    queryFn: () => listFamilyPortalUsers(clientId),
  })

  const inviteMutation = useMutation({
    mutationFn: ({ email }: { email: string }) =>
      inviteFamilyPortalUser(clientId, email),
    onSuccess: (res) => {
      setInviteError(null)
      setInviteUrl(res.inviteUrl)
      setExpiresAt(res.expiresAt)
      qc.invalidateQueries({ queryKey: ['family-portal-users', clientId] })
    },
    onError: () => {
      setInviteError(t('inviteError'))
    },
  })

  const removeMutation = useMutation({
    mutationFn: (fpuId: string) => removeFamilyPortalUser(clientId, fpuId),
    onSuccess: () => {
      setRemoveError(null)
      setConfirmRemove(null)
      qc.invalidateQueries({ queryKey: ['family-portal-users', clientId] })
    },
    onError: () => {
      setRemoveError(t('removeError'))
    },
  })

  function openInviteForm(prefill?: string) {
    setEmail(prefill ?? '')
    setPrefilledEmail(prefill ?? null)
    setInviteUrl(null)
    setExpiresAt(null)
    setCopied(false)
    setInviteError(null)
    setFormOpen(true)
  }

  function handleGenerate() {
    inviteMutation.mutate({ email })
  }

  function handleCopy() {
    if (inviteUrl) {
      navigator.clipboard.writeText(inviteUrl).then(() => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      }).catch(() => {
        // Clipboard API unavailable (non-HTTPS or permission denied) —
        // URL is visible in the box above for manual copy.
        console.error('Copy failed: clipboard API unavailable')
      })
    }
  }

  function formatExpiry(iso: string): { date: string; time: string } {
    const d = new Date(iso)
    return {
      date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      time: d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }),
    }
  }

  const users = data?.content ?? []

  return (
    <div className="bg-surface p-3">
      {/* Section header */}
      <div className="flex justify-between items-center mb-3">
        <span className="text-[9px] font-bold tracking-[.1em] uppercase text-text-secondary">
          {t('portalAccessHeading')}
        </span>
        <button
          onClick={() => openInviteForm()}
          className="bg-dark text-white text-[11px] font-bold px-3 py-1.5 min-h-[44px] flex items-center justify-center"
        >
          {t('addInvite')}
        </button>
      </div>

      {/* Invite form */}
      {formOpen && (
        <div className="border border-blue bg-white p-3 mb-3">
          {isReInvite && (
            <p className="text-[12px] text-text-secondary mb-2">{t('reInviteNote')}</p>
          )}
          {!inviteUrl ? (
            <>
              <div className="flex gap-2 items-end">
                <div className="flex-1">
                  <label className="text-[9px] font-bold tracking-[.08em] uppercase text-text-secondary block mb-1">
                    {t('inviteEmail')}
                  </label>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="email@example.com"
                    className="border border-border px-2.5 py-1.5 text-[12px] text-text-primary w-full"
                  />
                </div>
                <button
                  onClick={handleGenerate}
                  disabled={!email || inviteMutation.isPending}
                  className="bg-dark text-white text-[11px] font-bold px-3 py-1.5 min-h-[44px] flex items-center justify-center"
                >
                  {t('generateLink')}
                </button>
                <button
                  onClick={() => { setFormOpen(false); setPrefilledEmail(null); setInviteError(null) }}
                  className="border border-border text-text-secondary text-[11px] px-3 py-1.5 bg-transparent min-h-[44px] flex items-center justify-center"
                >
                  {t('cancel')}
                </button>
              </div>
              {inviteError && (
                <p className="text-[12px] text-red-600 mt-1">{inviteError}</p>
              )}
            </>
          ) : (
            <div>
              <div className="bg-surface border border-border p-2 font-mono text-[11px] text-text-primary break-all mb-2">
                {inviteUrl}
              </div>
              <div className="flex items-center gap-2 mb-2">
                <button
                  onClick={handleCopy}
                  className="border border-blue text-blue text-[11px] px-3 py-1.5 min-h-[44px] flex items-center justify-center"
                >
                  {copied ? t('linkCopied') : t('copyLink')}
                </button>
                <button
                  onClick={() => { setFormOpen(false); setPrefilledEmail(null); setInviteError(null) }}
                  className="border border-border text-text-secondary text-[11px] px-3 py-1.5 min-h-[44px] flex items-center justify-center"
                >
                  {t('done')}
                </button>
              </div>
              {expiresAt && (() => {
                const { date, time } = formatExpiry(expiresAt)
                return (
                  <p className="text-[12px] text-text-secondary">
                    {t('inviteExpiry', { date, time })}
                  </p>
                )
              })()}
            </div>
          )}
        </div>
      )}

      {/* User list */}
      {usersLoading && (
        <div role="status" aria-live="polite" className="text-[13px] text-text-secondary py-2">
          {t('loading')}
        </div>
      )}
      {usersError && !usersLoading && (
        <p className="text-[12px] text-red-600 py-2">{t('loadError')}</p>
      )}
      {!usersLoading && !usersError && users.length === 0 && !formOpen && (
        <p className="text-[13px] text-text-secondary">{t('noPortalUsers')}</p>
      )}
      <div className="flex flex-col gap-px">
        {users.map((fpu) => (
          <div key={fpu.id}>
            <div className="bg-white border border-border p-3 flex justify-between items-start">
              <div>
                <div className="text-[13px] font-semibold text-text-primary">{fpu.name ?? fpu.email}</div>
                <div className="text-[12px] text-text-secondary">{fpu.email}</div>
                <div className="text-[11px] text-text-muted mt-0.5">
                  {fpu.lastLoginAt
                    ? `${t('lastLogin')} ${new Date(fpu.lastLoginAt).toLocaleDateString()}`
                    : t('neverLoggedIn')}
                </div>
              </div>
              <div className="flex gap-2 flex-shrink-0">
                <button
                  onClick={() => openInviteForm(fpu.email)}
                  className="text-[11px] text-text-secondary border border-border px-2 py-1 min-h-[44px] flex items-center justify-center"
                >
                  {t('sendNewLink')}
                </button>
                <button
                  onClick={() => setConfirmRemove(fpu)}
                  className="text-[11px] text-text-secondary border border-border px-2 py-1 min-h-[44px] flex items-center justify-center"
                >
                  {t('remove')}
                </button>
              </div>
            </div>
            {/* Inline remove confirmation */}
            {confirmRemove?.id === fpu.id && (
              <div className="bg-white border border-border border-t-0 px-3 pb-3">
                <p className="text-[12px] text-text-primary mb-2">
                  {t('removeConfirmation', { name: fpu.name ?? fpu.email })}
                </p>
                <div className="flex gap-2">
                  <button
                    data-confirm
                    onClick={() => removeMutation.mutate(fpu.id)}
                    disabled={removeMutation.isPending}
                    className="bg-dark text-white text-[11px] font-bold px-3 py-1.5 min-h-[44px] flex items-center justify-center disabled:opacity-50"
                  >
                    {removeMutation.isPending ? t('removing') : t('removeConfirm')}
                  </button>
                  <button
                    onClick={() => setConfirmRemove(null)}
                    className="border border-border text-text-secondary text-[11px] px-3 py-1.5 min-h-[44px] flex items-center justify-center"
                  >
                    {t('cancel')}
                  </button>
                </div>
                {removeError && confirmRemove?.id === fpu.id && (
                  <p className="text-[12px] text-red-600 mt-1">{removeError}</p>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
