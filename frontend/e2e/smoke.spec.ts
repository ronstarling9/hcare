import { test, expect } from '@playwright/test'

test('redirects / to /schedule', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL('/schedule')
})

test('renders sidebar with h.care wordmark', async ({ page }) => {
  await page.goto('/schedule')
  // Sidebar renders: <span>h</span><span>.</span><span>care</span>
  await expect(page.locator('aside span').filter({ hasText: /^care$/ }).first()).toBeVisible()
})

test('schedule page shows week calendar', async ({ page }) => {
  await page.goto('/schedule')
  await expect(page.getByRole('heading', { name: 'Schedule' })).toBeVisible()
  const today = new Date().getDate().toString()
  await expect(page.getByText(today, { exact: true }).first()).toBeVisible()
})

test('clicking shift block opens shift detail panel', async ({ page }) => {
  await page.goto('/schedule')
  // ShiftBlock renders a <button> with clientName as text; Alice Johnson is on shift1.
  // The calendar overlays transparent empty-slot buttons at z-order above shift blocks,
  // so we use force:true to dispatch the click directly to the ShiftBlock button.
  await page.locator('button').filter({ hasText: 'Alice Johnson' }).first().click({ force: true })
  // ShiftDetailPanel renders the backLabel as a button with text "← Schedule"
  await expect(page.getByRole('button', { name: '← Schedule' })).toBeVisible()
})

test('New Shift button opens new shift panel', async ({ page }) => {
  await page.goto('/schedule')
  await page.getByRole('button', { name: '+ New Shift' }).click()
  // NewShiftPanel renders <h2> with panelTitle = "New Shift"
  await expect(page.getByRole('heading', { name: 'New Shift' })).toBeVisible()
})

test('Escape closes the panel', async ({ page }) => {
  await page.goto('/schedule')
  await page.getByRole('button', { name: '+ New Shift' }).click()
  // Confirm panel is open by checking the heading is in view
  await expect(page.getByRole('heading', { name: 'New Shift' })).toBeVisible()
  await page.keyboard.press('Escape')
  // SlidePanel uses CSS transform translateX(100%) to hide — the panel container
  // slides off-screen. Check the SlidePanel wrapper's transform style directly.
  const panel = page.locator('.absolute.inset-0.z-50.bg-white')
  await expect(panel).toHaveCSS('transform', /matrix/)
  // After Escape the backLabel button should no longer be visible in viewport
  await expect(page.getByRole('button', { name: '← Schedule' })).not.toBeInViewport()
})

test('navigating to /dashboard shows stat tiles', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page.getByText('RED EVV')).toBeVisible()
  await expect(page.getByText('ON TRACK')).toBeVisible()
})

test('navigating to /clients shows client table', async ({ page }) => {
  await page.goto('/clients')
  await expect(page.getByText('Alice Johnson')).toBeVisible()
})

test('navigating to /caregivers shows caregiver table', async ({ page }) => {
  await page.goto('/caregivers')
  await expect(page.getByText('Maria Garcia')).toBeVisible()
})
