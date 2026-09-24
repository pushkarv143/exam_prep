import type { ReactNode } from 'react'
import { AlertTriangle, Inbox, Loader2, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { errorMessage, toApiError } from '@/lib/errors'
import { cn } from '@/lib/utils'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('text-primary size-6 animate-spin', className)} aria-label="Loading" />
}

/** Full-area loader for route-level suspense and initial loads. */
export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3" role="status">
      <Spinner />
      <span className="text-muted-foreground text-sm">{label}</span>
    </div>
  )
}

/** In-place error for failed queries, with a retry button. */
export function ErrorState({ error, onRetry, title = 'Something went wrong' }:
                             { error: unknown; onRetry?: () => void; title?: string }) {
  const err = toApiError(error)
  return (
    <div className="flex min-h-[30vh] flex-col items-center justify-center gap-3 p-6 text-center" role="alert">
      <AlertTriangle className="text-destructive size-8" />
      <div>
        <p className="font-medium">{err.status === 404 ? 'Not found' : title}</p>
        <p className="text-muted-foreground mt-1 text-sm">{errorMessage(error)}</p>
        {err.requestId && <p className="text-muted-foreground mt-1 text-xs">Reference: {err.requestId}</p>}
      </div>
      {onRetry && err.status !== 404 && (
        <Button variant="outline" size="sm" onClick={onRetry}><RefreshCw /> Try again</Button>
      )}
    </div>
  )
}

export function EmptyState({ title, description, action, icon }:
                             { title: string; description?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed p-10 text-center">
      {icon ?? <Inbox className="text-muted-foreground size-8" />}
      <div>
        <p className="font-medium">{title}</p>
        {description && <p className="text-muted-foreground mt-1 text-sm">{description}</p>}
      </div>
      {action}
    </div>
  )
}
