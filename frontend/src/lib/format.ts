/** Display formatting. All dates are shown in IST, since the exams and the audience are Indian. */

const IST = 'Asia/Kolkata'

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

export function formatPrice(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined) return ''
  const n = typeof amount === 'string' ? Number(amount) : amount
  return Number.isInteger(n) ? inrWhole.format(n) : inr.format(n)
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return ''
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: IST,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(iso))
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  return new Intl.DateTimeFormat('en-IN', { timeZone: IST, day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(iso))
}

/** 180 → "3 h", 200 → "3 h 20 min", 45 → "45 min". */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} min`
  return m === 0 ? `${h} h` : `${h} h ${m} min`
}

/** Seconds → "1:05:09" or "5:09". */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = String(s % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`
}

export function formatNumber(value: number | string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') return '–'
  const n = typeof value === 'string' ? Number(value) : value
  return new Intl.NumberFormat('en-IN', { maximumFractionDigits: digits }).format(n)
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?'
  // "Sana (Support Agent)" → "S": ignore bracketed notes and words that don't start with a letter
  const parts = name.replace(/\([^)]*\)?/g, ' ').trim().split(/\s+/).filter((p) => /^\p{L}/u.test(p))
  if (parts.length === 0) return '?'
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

/** plural(1, 'test') → "1 test", plural(3, 'test') → "3 tests". */
export function plural(n: number, word: string, pluralWord = word + 's'): string {
  return `${n} ${n === 1 ? word : pluralWord}`
}

/** Coarse relative time for SLA timers and timelines: "in 5 h", "2 d ago", "just now". */
export function formatRelative(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return ''
  const diff = new Date(iso).getTime() - now
  const minutes = Math.round(Math.abs(diff) / 60_000)
  if (minutes < 1) return 'just now'
  const text = minutes < 60 ? `${minutes} min` : minutes < 48 * 60 ? `${Math.round(minutes / 60)} h` : `${Math.round(minutes / 1440)} d`
  return diff >= 0 ? `in ${text}` : `${text} ago`
}
