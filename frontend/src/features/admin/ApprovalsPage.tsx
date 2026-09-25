import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, Clock, Stamp, X } from 'lucide-react'
import { toast } from 'sonner'
import { portalApi, portalKeys, usePermissions } from '@/api/portal'
import type { ApprovalRequest, ApprovalStatus } from '@/api/types'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

type View = 'to-decide' | 'mine' | 'all'

export const APPROVAL_STATUS: Record<ApprovalStatus, { label: string; variant: 'warning' | 'success' | 'destructive' | 'muted' | 'default' }> = {
  PENDING: { label: 'Waiting', variant: 'warning' },
  APPROVED: { label: 'Approved', variant: 'default' },
  EXECUTED: { label: 'Done', variant: 'success' },
  FAILED: { label: 'Approved, but failed', variant: 'destructive' },
  REJECTED: { label: 'Rejected', variant: 'destructive' },
  CANCELLED: { label: 'Withdrawn', variant: 'muted' },
  EXPIRED: { label: 'Expired', variant: 'muted' },
}

export default function ApprovalsPage() {
  const { any, loading } = usePermissions()
  const canSeeAll = any('approval.view', 'approval.decide')
  // Until the user picks a tab, follow the permissions (they may still be loading on a fresh page load).
  const [picked, setView] = useState<View | null>(null)
  const view: View = picked ?? (any('approval.decide') ? 'to-decide' : 'mine')
  const [status, setStatus] = useState<ApprovalStatus | ''>('')
  const [page, setPage] = useState(0)
  const list = useQuery({
    queryKey: portalKeys.approvals(view, status, page),
    queryFn: () => portalApi.approvals(view, status, page),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
    enabled: !loading,
  })
  const tabs: { id: View; label: string; show: boolean }[] = [
    { id: 'to-decide', label: 'Waiting for me', show: any('approval.decide') },
    { id: 'mine', label: 'My requests', show: true },
    { id: 'all', label: 'All', show: canSeeAll },
  ]

  return (
    <>
      <PageHeader title="Approvals" description="Sensitive actions wait here until a second person approves them." />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="bg-muted inline-flex rounded-lg p-1" role="tablist">
          {tabs.filter((t) => t.show).map((t) => (
            <button key={t.id} type="button" role="tab" aria-selected={view === t.id}
                    onClick={() => { setView(t.id); setPage(0) }}
                    className={cn('rounded-md px-3 py-1.5 text-sm font-medium',
                      view === t.id ? 'bg-background shadow-sm' : 'text-muted-foreground hover:text-foreground')}>
              {t.label}
            </button>
          ))}
        </div>
        {view !== 'to-decide' && (
          <NativeSelect aria-label="Status" className="w-44" value={status}
                        onChange={(e) => { setStatus(e.target.value as ApprovalStatus | ''); setPage(0) }}>
            <option value="">Any status</option>
            {Object.entries(APPROVAL_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
          </NativeSelect>
        )}
      </div>

      {list.isError ? <ErrorState error={list.error} onRetry={() => list.refetch()} /> :
        list.isPending ? <Skeleton className="h-64 rounded-xl" /> :
          list.data.content.length === 0 ? (
            <EmptyState icon={<Stamp className="text-muted-foreground size-8" />}
                        title={view === 'to-decide' ? 'Nothing is waiting for you' : 'No requests'}
                        description={view === 'to-decide' ? 'When someone asks to publish a test or finalize ranks, it appears here.' : undefined} />
          ) : (
            <>
              <ul className="bg-card divide-y rounded-xl border">
                {list.data.content.map((r) => <ApprovalRow key={r.id} r={r} />)}
              </ul>
              <div className="mt-4"><Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} /></div>
            </>
          )}
    </>
  )
}

function ApprovalRow({ r }: { r: ApprovalRequest }) {
  const s = APPROVAL_STATUS[r.status]
  return (
    <li>
      <Link to={`/admin/approvals/${r.id}`} className="hover:bg-muted/40 flex flex-wrap items-center gap-3 px-4 py-3">
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{r.title}</p>
          <p className="text-muted-foreground text-xs">
            {r.actionDescription} · asked by {r.requestedByName} · {formatDateTime(r.requestedAt)}
          </p>
        </div>
        {r.canDecide && <Badge variant="warning">Your decision</Badge>}
        <Badge variant={s.variant}>{s.label}</Badge>
      </Link>
    </li>
  )
}

export function ApprovalDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [comment, setComment] = useState('')
  const req = useQuery({ queryKey: portalKeys.approval(id), queryFn: () => portalApi.approval(id) })

  const done = (r: ApprovalRequest, msg: string) => {
    qc.setQueryData(portalKeys.approval(id), r)
    void qc.invalidateQueries({ queryKey: ['admin', 'approvals'] })
    void qc.invalidateQueries({ queryKey: ['admin', 'tests'] })
    void qc.invalidateQueries({ queryKey: ['admin', 'test'] })
    if (r.status === 'FAILED') toast.error(`Approved, but the action failed: ${r.error ?? 'unknown error'}`)
    else toast.success(msg)
  }
  const approve = useMutation({ mutationFn: () => portalApi.approve(id, comment || undefined),
    onSuccess: (r) => done(r, 'Approved and done'), onError: (e) => toast.error(errorMessage(e)) })
  const reject = useMutation({ mutationFn: () => portalApi.reject(id, comment),
    onSuccess: (r) => done(r, 'Rejected'), onError: (e) => toast.error(errorMessage(e)) })
  const cancel = useMutation({ mutationFn: () => portalApi.cancelApproval(id),
    onSuccess: (r) => done(r, 'Request withdrawn'), onError: (e) => toast.error(errorMessage(e)) })

  if (req.isError) return <ErrorState error={req.error} onRetry={() => req.refetch()} />
  if (req.isPending) return <PageLoader />
  const r = req.data
  const s = APPROVAL_STATUS[r.status]
  const busy = approve.isPending || reject.isPending || cancel.isPending
  const target = r.entityType === 'TEST' ? `/admin/tests/${r.entityId}` : r.entityType === 'ROLE' ? '/admin/roles' : null

  return (
    <>
      <PageHeader title={r.title} description={r.actionDescription}
                  actions={<Button variant="outline" onClick={() => navigate('/admin/approvals')}><ArrowLeft /> Approvals</Button>} />
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2">Request <Badge variant={s.variant}>{s.label}</Badge></CardTitle></CardHeader>
          <CardContent className="space-y-4 text-sm">
            <dl className="grid grid-cols-[9rem_1fr] gap-x-3 gap-y-2">
              <dt className="text-muted-foreground">Asked by</dt><dd>{r.requestedByName}</dd>
              <dt className="text-muted-foreground">Asked on</dt><dd>{formatDateTime(r.requestedAt)}</dd>
              <dt className="text-muted-foreground">Reason</dt><dd>{r.reason ?? <span className="text-muted-foreground">none given</span>}</dd>
              <dt className="text-muted-foreground">Target</dt>
              <dd>{r.entityType} {target ? <Link to={target} className="text-primary font-mono text-xs hover:underline">{r.entityId}</Link>
                : <span className="font-mono text-xs">{r.entityId}</span>}</dd>
              {r.status === 'PENDING' && (<><dt className="text-muted-foreground">Expires</dt>
                <dd className="flex items-center gap-1"><Clock className="size-3.5" /> {formatDateTime(r.expiresAt)}</dd></>)}
              {r.decidedAt && (<><dt className="text-muted-foreground">Decided</dt>
                <dd>{r.decidedByName ?? 'requester'} · {formatDateTime(r.decidedAt)}</dd></>)}
              {r.decisionComment && (<><dt className="text-muted-foreground">Comment</dt><dd>{r.decisionComment}</dd></>)}
              {r.executedAt && (<><dt className="text-muted-foreground">Executed</dt><dd>{formatDateTime(r.executedAt)}</dd></>)}
              {r.error && (<><dt className="text-muted-foreground">Error</dt><dd className="text-destructive">{r.error}</dd></>)}
            </dl>
            {r.payload && (
              <details>
                <summary className="cursor-pointer font-medium">What will be done (parameters)</summary>
                <pre className="bg-muted mt-2 max-h-72 overflow-auto rounded-md p-3 text-xs">{JSON.stringify(r.payload, null, 2)}</pre>
              </details>
            )}
            {r.result && (
              <div className="bg-success/10 rounded-md p-3 text-xs"><p className="mb-1 font-semibold">Result</p>
                <pre className="overflow-auto">{JSON.stringify(r.result, null, 2)}</pre></div>
            )}
          </CardContent>
        </Card>

        {(r.canDecide || r.canCancel) && (
          <Card>
            <CardHeader><CardTitle>{r.canDecide ? 'Your decision' : 'Your request'}</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {r.canDecide && (
                <>
                  <div className="grid gap-2">
                    <Label htmlFor="decision-comment">Comment <span className="text-muted-foreground">(required to reject)</span></Label>
                    <Textarea id="decision-comment" rows={3} value={comment} maxLength={2000}
                              onChange={(e) => setComment(e.target.value)} placeholder="e.g. Checked the paper and answer keys" />
                  </div>
                  <div className="flex gap-2">
                    <Button variant="success" className="flex-1" disabled={busy} loading={approve.isPending}
                            onClick={() => approve.mutate()}><Check /> Approve</Button>
                    <Button variant="destructive" className="flex-1" disabled={busy || comment.trim().length < 3}
                            loading={reject.isPending} onClick={() => reject.mutate()}><X /> Reject</Button>
                  </div>
                  <p className="text-muted-foreground text-xs">Approving runs the action immediately, as you.</p>
                </>
              )}
              {r.canCancel && (
                <Button variant="outline" className="w-full" disabled={busy} loading={cancel.isPending}
                        onClick={() => cancel.mutate()}>Withdraw request</Button>
              )}
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
