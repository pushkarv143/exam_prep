import { useEffect, useRef, useState } from 'react'

/** True when the user asked the OS to reduce motion. Animations fall back to an instant set. */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(
    () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
  )
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    if (!mq) return
    const on = () => setReduced(mq.matches)
    mq.addEventListener?.('change', on)
    return () => mq.removeEventListener?.('change', on)
  }, [])
  return !!reduced
}

/**
 * Animates a number from 0 to `target` with an ease-out curve over `durationMs`.
 * Respects prefers-reduced-motion (snaps straight to the value). Used for the calm
 * score/rank/percentile reveal so numbers "count up" instead of popping in.
 */
export function useCountUp(target: number, durationMs = 900, startDelayMs = 0): number {
  const reduced = usePrefersReducedMotion()
  const [value, setValue] = useState(reduced ? target : 0)
  const frame = useRef<number>(0)

  useEffect(() => {
    if (reduced || !Number.isFinite(target)) {
      setValue(target)
      return
    }
    let start = 0
    const tick = (t: number) => {
      if (!start) start = t + startDelayMs
      const elapsed = t - start
      if (elapsed < 0) {
        frame.current = requestAnimationFrame(tick)
        return
      }
      const p = Math.min(1, elapsed / durationMs)
      const eased = 1 - Math.pow(1 - p, 3)
      setValue(target * eased)
      if (p < 1) frame.current = requestAnimationFrame(tick)
      else setValue(target)
    }
    frame.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame.current)
  }, [target, durationMs, startDelayMs, reduced])

  return value
}
