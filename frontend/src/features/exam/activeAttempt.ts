/**
 * A tiny, device-local record of the attempt the student is currently taking, written when the
 * exam loads and cleared on submit. It powers the dashboard "Continue where you left off" card.
 *
 * This is a frontend-only convenience: it does not sync across devices. A proper cross-device
 * "resume" needs a backend endpoint listing in-progress attempts (see backend gaps).
 */
export interface ActiveAttempt {
  attemptId: string
  testId: string
  title: string
  deadlineMs: number
  savedAt: number
}

const KEY = 'examprep-active-attempt'

export function saveActiveAttempt(a: ActiveAttempt) {
  try {
    localStorage.setItem(KEY, JSON.stringify(a))
  } catch {
    // storage unavailable — the card simply won't show
  }
}

export function readActiveAttempt(): ActiveAttempt | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const a = JSON.parse(raw) as ActiveAttempt
    // Drop it once the test window is well past (deadline + 2 min grace).
    if (!a.attemptId || a.deadlineMs + 120_000 < Date.now()) {
      localStorage.removeItem(KEY)
      return null
    }
    return a
  } catch {
    return null
  }
}

export function clearActiveAttempt(attemptId?: string) {
  try {
    if (attemptId) {
      const cur = readActiveAttempt()
      if (cur && cur.attemptId !== attemptId) return
    }
    localStorage.removeItem(KEY)
  } catch {
    // ignore
  }
}
