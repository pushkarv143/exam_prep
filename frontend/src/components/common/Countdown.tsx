import { useEffect, useState } from 'react'

/** Milliseconds until `target`, updating every second. 0 once the target has passed. */
export function useCountdown(target: number | null | undefined): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (target == null) return
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [target])
  if (target == null) return 0
  return Math.max(0, target - now)
}

/** "2d 4h", "3h 12m", "5m 09s". Compact, calm, no ticking seconds unless under a minute. */
export function formatCountdown(ms: number): string {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${String(sec).padStart(2, '0')}s`
  return `${sec}s`
}

/** A live, screen-reader-friendly countdown label. */
export function Countdown({ target, className, prefix }:
  { target: number | null | undefined; className?: string; prefix?: string }) {
  const ms = useCountdown(target)
  if (target == null) return null
  return (
    <span className={className} role="timer" aria-live="off">
      {prefix}{ms <= 0 ? 'now' : formatCountdown(ms)}
    </span>
  )
}
