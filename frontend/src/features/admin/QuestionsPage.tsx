import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { Archive, Pencil, Plus, Rocket, Send, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, useAdminQuestions } from '@/api/admin'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { MathText } from '@/components/common/MathText'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatNumber } from '@/lib/format'
import type { Difficulty, Language, QuestionFilter, QuestionStatus, SourceType } from '@/types/admin'
import type { QuestionType } from '@/types/exam'
import { CatalogPicker } from './components/CatalogPicker'
import { ImportDialog } from './components/ImportDialog'
import { contentApi } from '@/api/content'
import type { BulkAction } from '@/api/types'
import { plural } from '@/lib/format'
import {
  DIFFICULTIES, LANGUAGE_LABEL, QUESTION_STATUS, QUESTION_STATUS_LABEL, QUESTION_STATUSES, QUESTION_TYPES, SOURCE_TYPES, TYPE_LABEL,
  titleCase,
} from './labels'

const KEYS = ['examId', 'subjectId', 'chapterId', 'topicId', 'type', 'difficulty', 'status', 'q', 'mine', 'live', 'translated', 'sourceType', 'page'] as const

export default function QuestionsPage() {
  const [params, setParams] = useSearchParams()
  const filter: QuestionFilter = {
    examId: params.get('examId') ?? undefined,
    subjectId: params.get('subjectId') ?? undefined,
    chapterId: params.get('chapterId') ?? undefined,
    topicId: params.get('topicId') ?? undefined,
    type: (params.get('type') as QuestionType) || undefined,
    difficulty: (params.get('difficulty') as Difficulty) || undefined,
    status: (params.get('status') as QuestionStatus) || undefined,
    q: params.get('q') ?? undefined,
    mine: params.get('mine') === 'true' || undefined,
    live: params.get('live') === 'true' || undefined,
    translated: (params.get('translated') as Language) || undefined,
    sourceType: (params.get('sourceType') as SourceType) || undefined,
    page: Number(params.get('page') ?? 0),
    size: 20,
  }
  const update = (patch: Partial<Record<(typeof KEYS)[number], string | undefined>>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    if (!('page' in patch)) next.delete('page')
    setParams(next, { replace: true })
  }

  // Debounced text search.
  const [text, setText] = useState(filter.q ?? '')
  useEffect(() => {
    const t = setTimeout(() => { if ((filter.q ?? '') !== text.trim()) update({ q: text.trim() || undefined }) }, 350)
    return () => clearTimeout(t)
  }, [text])

  const questions = useAdminQuestions(filter)
  const qc = useQueryClient()
  const archive = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => adminApi.archiveQuestion(id, reason),
    onSuccess: () => { toast.success('Question archived'); void qc.invalidateQueries({ queryKey: ['admin', 'questions'] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const [importOpen, setImportOpen] = useState(false)
  const confirm = useConfirm()
  const [selected, setSelected] = useState<string[]>([])
  const rows = questions.data?.content ?? []
  const bulk = useMutation({
    mutationFn: ({ action, comment }: { action: BulkAction; comment?: string }) => contentApi.bulk(action, selected, comment),
    onSuccess: (r) => {
      setSelected([])
      void qc.invalidateQueries({ queryKey: ['admin', 'questions'] })
      if (r.failed.length === 0) toast.success(`${plural(r.succeeded, 'question')} done`)
      else toast.warning(`${r.succeeded} done, ${r.failed.length} not possible`, {
        description: r.failed.slice(0, 3).map((f) => f.message).join(' · '), duration: 10_000,
      })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <>
      <PageHeader title="Question bank" description="Search, create and import questions. Every save is a new version."
                  actions={
                    <div className="flex gap-2">
                      <Button variant="outline" onClick={() => setImportOpen(true)}><Upload /> Import</Button>
                      <Button asChild><Link to="/admin/questions/new"><Plus /> New question</Link></Button>
                    </div>
                  } />

      <Card className="mb-4 py-4">
        <CardContent className="space-y-3">
          <CatalogPicker value={filter} onChange={(v) => update({
            examId: v.examId, subjectId: v.subjectId, chapterId: v.chapterId, topicId: v.topicId,
          })} />
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[1fr_170px_140px_170px_150px_150px]">
            <Input placeholder="Search question text…" value={text} onChange={(e) => setText(e.target.value)} aria-label="Search" />
            <NativeSelect aria-label="Type" value={filter.type ?? ''} onChange={(e) => update({ type: e.target.value })}>
              <option value="">All types</option>
              {QUESTION_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </NativeSelect>
            <NativeSelect aria-label="Difficulty" value={filter.difficulty ?? ''} onChange={(e) => update({ difficulty: e.target.value })}>
              <option value="">Any difficulty</option>
              {DIFFICULTIES.map((d) => <option key={d} value={d}>{titleCase(d)}</option>)}
            </NativeSelect>
            <NativeSelect aria-label="Status" value={filter.status ?? ''} onChange={(e) => update({ status: e.target.value })}>
              <option value="">Any status (not archived)</option>
              {QUESTION_STATUSES.map((st) => <option key={st} value={st}>{QUESTION_STATUS_LABEL[st]}</option>)}
            </NativeSelect>
            <NativeSelect aria-label="Source" value={filter.sourceType ?? ''} onChange={(e) => update({ sourceType: e.target.value })}>
              <option value="">Any source</option>
              {SOURCE_TYPES.map((st) => <option key={st.value} value={st.value}>{st.label}</option>)}
            </NativeSelect>
            <NativeSelect aria-label="Translation" value={filter.translated ?? ''} onChange={(e) => update({ translated: e.target.value })}>
              <option value="">Any language</option>
              <option value="HI">Has Hindi</option>
              <option value="EN">Has English</option>
            </NativeSelect>
          </div>
          <div className="flex flex-wrap gap-4">
            <label className="flex items-center gap-2 text-sm whitespace-nowrap">
              <Checkbox checked={!!filter.mine} onCheckedChange={(v) => update({ mine: v ? 'true' : undefined })} /> Only mine
            </label>
            <label className="flex items-center gap-2 text-sm whitespace-nowrap">
              <Checkbox checked={!!filter.live} onCheckedChange={(v) => update({ live: v ? 'true' : undefined })} /> Usable in tests (live)
            </label>
          </div>
        </CardContent>
      </Card>

      {questions.isError ? <ErrorState error={questions.error} onRetry={() => questions.refetch()} /> :
        questions.isPending ? <Skeleton className="h-96 rounded-xl" /> :
          questions.data.content.length === 0 ? (
            <EmptyState title="No questions found" description="Change the filters, or create a question." />
          ) : (
            <>
              <p className="text-muted-foreground mb-2 text-sm">{formatNumber(questions.data.totalElements, 0)} questions</p>
              <Card className="py-0">
                <CardContent className="overflow-x-auto px-0">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-b text-left">
                        <th className="w-10 px-4 py-3">
                          <Checkbox aria-label="Select all on this page" checked={rows.length > 0 && rows.every((r) => selected.includes(r.id))}
                                    onCheckedChange={(v) => setSelected(v ? rows.map((r) => r.id) : [])} />
                        </th>
                        <th className="px-3 py-3 font-medium">Question</th>
                        <th className="px-3 py-3 font-medium">Topic</th>
                        <th className="px-3 py-3 font-medium">Type</th>
                        <th className="px-3 py-3 font-medium">Marks</th>
                        <th className="px-3 py-3 font-medium">Status</th>
                        <th className="px-3 py-3 font-medium">Created</th>
                        <th className="px-4 py-3" />
                      </tr>
                    </thead>
                    <tbody>
                      {questions.data.content.map((q) => (
                        <tr key={q.id} className="hover:bg-muted/40 border-b last:border-0">
                          <td className="px-4 py-2.5">
                            <Checkbox aria-label="Select" checked={selected.includes(q.id)}
                                      onCheckedChange={(v) => setSelected((cur) => (v ? [...cur, q.id] : cur.filter((x) => x !== q.id)))} />
                          </td>
                          <td className="max-w-md px-3 py-2.5">
                            <Link to={`/admin/questions/${q.id}`} className="line-clamp-2 hover:underline"><MathText as="span" text={q.textPreview || '(no text)'} /></Link>
                            <div className="mt-1 flex flex-wrap gap-1">
                              {(q.languages ?? []).length > 1 && <Badge variant="outline">{(q.languages ?? []).map((l) => LANGUAGE_LABEL[l]).join(' + ')}</Badge>}
                              {q.tags.map((t) => <Badge key={t} variant="muted">{t}</Badge>)}
                            </div>
                          </td>
                          <td className="px-3 py-2.5">
                            <p>{q.topic.topicName}</p>
                            <p className="text-muted-foreground text-xs">{q.topic.subjectName} · {q.topic.chapterName}</p>
                          </td>
                          <td className="px-3 py-2.5 whitespace-nowrap">
                            {TYPE_LABEL[q.type]}
                            {q.difficulty && <p className="text-muted-foreground text-xs">{titleCase(q.difficulty)}</p>}
                          </td>
                          <td className="px-3 py-2.5 whitespace-nowrap tabular-nums">+{formatNumber(q.marks)} / −{formatNumber(q.negativeMarks)}</td>
                          <td className="px-3 py-2.5">
                            <Badge variant={QUESTION_STATUS[q.status]}>{QUESTION_STATUS_LABEL[q.status]}</Badge>
                            <p className="text-muted-foreground mt-0.5 text-xs whitespace-nowrap">
                              v{q.currentVersion}
                              {q.publishedVersion != null && q.status !== 'PUBLISHED' && q.status !== 'ARCHIVED' && ` · live v${q.publishedVersion}`}
                            </p>
                          </td>
                          <td className="text-muted-foreground px-3 py-2.5 whitespace-nowrap">{formatDate(q.createdAt)}</td>
                          <td className="px-4 py-2.5">
                            <div className="flex justify-end gap-1">
                              <Button variant="ghost" size="icon" asChild aria-label="Edit"><Link to={`/admin/questions/${q.id}`}><Pencil /></Link></Button>
                              {q.status !== 'ARCHIVED' && (
                                <Button variant="ghost" size="icon" aria-label="Archive" disabled={archive.isPending}
                                        onClick={async () => {
                                          const r = await confirm({ title: 'Archive this question?', destructive: true, reason: 'required',
                                            description: 'It disappears from the bank and pickers but stays in existing tests.', confirmText: 'Archive' })
                                          if (r) archive.mutate({ id: q.id, reason: r.reason })
                                        }}>
                                  <Archive />
                                </Button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
              <div className="mt-4">
                <Pagination page={questions.data.page} totalPages={questions.data.totalPages}
                            onChange={(p) => update({ page: String(p) })} />
              </div>
            </>
          )}

      {selected.length > 0 && (
        <div className="bg-background/95 sticky bottom-0 z-20 -mx-4 mt-6 flex flex-wrap items-center gap-2 border-t px-4 py-3 backdrop-blur sm:-mx-6 sm:px-6"
             role="region" aria-label="Bulk actions">
          <span className="text-sm font-medium">{plural(selected.length, 'question')} selected</span>
          <Button variant="outline" size="sm" onClick={() => setSelected([])}>Clear</Button>
          <div className="ml-auto flex flex-wrap gap-2">
            <Button size="sm" variant="outline" loading={bulk.isPending} onClick={() => bulk.mutate({ action: 'SUBMIT' })}>
              <Send /> Submit for review
            </Button>
            <Button size="sm" variant="outline" loading={bulk.isPending} onClick={() => bulk.mutate({ action: 'PUBLISH' })}>
              <Rocket /> Publish approved
            </Button>
            <Button size="sm" variant="ghost" className="text-destructive" loading={bulk.isPending}
                    onClick={async () => {
                      const r = await confirm({ title: `Archive ${plural(selected.length, 'question')}?`, destructive: true, reason: 'required',
                        description: 'They disappear from the bank and pickers. Tests that use them keep working.', confirmText: 'Archive' })
                      if (r) bulk.mutate({ action: 'ARCHIVE', comment: r.reason })
                    }}>
              <Archive /> Archive
            </Button>
          </div>
        </div>
      )}

      <ImportDialog open={importOpen} onOpenChange={setImportOpen} />
    </>
  )
}
