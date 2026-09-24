import { useCallback, useEffect, useRef } from 'react'
import { toast } from 'sonner'
import { attemptsApi, autosaveKeepalive } from '@/api/attempts'
import { toApiError } from '@/lib/errors'
import { applySaveResult, syncClock, takeChanges, useExamStore } from './examStore'

const INTERVAL_MS = 12_000
const HEARTBEAT_MS = 60_000
const MAX_BACKOFF_MS = 30_000

/**
 * Keeps the server in sync with local answers.
 *  - Flushes every 12 s, and immediately on demand (navigation, Save & Next).
 *  - Single-flight: at most one request in flight. Changes made meanwhile go in the next batch.
 *  - Offline / 5xx: exponential backoff up to 30 s. Changes stay in memory and in the
 *    localStorage backup, which is also resent after a reload.
 *  - An empty batch every 60 s acts as a heartbeat that re-syncs the server clock.
 *  - When the page closes, one last keepalive request carries any pending changes.
 *  - 409 ATTEMPT_EXPIRED / ATTEMPT_NOT_IN_PROGRESS → onFinished (go to the result page).
 */
export function useAutosave(attemptId: string, onFinished: () => void) {
  const inFlight = useRef<Promise<void> | null>(null)
  const backoff = useRef(0)
  const nextAllowed = useRef(0)
  const lastRequest = useRef(Date.now())
  const finished = useRef(false)

  const flush = useCallback((force = false): Promise<void> => {
    if (finished.current) return Promise.resolve()
    if (inFlight.current) return inFlight.current
    if (!force && Date.now() < nextAllowed.current) return Promise.resolve()

    const run = async () => {
      const { changes, sentVersions } = takeChanges()
      const heartbeat = Date.now() - lastRequest.current > HEARTBEAT_MS
      if (changes.length === 0 && !heartbeat && !force) return
      useExamStore.setState({ saveState: 'saving' })
      lastRequest.current = Date.now()
      try {
        const res = await attemptsApi.autosave(attemptId, changes)
        applySaveResult(changes, sentVersions, res.rejected)
        syncClock(res.serverNow, res.remainingSeconds)
        res.rejected.forEach((r) => toast.error(r.reason))
        backoff.current = 0
        nextAllowed.current = 0
      } catch (e) {
        const err = toApiError(e)
        if (err.code === 'ATTEMPT_EXPIRED' || err.code === 'ATTEMPT_NOT_IN_PROGRESS') {
          finished.current = true
          onFinished()
          return
        }
        backoff.current = Math.min(MAX_BACKOFF_MS, backoff.current ? backoff.current * 2 : 2000)
        nextAllowed.current = Date.now() + backoff.current
        useExamStore.setState({ saveState: err.isNetwork ? 'offline' : 'error' })
      }
    }
    inFlight.current = run().finally(() => {
      inFlight.current = null
    })
    return inFlight.current
  }, [attemptId, onFinished])

  useEffect(() => {
    const timer = setInterval(() => void flush(), INTERVAL_MS)
    // A quick retry loop while offline or backing off.
    const retry = setInterval(() => {
      const s = useExamStore.getState().saveState
      if ((s === 'offline' || s === 'error') && Date.now() >= nextAllowed.current) void flush()
    }, 2000)
    const online = () => void flush(true)
    const hide = () => {
      if (document.visibilityState === 'hidden') void flush(true)
    }
    const pagehide = () => {
      const { changes } = takeChanges()
      autosaveKeepalive(attemptId, changes)
    }
    window.addEventListener('online', online)
    document.addEventListener('visibilitychange', hide)
    window.addEventListener('pagehide', pagehide)
    return () => {
      clearInterval(timer)
      clearInterval(retry)
      window.removeEventListener('online', online)
      document.removeEventListener('visibilitychange', hide)
      window.removeEventListener('pagehide', pagehide)
    }
  }, [attemptId, flush])

  const markFinished = useCallback(() => {
    finished.current = true
  }, [])

  return { flush, markFinished }
}
