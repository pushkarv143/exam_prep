import type { TrendPoint } from '@/types/domain'

/** Local calendar day key (IST-agnostic; uses the device's local day, which is fine for streaks). */
function dayKey(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

/**
 * Current test-taking streak: consecutive days (ending today or yesterday) on which the student
 * evaluated at least one attempt. Derived from real attempt dates in the analytics trend.
 */
export function computeStreak(trend: TrendPoint[]): number {
  const days = new Set(trend.map((t) => dayKey(t.evaluatedAt)))
  if (days.size === 0) return 0

  const oneDay = 86_400_000
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  // The streak may end today or yesterday (a student who hasn't tested yet today still has a streak).
  let cursor = today.getTime()
  if (!days.has(dayKey(new Date(cursor).toISOString()))) {
    cursor -= oneDay
    if (!days.has(dayKey(new Date(cursor).toISOString()))) return 0
  }

  let streak = 0
  while (days.has(dayKey(new Date(cursor).toISOString()))) {
    streak += 1
    cursor -= oneDay
  }
  return streak
}
