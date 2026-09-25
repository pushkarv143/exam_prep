import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlarmClock, Archive, ArchiveRestore, Check, CircleCheck, FilePlus2, MessageSquare, Pencil, Rocket, RotateCcw, Send,
  Undo2, UserCheck,
} from 'lucide-react'
import { toast } from 'sonner'
import { adminKeys } from '@/api/admin'
import { contentApi, contentKeys } from '@/api/content'
import type { QuestionActivity } from '@/api/types'
import { MathText } from '@/components/common/MathText'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { NativeSelect } from '@/components/ui/native-select'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Question } from '@/types/admin'

const KIND: Record<string, { label: string; icon: typeof Send; tone?: string }> = {
  CREATED: { label: 'created the question', icon: FilePlus2 },
  EDITED: { label: 'saved a new version', icon: Pencil },
  COMMENT: { label: 'commented', icon: MessageSquare },
  SUBMITTED: { label: 'submitted for review', icon: Send },
  ASSIGNED: { label: 'assigned the review', icon: UserCheck },
  CHANGES_REQUESTED: { label: 'requested changes', icon: Undo2, tone: 'text-destructive' },
  APPROVED: { label: 'approved', icon: Check, tone: 'text-success' },
  PUBLISHED: { label: 'published', icon: Rocket, tone: 'text-success' },
  ARCHIVED: { label: 'archived the question', icon: Archive },
  RESTORED: { label: 'restored the question', icon: ArchiveRestore },
  ROLLED_BACK: { label: 'restored an older version', icon: RotateCcw },
  OVERDUE: { label: 'Review is overdue', icon: AlarmClock, tone: 'text-warning' },
}

/** Fields a comment can point at, derived from the question's shape. */
export function commentFields(q: Question): { value: string; label: string }[] {
  const out = [{ value: '', label: 'General' }, { value: 'content.text', label: 'Question text' }]
  if (q.type === 'PARAGRAPH') out.push({ value: 'content.paragraph', label: 'Passage' })
  for (const o of q.content.options ?? []) out.push({ value: `options.${o.id}`, label: `Option ${o.id}` })
  if (q.type !== 'PARAGRAPH') out.push({ value: 'answerKey', label: 'Answer key' }, { value: 'solution', label: 'Solution' })
  for (const lang of Object.keys(q.translations ?? {})) out.push({ value: `translation.${lang}`, label: lang === 'HI' ? 'Hindi translation' : 'English translation' })
  out.push({ value: 'metadata', label: 'Metadata' })
  return out
}

