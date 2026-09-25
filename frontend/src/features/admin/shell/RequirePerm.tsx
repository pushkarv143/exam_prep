import type { ReactNode } from 'react'
import { usePermissions } from '@/api/portal'
import { PageLoader } from '@/components/common/States'
import { ForbiddenPage } from '@/features/errors/ErrorPages'

/** Renders children only for users holding ANY of the permissions (UX only; the API re-checks). */
export function RequirePerm({ any: required, children }: { any: string[]; children: ReactNode }) {
  const { loading, any } = usePermissions()
  if (loading) {
    return <PageLoader />
  }
  return any(...required) ? <>{children}</> : <ForbiddenPage />
}
