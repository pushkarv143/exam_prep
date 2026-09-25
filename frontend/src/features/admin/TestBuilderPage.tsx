import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  AlertTriangle, ArrowDown, ArrowLeft, ArrowUp, BarChart3, CheckCircle2, GripVertical, Pencil, Plus, Settings, Sparkles,
  Trash2, Wand2, XCircle,
} from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys, toastAdminError } from '@/api/admin'
import { contentApi } from '@/api/content'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { MathText } from '@/components/common/MathText'
import { ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatDuration, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { AdminTest, TestQuestion, TestSection } from '@/types/admin'
import { GeneratePatternDialog, GenerateSectionDialog } from './components/GenerateDialogs'
import { QuestionPickerDialog } from './components/QuestionPickerDialog'
import { SectionDialog } from './components/SectionDialog'
import { TestSettingsDialog } from './components/TestSettingsDialog'
import { TEST_STATUS, TYPE_LABEL, titleCase } from './labels'

type SectionModal = { kind: 'edit' | 'add' | 'generate'; section: TestSection } | { kind: 'new' } | null

export default function TestBuilderPage() {
  const { testId = '' } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const detail = useQuery({ queryKey: adminKeys.test(testId), queryFn: () => adminApi.test(testId) })
  const validation = useQuery({ queryKey: adminKeys.validation(testId), queryFn: () => adminApi.validateTest(testId) })
  // Tests pin question versions; a newer published version is offered, never applied silently.
  const outdated = (detail.data?.sections ?? []).flatMap((s) => s.questions)
    .filter((tq) => tq.question?.publishedVersion != null && tq.question.publishedVersion > tq.questionVersion).length
  const updateVersions = useMutation({
    mutationFn: () => contentApi.updateTestVersions(testId),
    onSuccess: (r) => {
      toast.success(r.updated ? `${r.updated} question(s) now use their latest version` : 'Already up to date')
      void qc.invalidateQueries({ queryKey: adminKeys.test(testId) })
      void qc.invalidateQueries({ queryKey: adminKeys.validation(testId) })
    },
    onError: toastAdminError,
  })
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [patternOpen, setPatternOpen] = useState(false)
  const [modal, setModal] = useState<SectionModal>(null)
  const [editing, setEditing] = useState<TestQuestion | null>(null)
  const confirm = useConfirm()

  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'test', testId] })
  const action = useMutation({
    mutationFn: ({ a, reason }: { a: 'publish' | 'unpublish' | 'archive'; reason?: string }) => adminApi.testAction(testId, a, reason),
    onSuccess: (_, { a }) => {
      toast.success(a === 'publish' ? 'Test published' : a === 'unpublish' ? 'Moved back to draft' : 'Test archived')
      void refresh()
      void qc.invalidateQueries({ queryKey: ['admin', 'tests'] })
    },
    onError: toastAdminError,
  })
  const remove = useMutation({
    mutationFn: (reason: string) => adminApi.deleteTest(testId, reason),
    onSuccess: () => { toast.success('Test deleted'); void qc.invalidateQueries({ queryKey: ['admin', 'tests'] }); navigate('/admin/tests') },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const deleteSection = useMutation({
    mutationFn: ({ sectionId, reason }: { sectionId: string; reason: string }) => adminApi.deleteSection(testId, sectionId, reason),
    onSuccess: () => { toast.success('Section deleted'); void refresh() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const removeQuestion = useMutation({
    mutationFn: (tqId: string) => adminApi.removeTestQuestion(testId, tqId),
    onSuccess: () => void refresh(),
    onError: (e) => toast.error(errorMessage(e)),
  })

  const inTest = useMemo(() => new Set(detail.data?.sections.flatMap((s) => s.questions.map((q) => q.questionId)) ?? []), [detail.data])

  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
  if (detail.isPending) return <PageLoader />
  const { test, sections } = detail.data
  const editable = test.status === 'DRAFT'
  let number = 0

  return (
    <>
      <PageHeader
        title={test.title}
        description={`${titleCase(test.pattern)} · ${formatDuration(test.durationMinutes)} · ${test.totalQuestions} questions · ${formatNumber(test.totalMarks)} marks`}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" asChild><Link to="/admin/tests"><ArrowLeft /> Tests</Link></Button>
            <Button variant="outline" onClick={() => setSettingsOpen(true)}><Settings /> Settings</Button>
            {test.status !== 'DRAFT' && <Button variant="outline" asChild><Link to={`/admin/tests/${test.id}/stats`}><BarChart3 /> Stats</Link></Button>}
            {editable && (
              <Button disabled={!validation.data?.publishable} loading={action.isPending && action.variables?.a === 'publish'}
                      onClick={async () => {
                        const r = await confirm({ title: 'Publish "' + test.title + '"?', reason: 'optional', confirmText: 'Publish',
                          description: 'Students can see and attempt it once it opens. This may need a second person to approve.' })
                        if (r) action.mutate({ a: 'publish', reason: r.reason })
                      }}>Publish</Button>
            )}
            {test.status === 'PUBLISHED' && (
              <Button variant="outline" loading={action.isPending && action.variables?.a === 'unpublish'} onClick={() => action.mutate({ a: 'unpublish' })}>Unpublish</Button>
            )}
            {test.status !== 'ARCHIVED' && test.status !== 'DRAFT' && (
              <Button variant="outline" loading={action.isPending && action.variables?.a === 'archive'}
                      onClick={async () => {
                        const r = await confirm({ title: 'Archive this test?', destructive: true, reason: 'required',
                          description: 'Students can no longer start it. Results already taken are kept.', confirmText: 'Archive' })
                        if (r) action.mutate({ a: 'archive', reason: r.reason })
                      }}>Archive</Button>
            )}
            {editable && (
              <Button variant="ghost" size="icon" aria-label="Delete test" loading={remove.isPending}
                      onClick={async () => {
                        const r = await confirm({ title: 'Delete this draft test?', destructive: true, reason: 'required',
                          description: 'The test and its sections are removed permanently.', confirmText: 'Delete' })
                        if (r) remove.mutate(r.reason)
                      }}><Trash2 /></Button>
            )}
          </div>
        }
      />

      <div className="mb-6 flex flex-wrap items-center gap-2 text-sm">
        <Badge variant={TEST_STATUS[test.status]}>{titleCase(test.status)}</Badge>
        {test.free && <Badge variant="success">Free</Badge>}
        <span className="text-muted-foreground">
          {test.startAt || test.endAt ? `${test.startAt ? formatDateTime(test.startAt) : 'Now'} – ${test.endAt ? formatDateTime(test.endAt) : 'open'}` : 'No time window'}
          {' · '}{test.maxAttempts} attempt(s)
        </span>
        {!editable && <span className="text-muted-foreground">· Unpublish the test to change its questions.</span>}
        {outdated > 0 && (
          <span className="text-warning ml-auto flex items-center gap-2">
            {outdated} question{outdated > 1 ? 's have' : ' has'} a newer published version
            {editable && (
              <Button size="sm" variant="outline" loading={updateVersions.isPending} onClick={() => updateVersions.mutate()}>
                Use latest versions
              </Button>
            )}
          </span>
        )}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          {sections.map((s) => {
            const start = number
            number += s.questions.length
            return (
              <SectionCard key={s.id} testId={test.id} section={s} startNumber={start} editable={editable}
                           onEdit={() => setModal({ kind: 'edit', section: s })}
                           onAdd={() => setModal({ kind: 'add', section: s })}
                           onGenerate={() => setModal({ kind: 'generate', section: s })}
                           onDelete={async () => {
                             const r = await confirm({ title: 'Delete section "' + s.name + '"?', destructive: true, reason: 'required',
                               description: 'Its questions are removed from this test (they stay in the bank).', confirmText: 'Delete section' })
                             if (r) deleteSection.mutate({ sectionId: s.id, reason: r.reason })
                           }}
                           onEditQuestion={setEditing}
                           onRemoveQuestion={(tq) => removeQuestion.mutate(tq.id)} />
            )
          })}
          {editable && (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setModal({ kind: 'new' })}><Plus /> Add section</Button>
              {test.pattern !== 'CUSTOM' && <Button variant="outline" onClick={() => setPatternOpen(true)}><Wand2 /> Generate full paper</Button>}
            </div>
          )}
        </div>

        <div className="xl:sticky xl:top-24 xl:self-start">
          <ValidationPanel report={validation.data} loading={validation.isFetching} />
        </div>
      </div>

      <TestSettingsDialog test={test} open={settingsOpen} onOpenChange={setSettingsOpen} />
      <GeneratePatternDialog test={test} open={patternOpen} onOpenChange={setPatternOpen} />
      <SectionDialog test={test} open={modal?.kind === 'edit' || modal?.kind === 'new'}
                     section={modal?.kind === 'edit' ? modal.section : undefined}
                     nextOrder={sections.length} onOpenChange={(o) => !o && setModal(null)} />
      {modal?.kind === 'add' && (
        <QuestionPickerDialog test={test} section={modal.section} inTest={inTest} open onOpenChange={(o) => !o && setModal(null)} />
      )}
      {modal?.kind === 'generate' && (
        <GenerateSectionDialog test={test} section={modal.section} open onOpenChange={(o) => !o && setModal(null)} />
      )}
      <MarksDialog test={test} tq={editing} onClose={() => setEditing(null)} />
    </>
  )
}

function SectionCard({ testId, section, startNumber, editable, onEdit, onAdd, onGenerate, onDelete, onEditQuestion, onRemoveQuestion }: {
  testId: string; section: TestSection; startNumber: number; editable: boolean
  onEdit: () => void; onAdd: () => void; onGenerate: () => void; onDelete: () => void
  onEditQuestion: (tq: TestQuestion) => void; onRemoveQuestion: (tq: TestQuestion) => void
}) {
  const qc = useQueryClient()
  const [order, setOrder] = useState(section.questions.map((q) => q.id))
  const [dragId, setDragId] = useState<string | null>(null)
  useEffect(() => setOrder(section.questions.map((q) => q.id)), [section.questions])
  const byId = new Map(section.questions.map((q) => [q.id, q]))
  const serverOrder = section.questions.map((q) => q.id).join()

  const save = useMutation({
    mutationFn: (ids: string[]) => adminApi.reorder(testId, section.id, ids),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin', 'test', testId] }),
    onError: (e) => {
      toast.error(errorMessage(e))
      setOrder(section.questions.map((q) => q.id))
    },
  })
  const commit = (ids: string[]) => { if (ids.join() !== serverOrder) save.mutate(ids) }
  const move = (from: number, to: number) => {
    if (to < 0 || to >= order.length) return
    const next = [...order]
    const [id] = next.splice(from, 1)
    next.splice(to, 0, id)
    setOrder(next)
    return next
  }

  const target = section.targetCount
  return (
    <Card className="gap-3 py-4">
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-2">
        <div>
          <CardTitle className="text-base">{section.name}</CardTitle>
          <p className="text-muted-foreground mt-1 text-xs">
            {section.questionCount}{target ? ` / ${target}` : ''} questions · {formatNumber(section.sectionMarks)} marks
            {section.questionType && ` · ${TYPE_LABEL[section.questionType]}`}
            {section.defaultMarks != null && ` · +${formatNumber(section.defaultMarks)} / −${formatNumber(section.defaultNegativeMarks ?? 0)}`}
            {section.maxQuestionsToAttempt && ` · attempt any ${section.maxQuestionsToAttempt}`}
          </p>
        </div>
        {editable && (
          <div className="flex flex-wrap gap-1">
            <Button size="sm" variant="outline" onClick={onAdd}><Plus /> Questions</Button>
            <Button size="sm" variant="outline" onClick={onGenerate}><Sparkles /> Auto-fill</Button>
            <Button size="icon" variant="ghost" aria-label="Edit section" onClick={onEdit}><Pencil /></Button>
            <Button size="icon" variant="ghost" aria-label="Delete section" onClick={onDelete}><Trash2 /></Button>
          </div>
        )}
      </CardHeader>
      <CardContent className="px-0">
        {order.length === 0 ? (
          <p className="text-muted-foreground px-6 py-4 text-sm">No questions yet.</p>
        ) : (
          <ol className="divide-y border-y">
            {order.map((id, i) => {
              const tq = byId.get(id)
              if (!tq) return null
              return (
                <li key={id} draggable={editable}
                    onDragStart={(e) => { setDragId(id); e.dataTransfer.effectAllowed = 'move' }}
                    onDragOver={(e) => {
                      if (!dragId || dragId === id) return
                      e.preventDefault()
                      move(order.indexOf(dragId), i)
                    }}
                    onDrop={(e) => e.preventDefault()}
                    onDragEnd={() => { setDragId(null); commit(order) }}
                    className={cn('flex items-center gap-3 px-4 py-2 text-sm', dragId === id && 'bg-accent opacity-70')}>
                  {editable && <GripVertical className="text-muted-foreground size-4 shrink-0 cursor-grab" aria-hidden />}
                  <span className="w-8 shrink-0 font-semibold tabular-nums">{startNumber + i + 1}</span>
                  <div className="min-w-0 flex-1">
                    <Link to={`/admin/questions/${tq.questionId}`} className="line-clamp-1 hover:underline"><MathText as="span" text={tq.question?.textPreview || '(no text)'} /></Link>
                    <p className="text-muted-foreground text-xs">
                      {TYPE_LABEL[tq.question.type]} · {tq.question.topic.chapterName}{tq.question.difficulty ? ` · ${titleCase(tq.question.difficulty)}` : ''}
                      {' · '}<span title="The version this test uses">v{tq.questionVersion}</span>
                      {tq.question.publishedVersion != null && tq.question.publishedVersion > tq.questionVersion && (
                        <span className="text-warning"> (v{tq.question.publishedVersion} available)</span>
                      )}
                    </p>
                  </div>
                  <span className="shrink-0 tabular-nums">+{formatNumber(tq.marks)} / −{formatNumber(tq.negativeMarks)}{tq.partialMarking ? ' · partial' : ''}</span>
                  {editable && (
                    <div className="flex shrink-0">
                      <Button size="icon" variant="ghost" aria-label="Move up" disabled={i === 0 || save.isPending}
                              onClick={() => { const n = move(i, i - 1); if (n) commit(n) }}><ArrowUp /></Button>
                      <Button size="icon" variant="ghost" aria-label="Move down" disabled={i === order.length - 1 || save.isPending}
                              onClick={() => { const n = move(i, i + 1); if (n) commit(n) }}><ArrowDown /></Button>
                      <Button size="icon" variant="ghost" aria-label="Edit marks" onClick={() => onEditQuestion(tq)}><Pencil /></Button>
                      <Button size="icon" variant="ghost" aria-label="Remove from test" onClick={() => onRemoveQuestion(tq)}><Trash2 /></Button>
                    </div>
                  )}
                </li>
              )
            })}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}

function ValidationPanel({ report, loading }: { report?: { publishable: boolean; errors: string[]; warnings: string[] }; loading: boolean }) {
  return (
    <Card className="gap-3 py-4">
      <CardHeader><CardTitle className="flex items-center gap-2 text-base">Publish checklist {loading && <span className="text-muted-foreground text-xs font-normal">checking…</span>}</CardTitle></CardHeader>
      <CardContent className="space-y-3 text-sm">
        {!report ? <p className="text-muted-foreground">Checking…</p> : (
          <>
            {report.publishable ? (
              <p className="text-success flex items-center gap-2 font-medium"><CheckCircle2 className="size-4" /> Ready to publish</p>
            ) : (
              <p className="text-destructive flex items-center gap-2 font-medium"><XCircle className="size-4" /> Fix these before publishing</p>
            )}
            {report.errors.length > 0 && (
              <ul className="space-y-1.5">
                {report.errors.map((e) => <li key={e} className="text-destructive flex gap-2"><XCircle className="mt-0.5 size-3.5 shrink-0" />{e}</li>)}
              </ul>
            )}
            {report.warnings.length > 0 && (
              <ul className="space-y-1.5">
                {report.warnings.map((w) => <li key={w} className="flex gap-2 text-amber-700 dark:text-amber-300"><AlertTriangle className="mt-0.5 size-3.5 shrink-0" />{w}</li>)}
              </ul>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function MarksDialog({ test, tq, onClose }: { test: AdminTest; tq: TestQuestion | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [marks, setMarks] = useState('')
  const [negative, setNegative] = useState('')
  const [partial, setPartial] = useState(false)
  useEffect(() => {
    if (tq) { setMarks(String(tq.marks)); setNegative(String(tq.negativeMarks)); setPartial(tq.partialMarking) }
  }, [tq])
  const valid = (v: string) => /^\d+(\.\d+)?$/.test(v) && +v <= 100
  const save = useMutation({
    mutationFn: () => adminApi.updateTestQuestion(test.id, tq!.id, { marks: Number(marks), negativeMarks: Number(negative), partialMarking: partial }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['admin', 'test', test.id] }); toast.success('Marks updated'); onClose() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <Dialog open={!!tq} onOpenChange={(o) => !o && !save.isPending && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Marks for this question</DialogTitle></DialogHeader>
        <div className="grid grid-cols-2 gap-3">
          <div className="grid gap-2"><Label htmlFor="m-marks">Marks</Label><Input id="m-marks" inputMode="decimal" value={marks} onChange={(e) => setMarks(e.target.value)} aria-invalid={!valid(marks)} /></div>
          <div className="grid gap-2"><Label htmlFor="m-neg">Negative</Label><Input id="m-neg" inputMode="decimal" value={negative} onChange={(e) => setNegative(e.target.value)} aria-invalid={!valid(negative)} /></div>
        </div>
        {tq?.question.type === 'MULTIPLE_CORRECT' && (
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={partial} onCheckedChange={setPartial} /> Partial marking</label>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>Cancel</Button>
          <Button disabled={!valid(marks) || !valid(negative)} loading={save.isPending} onClick={() => save.mutate()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