export function ReviewPanel({ question }: { question: Question }) {
  const qc = useQueryClient()
  const activity = useQuery({ queryKey: contentKeys.activity(question.id), queryFn: () => contentApi.activity(question.id) })
  const [body, setBody] = useState('')
  const [field, setField] = useState('')
  const fields = commentFields(question)
  const canComment = question.actions.includes('COMMENT')

  const refresh = (list: QuestionActivity[]) => {
    qc.setQueryData(contentKeys.activity(question.id), list)
    void qc.invalidateQueries({ queryKey: adminKeys.question(question.id) })
  }
  const add = useMutation({
    mutationFn: () => contentApi.comment(question.id, body.trim(), field),
    onSuccess: (list) => { setBody(''); refresh(list) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const resolve = useMutation({
    mutationFn: ({ id, resolved }: { id: string; resolved: boolean }) => contentApi.resolveComment(question.id, id, resolved),
    onSuccess: refresh,
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <div className="space-y-4">
      {question.review && question.status === 'IN_REVIEW' && <ReviewAssignment question={question} />}

      {canComment && (
        <div className="space-y-2 rounded-lg border p-3">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">Comment on</span>
            <NativeSelect aria-label="Comment on" className="h-8 flex-1" value={field} onChange={(e) => setField(e.target.value)}>
              {fields.map((f) => <option key={f.value} value={f.value}>{f.label}</option>)}
            </NativeSelect>
          </div>
          <Textarea rows={3} maxLength={5000} value={body} onChange={(e) => setBody(e.target.value)} aria-label="Comment"
                    placeholder="Point out a problem or leave a note. LaTeX works: $x^2$"
                    onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && body.trim()) add.mutate() }} />
          <div className="flex justify-end">
            <Button size="sm" disabled={!body.trim()} loading={add.isPending} onClick={() => add.mutate()}>Add comment</Button>
          </div>
        </div>
      )}

      {activity.isPending ? <Skeleton className="h-40" /> : (
        <ol className="space-y-3" aria-label="Timeline">
          {[...(activity.data ?? [])].reverse().map((a) => {
            const k = KIND[a.kind] ?? { label: a.kind.toLowerCase(), icon: MessageSquare }
            const isComment = a.kind === 'COMMENT' || a.kind === 'CHANGES_REQUESTED'
            const resolved = !!a.resolvedAt
            return (
              <li key={a.id} className={cn('flex gap-3', resolved && 'opacity-60')}>
                <k.icon className={cn('text-muted-foreground mt-0.5 size-4 shrink-0', k.tone)} />
                <div className="min-w-0 flex-1 text-sm">
                  <p>
                    {a.kind !== 'OVERDUE' && <strong>{a.actorName ?? 'System'} </strong>}{k.label}
                    {a.version != null && a.kind !== 'COMMENT' && <span className="text-muted-foreground"> · v{a.version}</span>}
                    {a.field && <Badge variant="outline" className="ml-2 font-mono text-[10px]">{fields.find((f) => f.value === a.field)?.label ?? a.field}</Badge>}
                  </p>
                  {a.body && <MathText text={a.body} className={cn('bg-muted/50 mt-1 rounded-md px-2.5 py-1.5', isComment && !resolved && 'border-l-2 border-warning')} />}
                  {a.kind === 'PUBLISHED' && a.meta && (
                    <p className="text-muted-foreground text-xs">
                      {Number(a.meta.draftTestsUpdated ?? 0)} draft test(s) and {Number(a.meta.liveTestsUpdated ?? 0)} published test(s) moved;
                      {' '}{Number(a.meta.liveTestsKept ?? 0)} kept their version
                    </p>
                  )}
                  <p className="text-muted-foreground text-xs" title={formatDateTime(a.createdAt)}>
                    {formatRelative(a.createdAt)}
                    {resolved && <> · resolved by {a.resolvedByName ?? 'someone'}</>}
                    {isComment && canComment && (
                      <button type="button" className="text-primary ml-2 hover:underline" disabled={resolve.isPending}
                              onClick={() => resolve.mutate({ id: a.id, resolved: !resolved })}>
                        {resolved ? 'Reopen' : <><CircleCheck className="mr-0.5 inline size-3" />Resolve</>}
                      </button>
                    )}
                  </p>
                </div>
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}

function ReviewAssignment({ question }: { question: Question }) {
  const qc = useQueryClient()
  const canAssign = question.actions.includes('ASSIGN')
  const reviewers = useQuery({ queryKey: contentKeys.reviewers(question.id), queryFn: () => contentApi.reviewers(question.id), enabled: canAssign })
  const assign = useMutation({
    mutationFn: (id: string) => contentApi.assign(question.id, id || null),
    onSuccess: (q) => {
      qc.setQueryData(adminKeys.question(q.id), q)
      void qc.invalidateQueries({ queryKey: contentKeys.activity(q.id) })
      toast.success(q.review?.reviewerName ? `Assigned to ${q.review.reviewerName}` : 'Unassigned')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const r = question.review!
  return (
    <div className={cn('rounded-lg border p-3 text-sm', r.overdue && 'border-destructive/50 bg-destructive/5')}>
      <p>
        <span className="text-muted-foreground">Submitted by</span> {r.submittedByName ?? '—'}
        <span className="text-muted-foreground"> · due </span>
        <span className={cn(r.overdue && 'text-destructive font-medium')} title={formatDateTime(r.dueAt)}>
          {r.overdue ? `overdue (${formatRelative(r.dueAt)})` : formatRelative(r.dueAt)}
        </span>
      </p>
      {canAssign ? (
        <div className="mt-2 flex items-center gap-2">
          <span className="text-muted-foreground">Reviewer</span>
          <NativeSelect aria-label="Reviewer" className="h-8 flex-1" value={r.reviewerId ?? ''} disabled={assign.isPending}
                        onChange={(e) => assign.mutate(e.target.value)}>
            <option value="">Unassigned</option>
            {reviewers.data?.map((u) => <option key={u.id} value={u.id}>{u.name} ({u.openReviews} open)</option>)}
            {r.reviewerId && !reviewers.data?.some((u) => u.id === r.reviewerId) && <option value={r.reviewerId}>{r.reviewerName}</option>}
          </NativeSelect>
        </div>
      ) : (
        <p className="mt-1"><span className="text-muted-foreground">Reviewer</span> {r.reviewerName ?? 'not assigned yet'}</p>
      )}
    </div>
  )
}
