import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ForbiddenPage } from '@/features/errors/ErrorPages'
import { hasRole, isStaff, useAuthStore } from '@/store/auth'
import type { Role } from '@/types/domain'

/**
 * Route guard. Anonymous users are sent to /login with `next` set, so they come back
 * afterwards. Users without the role see a 403 page.
 *
 * The UI check is for UX only. The API enforces every rule again server-side.
 */
export function RequireAuth({ roles, staff, children }: { roles?: Role[]; staff?: boolean; children?: ReactNode }) {
  const token = useAuthStore((s) => s.accessToken)
  const user = useAuthStore((s) => s.user)
  const location = useLocation()

  if (!token) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if ((roles && !hasRole(user, ...roles)) || (staff && !isStaff(user))) {
    return <ForbiddenPage />
  }
  return children ?? <Outlet />
}

/** For login/register pages: already signed-in users go straight to their dashboard. */
export function GuestOnly({ children }: { children?: ReactNode }) {
  const token = useAuthStore((s) => s.accessToken)
  if (token) {
    return <Navigate to="/dashboard" replace />
  }
  return children ?? <Outlet />
}

/** Only follow same-site relative redirects (prevents open-redirect abuse via ?next=). */
export function safeNext(next: string | null, fallback = '/dashboard'): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : fallback
}
