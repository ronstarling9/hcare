/**
 * Formats a date-only ISO string (e.g. "2026-04-10") for display.
 * Uses a T12:00:00 noon-anchor to avoid UTC-offset off-by-one errors.
 * @param dateStr  - ISO date string "YYYY-MM-DD"
 * @param locale   - BCP 47 locale tag (pass i18n.language from useTranslation)
 * @param options  - Optional Intl.DateTimeFormatOptions override; defaults to
 *                   { month: 'short', day: 'numeric', year: 'numeric' }
 */
export function formatLocalDate(
  dateStr: string,
  locale: string,
  options: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric', year: 'numeric' },
): string {
  return new Date(`${dateStr}T12:00:00`).toLocaleDateString(locale, options)
}
