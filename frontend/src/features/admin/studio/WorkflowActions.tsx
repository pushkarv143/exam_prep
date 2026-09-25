import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, Check, Hand, Rocket, Send, Undo2 } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys } from '@/api/admin'
import { contentApi, contentKeys, type PublishOutcome } from '@/api/content'
import { usePermissions } from '@/api/portal'
import type { PublishResult } from '@/api/types'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/errors'
import { plural } from '@/lib/format'
import type { Question } from '@/types/admin'

/** "Published v3: 2 draft tests moved; 1 published test kept v2 (the answer changed)." */
export function publishSummary(r: PublishResult): string {
  const parts = [`Published v${r.version}.`]
  if (r.draftTestsUpdated) parts.push(`${plural(r.draftTestsUpdated, 'draft test')} now use${r.draftTestsUpdated === 1 ? 's' : ''} it.`)
  if (r.liveTestsUpdated) parts.push(`${plural(r.liveTestsUpdated, 'published test')} got the wording fix.`)
  if (r.liveTestsKept) parts.push(`${plural(r.liveTestsKept, 'published test')} kept v${r.previousVersion ?? '?'} (scoring changed or not propagated).`)
  return parts.join(' ')
}

type DialogKind = 'submit' | 'changes' | 'approve' | 'publish' | null

/**
 * The workflow buttons the current user may press, driven by `question.actions` from the
 * server. Saving comes first: workflow actions are disabled while the form has unsaved changes.
 */
export function WorkflowActions({ question, dirty }: { question: Question; dirty: boolean }) {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const { can } = usePermissions()
  const [open, setOpen] = useState<DialogKind>(null)
  const actions = new Set(question.actions)

  const done = (q: Question, message: string) => {
    qc.setQueryData(adminKeys.question(q.id), q)
    void qc.invalidateQueries({ queryKey: ['admin', 'questions'] })
    void qc.invalidateQueries({ queryKey: contentKeys.activity(q.id) })
    void qc.invalidateQueries({ queryKey: contentKeys.versions(q.id) })
    setOpen(null)
    toast.success(message, { duration: 7000 })
  }
  const run = useMutation({
    mutationFn: (fn: () => Promise<{ q: Question; message: string }>) => fn(),
    onSuccess: ({ q, message }) => done(q, message),
    onError: (e) => toast.error(errorMessage(e), { duration: 8000 }),
  })
  const outcome = (o: PublishOutcome, fallback: string) => ({ q: o.question, message: o.published ? publishSummary(o.published) : fallback })
  const disabled = dirty || run.isPending
  const hint = dirty ? 'Save your changes first' : undefined

  return (
    <>
      {actions.has('SUBMIT') && (
        <Button type="button" disabled={disabled} title={hint} onClick={() => setOpen('submit')}><Send /> Submit for review</Button>
      )}
      {actions.has('CLAIM') && (
        <Button type="button" variant="outline" disabled={disabled}
                onClick={() => run.mutate(async () => ({ q: await contentApi.claim(question.id), message: 'The review is yours now' }))}>
          <Hand /> Take this review
        </Button>
      )}
      {actions.has('REQUEST_CHANGES') && (
        <Button type="button" variant="outline" disabled={disabled} title={hint} onClick={() => setOpen('changes')}><Undo2 /> Request changes</Button>
      )}
      {actions.has('APPROVE') && (
        <Button type="button" variant="success" disabled={disabled} title={hint} onClick={() => setOpen('approve')}><Check /> Approve</Button>
      )}
      {actions.has('PUBLISH') && (
        <Button type="button" disabled={disabled} title={hint} onClick={() => setOpen('publish')}><Rocket /> Publish</Button>
      )}
      {actions.has('RESTORE') && (
        <Button type="button" variant="outline" disabled={run.isPending}
                onClick={() => run.mutate(async () => ({ q: await contentApi.restore(question.id), message: 'Question restored' }))}>
          <ArchiveRestore /> Restore
        </Button>
      )}
      {actions.has('ARCHIVE') && (
        <Button type="button" variant="ghost" size="icon" aria-label="Archive" disabled={run.isPending}
                onClick={async () => {
                  const r = await confirm({ title: 'Archive this question?', destructive: true, reason: 'required', confirmText: 'Archive',
                    description: 'It disappears from the bank and the pickers. Tests that already use it keep working.' })
                  if (r) run.mutate(async () => {
                    await adminApi.archiveQuestion(question.id, r.reason)
                    return { q: await adminApi.question(question.id), message: 'Question archived' }
                  })
                }}>
          <Archive />
        </Button>
      )}

      <SubmitDialog open={open === 'submit'} onClose={() => setOpen(null)} question={question} pending={run.isPending}
                    onSubmit={(assigneeId, note) => run.mutate(async () => {
                      const q = await contentApi.submit(question.id, { assigneeId: assigneeId || undefined, note: note || undefined })
                      return { q, message: q.review?.reviewerName ? `Sent to ${q.review.reviewerName} for review` : 'Sent for review' }
                    })} />
      <CommentDialog open={open === 'changes'} onClose={() => setOpen(null)} pending={run.isPending} required
                     title="Request changes" confirmText="Send back"
                     description="The author sees your comment and the question returns to them. Field comments from the Review tab stay attached."
                     onConfirm={(comment) => run.mutate(async () => ({ q: await contentApi.requestChanges(question.id, comment), message: 'Sent back to the author' }))} />
      <ApproveDialog open={open === 'approve'} onClose={() => setOpen(null)} pending={run.isPending} canPublish={can('question.publish')}
                     onConfirm={(comment, publish) => run.mutate(async () =>
                       outcome(await contentApi.approve(question.id, comment, publish), 'Approved. A publisher can now make it live.'))} />
      <PublishDialog open={open === 'publish'} onClose={() => setOpen(null)} pending={run.isPending} question={question}
                     onConfirm={(propagate) => run.mutate(async () => outcome(await contentApi.publish(question.id, propagate), 'Published'))} />
    </>
  )
}

