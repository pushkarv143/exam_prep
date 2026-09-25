import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Download, Loader2, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { downloadBlob } from '@/api/admin'
import { portalApi, portalKeys } from '@/api/portal'
import type { Job } from '@/api/types'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/errors'
import { cn } from '@/lib/utils'

const FINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED', 'DEAD'])

export function isFinal(job: Pick<Job, 'status'>) {
  return FINAL.has(job.status)
}

export async function downloadJobArtifact(jobId: string) {
  try {
    const { blob, filename } = await portalApi.downloadArtifact(jobId)
    downloadBlob(blob, filename)
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

/** Polls a job every second until it finishes, then offers its file. */
export function JobProgress({ jobId, className }: { jobId: string; className?: string }) {
  const job = useQuery({
    queryKey: portalKeys.job(jobId),
    queryFn: () => portalApi.job(jobId),
    refetchInterval: (q) => (q.state.data && isFinal(q.state.data) ? false : 1000),
  })
  const j = job.data
  if (!j) {
    return <p className={cn('text-muted-foreground flex items-center gap-2 text-sm', className)}><Loader2 className="size-4 animate-spin" /> Queued…</p>
  }
  return (
    <div className={cn('space-y-2 rounded-lg border p-3 text-sm', className)} role="status">
      <div className="flex items-center gap-2">
        {j.status === 'SUCCEEDED' ? <CheckCircle2 className="text-success size-4" />
          : isFinal(j) ? <XCircle className="text-destructive size-4" /> : <Loader2 className="size-4 animate-spin" />}
        <span className="font-medium">{j.status === 'SUCCEEDED' ? 'Done' : j.status.toLowerCase()}</span>
        <span className="text-muted-foreground truncate">{j.progressMessage ?? ''}</span>
        {j.status === 'SUCCEEDED' && j.artifact && (
          <Button size="sm" variant="outline" className="ml-auto" onClick={() => void downloadJobArtifact(j.id)}>
            <Download /> {j.artifact.filename}
          </Button>
        )}
      </div>
      <div className="bg-muted h-1.5 overflow-hidden rounded-full" aria-label={`${j.progress}% complete`}>
        <div className={cn('h-full transition-all', j.status === 'SUCCEEDED' ? 'bg-success' : isFinal(j) ? 'bg-destructive' : 'bg-primary')}
             style={{ width: `${Math.max(3, j.progress)}%` }} />
      </div>
      {j.error && <p className="text-destructive text-xs">{j.error}</p>}
    </div>
  )
}
