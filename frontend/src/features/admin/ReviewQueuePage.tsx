import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { AlarmClock, Check, Hand, Rocket, Send, Settings2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAdminQuestions } from '@/api/admin'
import { contentApi, contentKeys, useQueueCounts } from '@/api/content'
import { usePermissions } from '@/api/portal'
import type { BulkAction, ContentSettings, QueueCounts } from '@/api/types'
import { MathText } from '@/components/common/MathText'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatRelative, plural } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { QuestionFilter, QuestionSummary } from '@/types/admin'
import { LANGUAGE_LABEL, QUESTION_STATUS, QUESTION_STATUS_LABEL, TYPE_LABEL } from './labels'
import { CommentDialog } from './studio/WorkflowActions'

type Tab = 'mine' | 'unassigned' | 'overdue' | 'all' | 'changes' | 'approved' | 'drafts'

interface TabDef {
  id: Tab
  label: string
  count: (c: QueueCounts) => number
  filter: QuestionFilter
  perms: string[]
  bulk?: { action: BulkAction; label: string; icon: typeof Check }
  empty: string
}

const TABS: TabDef[] = [
  { id: 'mine', label: 'Assigned to me', count: (c) => c.assignedToMe, filter: { reviewer: 'me', sort: 'reviewDueAt,asc' },
    perms: ['question.review'], bulk: { action: 'APPROVE', label: 'Approve', icon: Check }, empty: 'Nothing waiting for you. Nice.' },
  { id: 'unassigned', label: 'Unassigned', count: (c) => c.unassigned, filter: { reviewer: 'none', sort: 'reviewDueAt,asc' },
    perms: ['question.review'], bulk: { action: 'APPROVE', label: 'Approve', icon: Check }, empty: 'Every review has a reviewer.' },
  { id: 'overdue', label: 'Overdue', count: (c) => c.overdue, filter: { overdue: true, sort: 'reviewDueAt,asc' },
    perms: ['question.review', 'question.approve'], bulk: { action: 'APPROVE', label: 'Approve', icon: Check }, empty: 'No review is past its due time.' },
  { id: 'all', label: 'All in review', count: (c) => c.inReview, filter: { status: 'IN_REVIEW', sort: 'reviewDueAt,asc' },
    perms: ['question.review', 'question.approve'], bulk: { action: 'APPROVE', label: 'Approve', icon: Check }, empty: 'Nothing is in review.' },
  { id: 'changes', label: 'Changes requested (mine)', count: (c) => c.changesRequested, filter: { status: 'CHANGES_REQUESTED', mine: true, sort: 'updatedAt,desc' },
    perms: ['question.create'], bulk: { action: 'SUBMIT', label: 'Submit again', icon: Send }, empty: 'No reviewer is waiting on you.' },
  { id: 'approved', label: 'Ready to publish', count: (c) => c.approved, filter: { status: 'APPROVED', sort: 'updatedAt,asc' },
    perms: ['question.publish', 'question.approve'], bulk: { action: 'PUBLISH', label: 'Publish', icon: Rocket }, empty: 'No approved question is waiting.' },
  { id: 'drafts', label: 'My drafts', count: (c) => c.myDrafts, filter: { status: 'DRAFT', mine: true, sort: 'updatedAt,desc' },
    perms: ['question.create'], bulk: { action: 'SUBMIT', label: 'Submit for review', icon: Send }, empty: 'You have no drafts.' },
]

