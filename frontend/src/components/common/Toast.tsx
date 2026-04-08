import { useEffect } from 'react'
import { useToastStore } from '../../store/toastStore'
import { usePanelStore } from '../../store/panelStore'

export function Toast() {
  const { visible, showCount, message, linkLabel, targetId, panelType, panelTab, backLabel, dismiss } =
    useToastStore()
  const { openPanel } = usePanelStore()

  useEffect(() => {
    if (!visible) return
    const id = setTimeout(() => dismiss(), 6000)
    return () => clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, showCount])

  if (!visible) return null

  function handleLinkClick() {
    dismiss()
    openPanel(panelType as Parameters<typeof openPanel>[0], targetId ?? undefined, {
      initialTab: panelTab,
      backLabel,
    })
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-4 right-4 z-50 flex items-center gap-3 px-4 py-3 bg-dark text-white text-[13px] shadow-lg max-w-sm"
    >
      <span>{message}</span>
      {linkLabel && targetId && (
        <button
          type="button"
          onClick={handleLinkClick}
          className="underline text-blue whitespace-nowrap hover:no-underline"
        >
          {linkLabel}
        </button>
      )}
      <button
        type="button"
        aria-label="Dismiss"
        onClick={dismiss}
        className="ml-1 text-text-muted hover:text-white text-[16px] leading-none"
      >
        ×
      </button>
    </div>
  )
}
