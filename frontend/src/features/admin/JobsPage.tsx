import { useMemo, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Ban, Download, RotateCw } from 'lucide-react'
import { toast } from 'sonner'
import { portalApi, portalKeys, usePermissions } from '@/api/portal'
import type { Job, JobStatus } from '@/api/types'
import { DataTable } from '@/components/common/DataTable'
import { Pagination } from '@/components/common/Pagination'
import { ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { NativeSelect } from '@/components/ui/native-select'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { downloadJobArtifact, isFinal } from './components/JobProgress'

const STATUS: Record<JobStatus, { label: string; variant: 'muted' | 'default' | 'success' | 'destructive' | 'warning' }> = {
  QUEUED: { label: 'Queued', variant: 'muted' },
  RUNNING: { label: 'Running', variant: 'default' },
  SUCCEEDED: { label: 'Succeeded', variant: 'success' },
  FAILED: { label: 'Failed', variant: 'destructive' },
  CANCELLED: { label: 'Cancelled', variant: 'muted' },
  DEAD: { label: 'Dead (gave up)', variant: 'destructive' },
}

const TYPE_LABEL: Record<string, string> = { 'audit.export': 'Audit log export' }

export default function JobsPage() {
  const qc = useQueryClient()
  const { can } = usePermissions()
  const [status, setStatus] = useState<JobStatus | ''>('')
  const [mine, setMine] = useState(!can('job.view'))
  const [page, setPage] = useState(0)
  const params = { status, mine, page }
  const jobs = useQuery({
    queryKey: portalKeys.jobs(params),
    queryFn: () => portalApi.jobs(params),
    placeholderData: keepPreviousData,
    refetchInterval: (q) => (q.state.data?.content.some((j) => !isFinal(j)) ? 2000 : 15_000),
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'jobs'] })
  const cancel = useMutation({ mutationFn: portalApi.cancelJob, onSuccess: () => { toast.success('Cancellation requested'); void refresh() },
    onError: (e) => toast.error(errorMessage(e)) })
  const retry = useMutation({ mutationFn: portalApi.retryJob, onSuccess: () => { toast.success('Job queued again'); void refresh() },
    onError: (e) => toast.error(errorMessage(e)) })

  const columns = useMemo<ColumnDef<Job, unknown>[]>(() => [
    { id: 'type', header: 'Job', enableHiding: false, cell: ({ row }) => (
      <div><p className="font-medium">{TYPE_LABEL[row.original.type] ?? row.original.type}</p>
        <p className="text-muted-foreground font-mono text-[11px]">{row.original.id.slice(0, 8)}</p></div>) },
    { id: 'status', header: 'Status', cell: ({ row }) => <Badge variant={STATUS[row.original.status].variant}>{STATUS[row.original.status].label}</Badge> },
    { id: 'progress', header: 'Progress', cell: ({ row }) => {
      const j = row.original
      return (
        <div className="w-44">
          <div className="bg-muted h-1.5 overflow-hidden rounded-full">
            <div className={cn('h-full', j.status === 'SUCCEEDED' ? 'bg-success' : isFinal(j) ? 'bg-destructive' : 'bg-primary')}
                 style={{ width: `${Math.max(2, j.progress)}%` }} />
          </div>
          <p className="text-muted-foreground mt-1 truncate text-xs">{j.progressMessage ?? (j.error ? j.error.slice(0, 80) : `${j.progress}%`)}</p>
        </div>
      )
    } },
    { id: 'attempts', header: 'Attempts', cell: ({ row }) => <span className="tabular-nums">{row.original.attempts}/{row.original.maxAttempts}</span> },
    { id: 'owner', header: 'Started by', cell: ({ row }) => row.original.createdByName ?? 'system' },
    { id: 'created', header: 'Created', cell: ({ row }) => <span className="whitespace-nowrap text-xs">{formatDateTime(row.original.createdAt)}</span> },
    { id: 'finished', header: 'Finished', cell: ({ row }) => <span className="whitespace-nowrap text-xs">{row.original.finishedAt ? formatDateTime(row.original.finishedAt) : '–'}</span> },
    { id: 'actions', header: '', enableHiding: false, cell: ({ row }) => {
      const j = row.original
      return (
        <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
          {j.artifact && j.status === 'SUCCEEDED' && (
            <Button size="sm" variant="ghost" onClick={() => void downloadJobArtifact(j.id)}><Download /> File</Button>)}
          {!isFinal(j) && <Button size="sm" variant="ghost" disabled={cancel.isPending} onClick={() => cancel.mutate(j.id)}><Ban /> Cancel</Button>}
          {['FAILED', 'DEAD', 'CANCELLED'].includes(j.status) && can('job.manage') && (
            <Button size="sm" variant="ghost" disabled={retry.isPending} onClick={() => retry.mutate(j.id)}><RotateCw /> Retry</Button>)}
        </div>
      )
    } },
  ], [cancel, retry, can])

  return (
    <>
      <PageHeader title="Background jobs" description="Exports and other long tasks. Failed jobs retry with backoff; Dead means the last retry failed too." />
      {jobs.isError ? <ErrorState error={jobs.error} onRetry={() => jobs.refetch()} /> : (
        <>
          <DataTable id="jobs" columns={columns} data={jobs.data?.content ?? []} loading={jobs.isPending} rowKey={(j) => j.id}
                     empty={<span className="text-muted-foreground">No jobs yet. Exports you start appear here.</span>}
                     toolbar={
                       <>
                         <NativeSelect aria-label="Status" className="h-8 w-40" value={status}
                                       onChange={(e) => { setStatus(e.target.value as JobStatus | ''); setPage(0) }}>
                           <option value="">All statuses</option>
                           {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                         </NativeSelect>
                         {can('job.view') && (
                           <label className="flex items-center gap-2 text-sm">
                             <Checkbox checked={mine} onCheckedChange={(v) => { setMine(v); setPage(0) }} /> Only mine
                           </label>
                         )}
                       </>
                     } />
          {jobs.data && <div className="mt-4"><Pagination page={jobs.data.page} totalPages={jobs.data.totalPages} onChange={setPage} /></div>}
        </>
      )}
    </>
  )
}
