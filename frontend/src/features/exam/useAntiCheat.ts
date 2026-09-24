import { useCallback, useEffect, useRef, useState } from 'react'
import { attemptsApi } from '@/api/attempts'
import type { AttemptEventType } from '@/types/exam'
import { useExamStore } from './examStore'

interface QueuedEvent {
  type: AttemptEventType
  clientTs: string
  details?: Record<string, string>
}

/**
 * Proctoring signals: tab switches, focus loss, fullscreen exit, copy/paste/right-click
 * (copy/paste/context menu are also blocked). Events are batched to the server, which keeps
 * the authoritative counters and may auto-submit past the configured tab-switch limit.
 * These are deterrents and evidence for review, not proof: browsers cannot fully prevent cheating.
 */
export function useAntiCheat(attemptId: string, enabled: boolean, onAutoSubmitted: () => void) {
  const queue = useRef<QueuedEvent[]>([])
  const [warning, setWarning] = useState<string | null>(null)

  const send = useCallback(async () => {
    if (queue.current.length === 0) return
    const batch = queue.current.splice(0, 50)
    try {
      const res = await attemptsApi.events(attemptId, batch)
      useExamStore.setState({ antiCheat: res })
      if (res.autoSubmitted) onAutoSubmitted()
    } catch {
      queue.current.unshift(...batch)   // retried on the next tick
    }
  }, [attemptId, onAutoSubmitted])

  const record = useCallback((type: AttemptEventType, details?: Record<string, string>, urgent = false) => {
    queue.current.push({ type, clientTs: new Date().toISOString(), details })
    if (urgent) void send()
  }, [send])

  useEffect(() => {
    if (!enabled) return
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        record('TAB_SWITCH', undefined, true)
        const ac = useExamStore.getState().antiCheat
        const next = ac.tabSwitchCount + 1
        setWarning(ac.maxTabSwitches > 0
          ? `You switched away from the test (${next}/${ac.maxTabSwitches}). The test is submitted automatically after ${ac.maxTabSwitches} switches.`
          : `You switched away from the test ${next} time(s). This is recorded.`)
      }
    }
    const onBlur = () => record('WINDOW_BLUR')
    const onFullscreen = () => record(document.fullscreenElement ? 'FULLSCREEN_ENTER' : 'FULLSCREEN_EXIT', undefined,
      !document.fullscreenElement)
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
      document.removeEventListener('fullscreenchange', onFullscreen)
      document.removeEventListener('copy', onCopy)
      document.removeEventListener('paste', onPaste)
      document.removeEventListener('contextmenu', onContext)
      window.removeEventListener('offline', onOffline)
      window.removeEventListener('online', onOnline)
      clearInterval(timer)
    }
  }, [enabled, record, send])

  return { warning, dismissWarning: () => setWarning(null) }
}
