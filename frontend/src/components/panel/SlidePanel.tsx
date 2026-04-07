import { useEffect } from 'react'

interface SlidePanelProps {
  isOpen: boolean
  onClose: () => void
  children: React.ReactNode
}

export function SlidePanel({ isOpen, onClose, children }: SlidePanelProps) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) onClose()
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [isOpen, onClose])

  return (
    <div
      className="absolute inset-0 z-50 bg-white overflow-y-auto"
      style={{
        transform: isOpen ? 'translateX(0)' : 'translateX(100%)',
        transition: 'transform 280ms cubic-bezier(0.4, 0, 0.2, 1)',
      }}
    >
      {isOpen && children}
    </div>
  )
}
