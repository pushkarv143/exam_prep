import { useCallback, useEffect, useRef, useState } from 'react'
import { attemptsApi } from '@/api/attempts'
import type { AttemptEventType } from '@/types/exam'
import { useExamStore } from './examStore'

interface QueuedEvent {
  type: AttemptEventType
  clientTs: string
  details?: Record<string, string>
}

/** Why the student is away from the test right now. */
export type LeaveCause = 'hidden' | 'fullscreen' | 'blur'

/** Whether this browser can put the page in full screen (false on iPhone Safari, for example). */
export const fullscreenSupported = () => typeof document !== 'undefined' && !!document.fullscreenEnabled

/**
 * Leaves allowed before the server auto-submits: it submits once the count exceeds
 * `maxTabSwitches`, so the limit is the (max + 1)-th leave. 0 = no limit.
 */
export function leaveLimit(maxTabSwitches: number): number {
  return maxTabSwitches > 0 ? maxTabSwitches + 1 : 0
}

/**
 * Proctoring. A browser cannot stop a student from pressing Esc, switching apps or minimising, so the
 * test is *locked* instead: the moment the student leaves (tab hidden, window blurred, full screen
 * exited) the page reports `away` and the exam screen hides the questions until they come back.
 *
 * One "leave" is counted per away episode, however many signals it fires (minimising a full-screen
 * window fires blur, fullscreenchange and visibilitychange). It is reported as a TAB_SWITCH, which the
 * server counts and auto-submits past `app.attempt.max-tab-switches`. The raw FULLSCREEN_EXIT,
 * WINDOW_BLUR, COPY, ... events are still sent as evidence for review.
 */
export function useAntiCheat(attemptId: string, enabled: boolean, onAutoSubmitted: () => void,
                             isFinishing: () => boolean) {
  const queue = useRef<QueuedEvent[]>([])
  const awayRef = useRef<LeaveCause | null>(null)
  const leavesRef = useRef(0)
  const [away, setAway] = useState<LeaveCause | null>(null)
  const [leaves, setLeaves] = useState(0)
  const maxTabSwitches = useExamStore((s) => s.antiCheat.maxTabSwitches)

  const send = useCallback(async () => {
    if (queue.current.length === 0) return
    const batch = queue.current.splice(0, 50)
    try {
      const res = await attemptsApi.events(attemptId, batch)
      useExamStore.setState({ antiCheat: res })
      if (res.tabSwitchCount > leavesRef.current) {
        leavesRef.current = res.tabSwitchCount
        setLeaves(res.tabSwitchCount)
      }
      if (res.autoSubmitted) onAutoSubmitted()
    } catch {
      queue.current.unshift(...batch)   // retried on the next tick
    }
  }, [attemptId, onAutoSubmitted])

  const record = useCallback((type: AttemptEventType, details?: Record<string, string>, urgent = false) => {
    queue.current.push({ type, clientTs: new Date().toISOString(), details })
    if (urgent) void send()
  }, [send])

  /** Back only when the tab is visible, focused and (where supported) in full screen. */
  const checkBack = useCallback(() => {
    if (!awayRef.current) return
    const visible = document.visibilityState === 'visible'
    const inFullscreen = !fullscreenSupported() || !!document.fullscreenElement
    if (visible && document.hasFocus() && inFullscreen) {
      awayRef.current = null
      setAway(null)
    }
  }, [])

  const leave = useCallback((cause: LeaveCause) => {
    if (isFinishing()) return
    if (awayRef.current) return          // same episode: count it once
    awayRef.current = cause
    setAway(cause)
    leavesRef.current += 1
    setLeaves(leavesRef.current)
    record('TAB_SWITCH', { cause }, true)
  }, [isFinishing, record])

  useEffect(() => {
    if (!enabled) return
    const initial = useExamStore.getState().antiCheat.tabSwitchCount
    if (initial > leavesRef.current) {
      leavesRef.current = initial
      setLeaves(initial)
    }

    const onVisibility = () => {
      if (document.visibilityState === 'hidden') leave('hidden')
      else checkBack()
    }
    const onBlur = () => {
      record('WINDOW_BLUR')
      leave('blur')
    }
    const onFocus = () => checkBack()
    const onFullscreen = () => {
      if (document.fullscreenElement) {
        record('FULLSCREEN_ENTER')
        checkBack()
      } else {
        record('FULLSCREEN_EXIT')
        leave('fullscreen')
      }
    }
    const block = (type: AttemptEventType) => (e: Event) => {
      e.preventDefault()
      record(type)
    }
    const onCopy = block('COPY')
    const onPaste = block('PASTE')
    const onContext = block('CONTEXT_MENU')
    const onOffline = () => record('NETWORK_OFFLINE')
    const onOnline = () => record('NETWORK_ONLINE', undefined, true)

    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onBlur)
    window.addEventListener('focus', onFocus)
    document.addEventListener('fullscreenchange', onFullscreen)
    document.addEventListener('copy', onCopy)
    document.addEventListener('paste', onPaste)
    document.addEventListener('contextmenu', onContext)
    window.addEventListener('offline', onOffline)
    window.addEventListener('online', onOnline)
    const timer = setInterval(() => void send(), 5000)
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('blur', onBlur)
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('fullscreenchange', onFullscreen)
      document.removeEventListener('copy', onCopy)
      document.removeEventListener('paste', onPaste)
      document.removeEventListener('contextmenu', onContext)
      window.removeEventListener('offline', onOffline)
      window.removeEventListener('online', onOnline)
      clearInterval(timer)
    }
  }, [enabled, record, send, leave, checkBack])

  return { away, leaves, limit: leaveLimit(maxTabSwitches), checkBack }
}
