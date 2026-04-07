import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SlidePanel } from './SlidePanel'

describe('SlidePanel', () => {
  it('renders children when open', () => {
    render(
      <SlidePanel isOpen onClose={() => {}}>
        <div>Panel content</div>
      </SlidePanel>
    )
    expect(screen.getByText('Panel content')).toBeInTheDocument()
  })

  it('is translated off-screen when closed', () => {
    const { container } = render(
      <SlidePanel isOpen={false} onClose={() => {}}>
        <div>Panel content</div>
      </SlidePanel>
    )
    const panel = container.firstChild as HTMLElement
    expect(panel.style.transform).toBe('translateX(100%)')
  })

  it('is at translateX(0) when open', () => {
    const { container } = render(
      <SlidePanel isOpen onClose={() => {}}>
        <div>Panel content</div>
      </SlidePanel>
    )
    const panel = container.firstChild as HTMLElement
    expect(panel.style.transform).toBe('translateX(0)')
  })

  it('calls onClose when Escape is pressed', async () => {
    const onClose = vi.fn()
    render(
      <SlidePanel isOpen onClose={onClose}>
        <div>Panel content</div>
      </SlidePanel>
    )
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('does not call onClose on Escape when closed', async () => {
    const onClose = vi.fn()
    render(
      <SlidePanel isOpen={false} onClose={onClose}>
        <div>Panel</div>
      </SlidePanel>
    )
    await userEvent.keyboard('{Escape}')
    expect(onClose).not.toHaveBeenCalled()
  })
})
