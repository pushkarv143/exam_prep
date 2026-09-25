import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * A subtle, calm route transition: content fades and rises a few pixels on navigation.
 * Uses tw-animate-css utilities gated behind `motion-safe:` so users who prefer reduced
 * motion (and low-power devices honouring that setting) see an instant change — no gimmicks,
 * no extra JavaScript animation runtime.
 */
export function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  return (
    <div key={pathname}
         className="motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-1 motion-safe:duration-300">
      {children}
    </div>
  )
}