export default function ReviewQueuePage() {
  const { any, can, loading } = usePermissions()
  const counts = useQueueCounts()
  const [params, setParams] = useSearchParams()
  const tabs = TABS.filter((t) => any(...t.perms))
  const requested = params.get('tab') as Tab | null
  const firstBusy = counts.data ? tabs.find((t) => t.count(counts.data) > 0) : undefined
  const tab = tabs.find((t) => t.id === requested) ?? firstBusy ?? tabs[0]
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string[]>([])
  useEffect(() => { setSelected([]); setPage(0) }, [tab?.id])

  const filter: QuestionFilter = { ...(tab?.filter ?? {}), page, size: 20 }
  const list = useAdminQuestions(filter, !!tab)
  const qc = useQueryClient()
  const [approveOpen, setApproveOpen] = useState(false)

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['admin', 'questions'] })
  }
  const bulk = useMutation({
    mutationFn: ({ action, comment }: { action: BulkAction; comment?: string }) => contentApi.bulk(action, selected, comment),
    onSuccess: (r) => {
      setSelected([])
      setApproveOpen(false)
      refresh()
      if (r.failed.length === 0) toast.success(`${plural(r.succeeded, 'question')} done`)
      else toast.warning(`${r.succeeded} done, ${r.failed.length} not possible`, {
        description: r.failed.slice(0, 3).map((f) => f.message).join(' · '), duration: 10_000,
      })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const claim = useMutation({
    mutationFn: (id: string) => contentApi.claim(id),
    onSuccess: () => { toast.success('The review is yours now'); refresh() },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (loading) return <Skeleton className="h-96 rounded-xl" />
  const rows = list.data?.content ?? []
  const allSelected = rows.length > 0 && rows.every((r) => selected.includes(r.id))

  return (
    <>
      <PageHeader title="Review queue" description="Questions on their way to students: review, fix, approve, publish." />
      <div className="mb-4 flex flex-wrap gap-1" role="tablist" aria-label="Queues">
        {tabs.map((t) => {
          const n = counts.data ? t.count(counts.data) : undefined
          return (
            <button key={t.id} type="button" role="tab" aria-selected={tab?.id === t.id}
                    onClick={() => setParams({ tab: t.id }, { replace: true })}
                    className={cn('flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-medium',
                      tab?.id === t.id ? 'bg-background border-primary shadow-sm' : 'text-muted-foreground hover:text-foreground border-transparent')}>
              {t.id === 'overdue' && <AlarmClock className="size-3.5" />}
              {t.label}
              {n != null && <span className={cn('rounded-full px-1.5 text-[11px] leading-5',
                n > 0 && (t.id === 'overdue' ? 'bg-destructive text-white' : 'bg-primary text-primary-foreground'),
                n === 0 && 'bg-muted')}>{n}</span>}
            </button>
          )
        })}
      </div>

      {list.isError ? <ErrorState error={list.error} onRetry={() => list.refetch()} /> :
        list.isPending ? <Skeleton className="h-72 rounded-xl" /> :
          rows.length === 0 ? <EmptyState title={tab?.empty ?? 'Nothing here'} description="New work appears here automatically." /> : (
            <>
              <Card className="py-0">
                <CardContent className="overflow-x-auto px-0">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-b text-left">
                        <th className="w-10 px-4 py-3">
                          <Checkbox checked={allSelected} aria-label="Select all on this page"
                                    onCheckedChange={(v) => setSelected(v ? rows.map((r) => r.id) : [])} />
                        </th>
                        <th className="px-3 py-3 font-medium">Question</th>
                        <th className="px-3 py-3 font-medium">Topic</th>
                        <th className="px-3 py-3 font-medium">Status</th>
                        <th className="px-3 py-3 font-medium">Reviewer</th>
                        <th className="px-3 py-3 font-medium">Due</th>
                        <th className="px-4 py-3" />
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((q) => (
                        <QueueRow key={q.id} q={q} checked={selected.includes(q.id)}
                                  onCheck={(v) => setSelected((s) => (v ? [...s, q.id] : s.filter((x) => x !== q.id)))}
                                  canClaim={can('question.review') && q.status === 'IN_REVIEW' && !q.reviewerId}
                                  claiming={claim.isPending} onClaim={() => claim.mutate(q.id)} />
                      ))}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
              <div className="mt-4"><Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} /></div>
            </>
          )}

      {selected.length > 0 && tab?.bulk && (
        <div className="bg-background/95 sticky bottom-0 z-20 -mx-4 mt-6 flex flex-wrap items-center gap-3 border-t px-4 py-3 backdrop-blur sm:-mx-6 sm:px-6"
             role="region" aria-label="Bulk actions">
          <span className="text-sm font-medium">{plural(selected.length, 'question')} selected</span>
          <Button variant="outline" size="sm" onClick={() => setSelected([])}>Clear</Button>
          <Button size="sm" className="ml-auto" loading={bulk.isPending}
                  onClick={() => (tab.bulk!.action === 'APPROVE' ? setApproveOpen(true) : bulk.mutate({ action: tab.bulk!.action }))}>
            <tab.bulk.icon /> {tab.bulk.label} {selected.length}
          </Button>
        </div>
      )}
      <CommentDialog open={approveOpen} onClose={() => setApproveOpen(false)} pending={bulk.isPending}
                     title={`Approve ${plural(selected.length, 'question')}`} confirmText="Approve all"
                     description="Questions you submitted or last edited are skipped and listed afterwards."
                     onConfirm={(comment) => bulk.mutate({ action: 'APPROVE', comment })} />

      {can('settings.manage') && <SettingsCard />}
    </>
  )
}

function QueueRow({ q, checked, onCheck, canClaim, claiming, onClaim }: {
  q: QuestionSummary; checked: boolean; onCheck: (v: boolean) => void; canClaim: boolean; claiming: boolean; onClaim: () => void
}) {
  const overdue = q.status === 'IN_REVIEW' && !!q.reviewDueAt && new Date(q.reviewDueAt).getTime() < Date.now()
  return (
    <tr className={cn('hover:bg-muted/40 border-b last:border-0', overdue && 'bg-destructive/5')}>
      <td className="px-4 py-2.5"><Checkbox checked={checked} onCheckedChange={(v) => onCheck(v === true)} aria-label="Select" /></td>
      <td className="max-w-md px-3 py-2.5">
        <Link to={`/admin/questions/${q.id}`} className="line-clamp-2 hover:underline"><MathText as="span" text={q.textPreview || '(no text)'} /></Link>
        <p className="text-muted-foreground mt-0.5 flex flex-wrap gap-x-2 text-xs">
          <span>{TYPE_LABEL[q.type]}</span>
          <span>v{q.currentVersion}{q.publishedVersion != null && q.publishedVersion !== q.currentVersion ? ` (live v${q.publishedVersion})` : ''}</span>
          {(q.languages ?? []).length > 1 && <span>{(q.languages ?? []).map((l) => LANGUAGE_LABEL[l]).join(' + ')}</span>}
        </p>
      </td>
      <td className="px-3 py-2.5">
        <p>{q.topic.topicName}</p>
        <p className="text-muted-foreground text-xs">{q.topic.subjectName}</p>
      </td>
      <td className="px-3 py-2.5"><Badge variant={QUESTION_STATUS[q.status]}>{QUESTION_STATUS_LABEL[q.status]}</Badge></td>
      <td className="px-3 py-2.5">{q.reviewerName ?? <span className="text-muted-foreground">—</span>}</td>
      <td className="px-3 py-2.5 whitespace-nowrap" title={formatDateTime(q.reviewDueAt)}>
        {q.status === 'IN_REVIEW' && q.reviewDueAt
          ? <span className={cn(overdue && 'text-destructive font-medium')}>{overdue ? `overdue ${formatRelative(q.reviewDueAt).replace(' ago', '')}` : formatRelative(q.reviewDueAt)}</span>
          : <span className="text-muted-foreground">{formatRelative(q.updatedAt)}</span>}
      </td>
      <td className="px-4 py-2.5">
        <div className="flex justify-end gap-1">
          {canClaim && <Button size="sm" variant="outline" disabled={claiming} onClick={onClaim}><Hand /> Take</Button>}
          <Button size="sm" variant="ghost" asChild><Link to={`/admin/questions/${q.id}`}>Open</Link></Button>
        </div>
      </td>
    </tr>
  )
}

function SettingsCard() {
  const qc = useQueryClient()
  const settings = useQuery({ queryKey: contentKeys.settings, queryFn: contentApi.settings })
  const [draft, setDraft] = useState<ContentSettings | null>(null)
  const value = draft ?? settings.data
  const dirty = useMemo(() => !!draft && JSON.stringify(draft) !== JSON.stringify(settings.data), [draft, settings.data])
  const save = useMutation({
    mutationFn: (s: ContentSettings) => contentApi.updateSettings(s),
    onSuccess: (s) => { qc.setQueryData(contentKeys.settings, s); setDraft(null); toast.success('Workflow settings saved') },
    onError: (e) => toast.error(errorMessage(e)),
  })
  if (!value) return null
  const set = (patch: Partial<ContentSettings>) => setDraft({ ...value, ...patch })
  return (
    <Card className="mt-8">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Settings2 className="size-4" /> Workflow settings</CardTitle>
        <CardDescription>Apply to everyone who authors or reviews questions.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        <label className="flex items-start gap-3">
          <Checkbox className="mt-0.5" checked={value.reviewRequired} onCheckedChange={(v) => set({ reviewRequired: v === true })} />
          <span>
            <span className="font-medium">A review is required before publishing</span>
            <span className="text-muted-foreground block">Turn off only for a small trusted team: publishers can then publish drafts directly.</span>
          </span>
        </label>
        <label className="flex items-start gap-3">
          <Checkbox className="mt-0.5" checked={value.autoAssign} onCheckedChange={(v) => set({ autoAssign: v === true })} />
          <span>
            <span className="font-medium">Assign reviewers automatically</span>
            <span className="text-muted-foreground block">The eligible reviewer with the fewest open reviews gets new submissions.</span>
          </span>
        </label>
        <div className="flex items-center gap-3">
          <label htmlFor="sla-hours" className="font-medium">Review due within</label>
          <Input id="sla-hours" className="w-24" inputMode="numeric" value={value.slaHours}
                 onChange={(e) => set({ slaHours: Number(e.target.value.replace(/\D/g, '')) || 0 })} />
          <span className="text-muted-foreground">hours of submission</span>
        </div>
        <Button disabled={!dirty || value.slaHours < 1 || value.slaHours > 720} loading={save.isPending} onClick={() => save.mutate(value)}>
          Save settings
        </Button>
      </CardContent>
    </Card>
  )
}
