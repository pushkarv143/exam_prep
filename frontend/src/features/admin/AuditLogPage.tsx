import { useMemo, useState } from 'react'
import { useInfiniteQuery, useMutation } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Download, Filter, RotateCcw } from 'lucide-react'
import { toast } from 'sonner'
import { portalApi } from '@/api/portal'
import type { AuditChange, AuditEntry, AuditFilterInput } from '@/api/types'
import { DataTable } from '@/components/common/DataTable'
import { ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { JobProgress } from './components/JobProgress'

const OUTCOME_VARIANT = { SUCCESS: 'success', FAILURE: 'warning', DENIED: 'destructive' } as const
const ACTION_PRESETS = ['', 'auth.', 'test.', 'question.', 'user.', 'role.', 'approval.', 'mfa.', 'security.', 'session.']

function isoDay(d: Date) {
  return d.toISOString().slice(0, 10)
}

export default function AuditLogPage() {
  const today = new Date()
  const [draft, setDraft] = useState({
    from: isoDay(new Date(today.getTime() - 29 * 86_400_000)), to: isoDay(today),
    actorEmail: '', action: '', entityType: '', entityId: '', outcome: '', q: '',
  })
  const [applied, setApplied] = useState(draft)
  const [selected, setSelected] = useState<AuditEntry | null>(null)
  const [exportJob, setExportJob] = useState<string | null>(null)

  const filter: AuditFilterInput = useMemo(() => ({
    from: new Date(`${applied.from}T00:00:00`).toISOString(),
    to: new Date(new Date(`${applied.to}T00:00:00`).getTime() + 86_400_000).toISOString(),
    actorEmail: applied.actorEmail || undefined, action: applied.action || undefined,
    entityType: applied.entityType || undefined, entityId: applied.entityId || undefined,
    outcome: applied.outcome || undefined, q: applied.q || undefined,
  }), [applied])

  const audit = useInfiniteQuery({
    queryKey: ['admin', 'audit', filter],
    queryFn: ({ pageParam }) => portalApi.audit(filter, pageParam, 50),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const rows = audit.data?.pages.flatMap((p) => p.items) ?? []

  const exporter = useMutation({
    mutationFn: () => portalApi.exportAudit(filter),
    onSuccess: (job) => { setExportJob(job.id); toast.success('Export started') },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const columns = useMemo<ColumnDef<AuditEntry, unknown>[]>(() => [
    { id: 'time', header: 'When', enableHiding: false,
      cell: ({ row }) => <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.original.occurredAt)}</span> },
    { id: 'actor', header: 'Who',
      cell: ({ row }) => <div className="max-w-48"><p className="truncate">{row.original.actorEmail ?? 'system'}</p>
        <p className="text-muted-foreground truncate text-xs">{row.original.actorRoles}</p></div> },
    { id: 'action', header: 'Action', enableHiding: false,
      cell: ({ row }) => <code className="bg-muted rounded px-1.5 py-0.5 text-xs">{row.original.action}</code> },
    { id: 'entity', header: 'Entity',
      cell: ({ row }) => row.original.entityType ? <span className="text-xs">{row.original.entityType}
        <span className="text-muted-foreground block max-w-40 truncate font-mono">{row.original.entityId}</span></span> : '–' },
    { id: 'outcome', header: 'Outcome',
      cell: ({ row }) => <Badge variant={OUTCOME_VARIANT[row.original.outcome as keyof typeof OUTCOME_VARIANT] ?? 'muted'}>
        {row.original.outcome.toLowerCase()}</Badge> },
    { id: 'changes', header: 'Changes',
      cell: ({ row }) => row.original.changes?.length ? <span className="text-xs">{row.original.changes.length} field(s)</span>
        : <span className="text-muted-foreground text-xs">–</span> },
    { id: 'reason', header: 'Reason',
      cell: ({ row }) => <span className="line-clamp-2 max-w-56 text-xs">{row.original.reason ?? ''}</span> },
    { id: 'ip', header: 'IP', cell: ({ row }) => <span className="font-mono text-xs">{row.original.ip ?? ''}</span> },
    { id: 'status', header: 'HTTP', cell: ({ row }) => <span className="text-xs tabular-nums">{row.original.statusCode ?? ''}</span> },
  ], [])

  const set = (k: keyof typeof draft) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setDraft({ ...draft, [k]: e.target.value })

  return (
    <>
      <PageHeader title="Audit log" description="Every admin change: who, what, when and exactly what changed. Append-only."
                  actions={<Button variant="outline" loading={exporter.isPending} onClick={() => exporter.mutate()}>
                    <Download /> Export CSV</Button>} />
      {exportJob && <JobProgress jobId={exportJob} className="mb-4" />}

      <form className="bg-card mb-4 grid gap-3 rounded-xl border p-4 sm:grid-cols-2 lg:grid-cols-4"
            onSubmit={(e) => { e.preventDefault(); setApplied(draft) }}>
        <div className="grid gap-1.5"><Label htmlFor="af-from">From</Label><Input id="af-from" type="date" value={draft.from} onChange={set('from')} /></div>
        <div className="grid gap-1.5"><Label htmlFor="af-to">To</Label><Input id="af-to" type="date" value={draft.to} onChange={set('to')} /></div>
        <div className="grid gap-1.5"><Label htmlFor="af-actor">Actor email</Label>
          <Input id="af-actor" placeholder="contains…" value={draft.actorEmail} onChange={set('actorEmail')} /></div>
        <div className="grid gap-1.5"><Label htmlFor="af-action">Action</Label>
          <NativeSelect id="af-action" value={ACTION_PRESETS.includes(draft.action) ? draft.action : ''} onChange={set('action')}>
            {ACTION_PRESETS.map((a) => <option key={a} value={a}>{a ? `${a}*` : 'Any action'}</option>)}
          </NativeSelect></div>
        <div className="grid gap-1.5"><Label htmlFor="af-type">Entity type</Label>
          <Input id="af-type" placeholder="TEST, USER, ROLE…" value={draft.entityType} onChange={set('entityType')} /></div>
        <div className="grid gap-1.5"><Label htmlFor="af-id">Entity id</Label>
          <Input id="af-id" placeholder="exact id" value={draft.entityId} onChange={set('entityId')} /></div>
        <div className="grid gap-1.5"><Label htmlFor="af-outcome">Outcome</Label>
          <NativeSelect id="af-outcome" value={draft.outcome} onChange={set('outcome')}>
            <option value="">Any</option><option value="SUCCESS">Success</option><option value="FAILURE">Failure</option>
            <option value="DENIED">Denied</option>
          </NativeSelect></div>
        <div className="grid gap-1.5"><Label htmlFor="af-q">Text</Label>
          <Input id="af-q" placeholder="action, path or reason" value={draft.q} onChange={set('q')} /></div>
        <div className="flex gap-2 sm:col-span-2 lg:col-span-4">
          <Button type="submit"><Filter /> Apply filters</Button>
          <Button type="button" variant="ghost" onClick={() => {
            const reset = { ...draft, actorEmail: '', action: '', entityType: '', entityId: '', outcome: '', q: '' }
            setDraft(reset)
            setApplied(reset)
          }}><RotateCcw /> Reset</Button>
        </div>
      </form>

      {audit.isError ? <ErrorState error={audit.error} onRetry={() => audit.refetch()} /> : (
        <>
          <DataTable id="audit" columns={columns} data={rows} loading={audit.isPending} rowKey={(r) => r.id}
                     onRowClick={setSelected} empty={<span className="text-muted-foreground">No audit entries for these filters.</span>}
                     toolbar={<span className="text-muted-foreground text-sm">{rows.length} shown{audit.hasNextPage ? ' (more available)' : ''}</span>} />
          {audit.hasNextPage && (
            <div className="mt-4 flex justify-center">
              <Button variant="outline" loading={audit.isFetchingNextPage} onClick={() => void audit.fetchNextPage()}>Load more</Button>
            </div>
          )}
        </>
      )}

      <AuditDetail entry={selected} onClose={() => setSelected(null)} />
    </>
  )
}

function AuditDetail({ entry, onClose }: { entry: AuditEntry | null; onClose: () => void }) {
  return (
    <Sheet open={!!entry} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-2xl">
        {entry && (
          <>
            <SheetHeader>
              <SheetTitle className="font-mono text-base">{entry.action}</SheetTitle>
              <SheetDescription>{formatDateTime(entry.occurredAt)} · {entry.actorEmail ?? 'system'}</SheetDescription>
            </SheetHeader>
            <dl className="grid grid-cols-[8rem_1fr] gap-x-3 gap-y-1.5 text-sm">
              {([
                ['Outcome', <Badge key="o" variant={OUTCOME_VARIANT[entry.outcome as keyof typeof OUTCOME_VARIANT] ?? 'muted'}>{entry.outcome}</Badge>],
                ['Entity', entry.entityType ? `${entry.entityType} ${entry.entityId ?? ''}` : '–'],
                ['Roles', entry.actorRoles ?? '–'],
                ['Reason', entry.reason ?? '–'],
                ['Request', `${entry.httpMethod ?? ''} ${entry.path ?? ''}`.trim() || '–'],
                ['Status', entry.statusCode ? `${entry.statusCode}${entry.errorCode ? ` · ${entry.errorCode}` : ''}` : '–'],
                ['IP', entry.ip ?? '–'],
                ['Device', entry.userAgent ?? '–'],
                ['Request id', entry.requestId ?? '–'],
              ] as [string, React.ReactNode][]).map(([k, v]) => (
                <div key={k} className="contents">
                  <dt className="text-muted-foreground">{k}</dt><dd className="break-all">{v}</dd>
                </div>
              ))}
            </dl>
            <section>
              <h3 className="mb-2 text-sm font-semibold">What changed</h3>
              {entry.changes?.length ? <ChangesTable changes={entry.changes} />
                : <p className="text-muted-foreground text-sm">No before/after snapshot for this action.</p>}
            </section>
            {entry.metadata && Object.keys(entry.metadata).length > 0 && (
              <details className="text-sm">
                <summary className="cursor-pointer font-semibold">Details (request body, ids)</summary>
                <pre className="bg-muted mt-2 max-h-80 overflow-auto rounded-md p-3 text-xs">{JSON.stringify(entry.metadata, null, 2)}</pre>
              </details>
            )}
            {(entry.before != null || entry.after != null) && (
              <details className="text-sm">
                <summary className="cursor-pointer font-semibold">Full before / after</summary>
                <div className="mt-2 grid gap-2 md:grid-cols-2">
                  <pre className="bg-muted max-h-80 overflow-auto rounded-md p-3 text-xs">{JSON.stringify(entry.before, null, 2) ?? 'null'}</pre>
                  <pre className="bg-muted max-h-80 overflow-auto rounded-md p-3 text-xs">{JSON.stringify(entry.after, null, 2) ?? 'null'}</pre>
                </div>
              </details>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}

function show(v: unknown) {
  if (v === undefined) return ''
  return typeof v === 'string' ? v : JSON.stringify(v)
}

export function ChangesTable({ changes }: { changes: AuditChange[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border">
      <table className="w-full text-xs">
        <thead><tr className="text-muted-foreground border-b text-left"><th className="px-2 py-1.5">Field</th>
          <th className="px-2 py-1.5">Before</th><th className="px-2 py-1.5">After</th></tr></thead>
        <tbody>
          {changes.map((c, i) => (
            <tr key={i} className="border-b align-top last:border-0">
              <td className="px-2 py-1.5 font-mono">{c.path}</td>
              <td className="max-w-60 px-2 py-1.5 break-all">
                {c.op === 'added' ? <span className="text-muted-foreground">–</span>
                  : <span className="bg-destructive/10 text-destructive rounded px-1">{show(c.from)}</span>}
              </td>
              <td className="max-w-60 px-2 py-1.5 break-all">
                {c.op === 'removed' ? <span className="text-muted-foreground">–</span>
                  : <span className="bg-success/15 text-success rounded px-1">{show(c.to)}</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