function SubmitDialog({ open, onClose, question, pending, onSubmit }: {
  open: boolean; onClose: () => void; question: Question; pending: boolean; onSubmit: (assigneeId: string, note: string) => void
}) {
  const reviewers = useQuery({ queryKey: contentKeys.reviewers(question.id), queryFn: () => contentApi.reviewers(question.id), enabled: open })
  const [assignee, setAssignee] = useState('')
  const [note, setNote] = useState('')
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Submit for review</DialogTitle>
          <DialogDescription>A reviewer checks the question before it can be published. Review time is shown in the queue.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="submit-reviewer">Reviewer</Label>
          <NativeSelect id="submit-reviewer" value={assignee} onChange={(e) => setAssignee(e.target.value)}>
            <option value="">Pick automatically (least busy)</option>
            {reviewers.data?.map((r) => <option key={r.id} value={r.id}>{r.name} · {plural(r.openReviews, 'open review')}</option>)}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="submit-note">Note for the reviewer <span className="text-muted-foreground">(optional)</span></Label>
          <Textarea id="submit-note" rows={3} maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)}
                    placeholder="e.g. Please check the range of accepted answers." />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button loading={pending} onClick={() => onSubmit(assignee, note)}><Send /> Submit</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function CommentDialog({ open, onClose, pending, onConfirm, title, description, confirmText, required }: {
  open: boolean; onClose: () => void; pending: boolean; onConfirm: (comment: string) => void; title: string
  description: string; confirmText: string; required?: boolean
}) {
  const [comment, setComment] = useState('')
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <Textarea rows={4} maxLength={5000} value={comment} onChange={(e) => setComment(e.target.value)} aria-label="Comment" autoFocus
                  placeholder="What should change?" />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={required && comment.trim().length < 3} loading={pending} onClick={() => onConfirm(comment.trim())}>{confirmText}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ApproveDialog({ open, onClose, pending, canPublish, onConfirm }: {
  open: boolean; onClose: () => void; pending: boolean; canPublish: boolean; onConfirm: (comment: string, publish: boolean) => void
}) {
  const [comment, setComment] = useState('')
  const [publish, setPublish] = useState(false)
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Approve this question</DialogTitle>
          <DialogDescription>Approving confirms the question, answer key, solution and translation are correct.</DialogDescription>
        </DialogHeader>
        <Textarea rows={3} maxLength={5000} value={comment} onChange={(e) => setComment(e.target.value)} aria-label="Comment"
                  placeholder="Comment (optional)" />
        {canPublish && (
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={publish} onCheckedChange={(v) => setPublish(v === true)} /> Publish it right away
          </label>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant="success" loading={pending} onClick={() => onConfirm(comment.trim(), publish)}><Check /> Approve</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PublishDialog({ open, onClose, pending, question, onConfirm }: {
  open: boolean; onClose: () => void; pending: boolean; question: Question; onConfirm: (propagate: boolean) => void
}) {
  const [propagate, setPropagate] = useState(true)
  const revision = question.publishedVersion != null
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Publish v{question.currentVersion}</DialogTitle>
          <DialogDescription>
            {revision
              ? `v${question.publishedVersion} is live now. New tests will use v${question.currentVersion}; draft tests move to it automatically.`
              : 'The question becomes available in the test builder and the generator.'}
          </DialogDescription>
        </DialogHeader>
        {revision && question.usedInPublishedTests > 0 && (
          <label className="flex items-start gap-2 text-sm">
            <Checkbox className="mt-0.5" checked={propagate} onCheckedChange={(v) => setPropagate(v === true)} />
            <span>
              Also update the {plural(question.usedInPublishedTests, 'published test')} that use this question
              <span className="text-muted-foreground block text-xs">
                Only if the change keeps scoring identical (wording, solution, translation). If the answer, type or options
                changed, published tests keep the version students were tested on.
              </span>
            </span>
          </label>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button loading={pending} onClick={() => onConfirm(propagate)}><Rocket /> Publish</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
