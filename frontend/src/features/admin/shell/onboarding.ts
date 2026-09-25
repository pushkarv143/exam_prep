import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'

const VISITS_KEY = (userId: string) => `examprep-onboarding:${userId}`

/** Remembers which admin pages this user has opened (per browser), for the onboarding checklist. */
export function useVisitTracker() {
  const userId = useAuthStore((s) => s.user?.id)
  const { pathname } = useLocation()
  useEffect(() => {
    if (!userId || !pathname.startsWith('/admin')) return
    try {
      const key = VISITS_KEY(userId)
      const set = new Set<string>(JSON.parse(localStorage.getItem(key) ?? '[]'))
      const base = '/' + pathname.split('/').slice(1, 3).join('/')
      if (!set.has(base)) {
        set.add(base)
        localStorage.setItem(key, JSON.stringify([...set]))
      }
    } catch {
      // storage unavailable: the checklist just does not remember
    }
  }, [userId, pathname])
}

export function markVisited(userId: string | undefined, id: string) {
  if (!userId) return
  try {
    const key = VISITS_KEY(userId)
    const set = new Set<string>(JSON.parse(localStorage.getItem(key) ?? '[]'))
    set.add(id)
    localStorage.setItem(key, JSON.stringify([...set]))
  } catch {
    // ignore
  }
}

export function readVisited(userId: string | undefined): Set<string> {
  try {
    return new Set<string>(JSON.parse(localStorage.getItem(VISITS_KEY(userId ?? '')) ?? '[]'))
  } catch {
    return new Set()
  }
}
