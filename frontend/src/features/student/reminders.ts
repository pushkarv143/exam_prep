import { useCallback, useState } from 'react'

/**
 * Local reminder toggles for live tests. These are device-local flags only — the app has no
 * push/email reminder endpoint yet, so toggling stores intent and (where supported) schedules a
 * best-effort in-tab notification. Wire to a backend reminders endpoint when available.
 */
const KEY = 'examprep-reminders'

function read(): Record<string, boolean> {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, boolean>
  } catch {
    return {}
  }
}

export function useReminders() {
  const [map, setMap] = useState<Record<string, boolean>>(read)

  const toggle = useCallback((testId: string) => {
    setMap((prev) => {
      const next = { ...prev, [testId]: !prev[testId] }
      if (!next[testId]) delete next[testId]
      try {
        localStorage.setItem(KEY, JSON.stringify(next))
      } catch {
        // ignore
      }
      // Best-effort: ask for notification permission when the student opts in.
      if (next[testId] && 'Notification' in window && Notification.permission === 'default') {
        void Notification.requestPermission()
      }
      return next
    })
  }, [])

  return { isOn: (id: string) => !!map[id], toggle }
}
