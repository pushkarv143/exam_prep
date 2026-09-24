import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { Archive, Pencil, Plus, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, useAdminQuestions } from '@/api/admin'
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
import type { Difficulty, QuestionFilter, QuestionStatus } from '@/types/admin'
import type { QuestionType } from '@/types/exam'
import { CatalogPicker } from './components/CatalogPicker'
import { ImportDialog } from './components/ImportDialog'
import { DIFFICULTIES, QUESTION_STATUS, QUESTION_TYPES, TYPE_LABEL, titleCase } from './labels'

const KEYS = ['examId', 'subjectId', 'chapterId', 'topicId', 'type', 'difficulty', 'status', 'q', 'mine', 'page'] as const

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
    mutationFn: adminApi.archiveQuestion,
    onSuccess: () => { toast.success('Question archived'); void qc.invalidateQueries({ queryKey: ['admin', 'questions'] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const [importOpen, setImportOpen] = useState(false)

  return (
    <>
      <PageHeader title="Question bank" description="Search, create and import questions"
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
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[1fr_180px_150px_150px_auto]">
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
              <option value="">Draft &amp; active</option>
              <option value="DRAFT">Draft</option>
              <option value="ACTIVE">Active</option>
              <option value="ARCHIVED">Archived</option>
            </NativeSelect>
            <label className="flex items-center gap-2 text-sm whitespace-nowrap">
              <Checkbox checked={!!filter.mine} onCheckedChange={(v) => update({ mine: v ? 'true' : undefined })} /> Only mine
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
                        <th className="px-4 py-3 font-medium">Question</th>
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
                          <td className="max-w-md px-4 py-2.5">
                            <Link to={`/admin/questions/${q.id}`} className="line-clamp-2 hover:underline"><MathText as="span" text={q.textPreview || '(no text)'} /></Link>
                            {q.tags.length > 0 && (
                              <div className="mt-1 flex flex-wrap gap-1">{q.tags.map((t) => <Badge key={t} variant="muted">{t}</Badge>)}</div>
                            )}
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
                          <td className="px-3 py-2.5"><Badge variant={QUESTION_STATUS[q.status]}>{titleCase(q.status)}</Badge></td>
                          <td className="text-muted-foreground px-3 py-2.5 whitespace-nowrap">{formatDate(q.createdAt)}</td>
                          <td className="px-4 py-2.5">
                            <div className="flex justify-end gap-1">
                              <Button variant="ghost" size="icon" asChild aria-label="Edit"><Link to={`/admin/questions/${q.id}`}><Pencil /></Link></Button>
                              {q.status !== 'ARCHIVED' && (
                                <Button variant="ghost" size="icon" aria-label="Archive" disabled={archive.isPending}
                                        onClick={() => { if (confirm('Archive this question? It stays in existing tests.')) archive.mutate(q.id) }}>
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

      <ImportDialog open={importOpen} onOpenChange={setImportOpen} />
    </>
  )
}
