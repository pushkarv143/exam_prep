import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { adminApi, useAdminQuestions } from '@/api/admin'
import { MathText } from '@/components/common/MathText'
import { Pagination } from '@/components/common/Pagination'
import { Spinner } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { errorMessage } from '@/lib/errors'
import type { AdminTest, Difficulty, QuestionFilter, TestSection } from '@/types/admin'
import type { QuestionType } from '@/types/exam'
import { DIFFICULTIES, QUESTION_TYPES, TYPE_LABEL, titleCase } from '../labels'
import { CatalogPicker, type CatalogSelection } from './CatalogPicker'

/** Search the bank and add selected ACTIVE questions to a section. */
export function QuestionPickerDialog({ test, section, inTest, open, onOpenChange }: {
  test: AdminTest; section: TestSection; inTest: Set<string>; open: boolean; onOpenChange: (o: boolean) => void
}) {
  const qc = useQueryClient()
  const [catalog, setCatalog] = useState<CatalogSelection>({ examId: test.examId, subjectId: section.subjectId })
  const [type, setType] = useState<QuestionType | ''>(section.questionType ?? '')
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('')
  const [text, setText] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string[]>([])

  useEffect(() => {
    if (!open) return
    setCatalog({ examId: test.examId, subjectId: section.subjectId })
    setType(section.questionType ?? '')
    setSelected([])
    setPage(0)
  }, [open, section.id, section.subjectId, section.questionType, test.examId])
  useEffect(() => {
    const t = setTimeout(() => { setQ(text.trim()); setPage(0) }, 350)
    return () => clearTimeout(t)
  }, [text])

  const filter: QuestionFilter = {
    ...catalog, examId: test.examId, type: type || undefined, difficulty: difficulty || undefined,
    live: true, q: q || undefined, page, size: 15,
  }
  const questions = useAdminQuestions(filter, open)

  const add = useMutation({
    mutationFn: () => adminApi.addQuestions(test.id, section.id, selected),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: ['admin', 'test', test.id] })
      const extra = [
        r.skippedAlreadyInTest.length ? `${r.skippedAlreadyInTest.length} already in the test` : '',
        r.expandedParagraphs.length ? `${r.expandedParagraphs.length} passage(s) expanded` : '',
      ].filter(Boolean).join(', ')
      toast.success(`Added ${r.added} question(s)${extra ? ` (${extra})` : ''}`)
      onOpenChange(false)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const toggle = (id: string) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))

  return (
    <Dialog open={open} onOpenChange={(o) => !add.isPending && onOpenChange(o)}>
      <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>Add questions to {section.name}</DialogTitle>
          <DialogDescription>Only active questions are listed. Passage questions bring their whole passage group.</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <CatalogPicker value={catalog} lockExam onChange={(v) => { setCatalog(v); setPage(0) }} />
          <div className="grid gap-2 sm:grid-cols-[1fr_180px_150px]">
            <Input placeholder="Search text…" value={text} onChange={(e) => setText(e.target.value)} aria-label="Search" />
            <NativeSelect aria-label="Type" value={type} onChange={(e) => { setType(e.target.value as QuestionType | ''); setPage(0) }}>
              <option value="">All types</option>
              {QUESTION_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </NativeSelect>
            <NativeSelect aria-label="Difficulty" value={difficulty} onChange={(e) => { setDifficulty(e.target.value as Difficulty | ''); setPage(0) }}>
              <option value="">Any difficulty</option>
              {DIFFICULTIES.map((d) => <option key={d} value={d}>{titleCase(d)}</option>)}
            </NativeSelect>
          </div>
        </div>

        <div className="min-h-64 rounded-lg border">
          {questions.isPending ? <div className="grid h-64 place-items-center"><Spinner /></div> :
            questions.isError ? <p className="text-destructive p-4 text-sm">{errorMessage(questions.error)}</p> :
              questions.data.content.length === 0 ? <p className="text-muted-foreground p-6 text-center text-sm">No matching questions.</p> : (
                <ul className="divide-y">
                  {questions.data.content.map((qq) => {
                    const already = inTest.has(qq.id)
                    return (
                      <li key={qq.id}>
                        <label className={`flex items-start gap-3 p-3 text-sm ${already ? 'opacity-50' : 'hover:bg-muted/50 cursor-pointer'}`}>
                          <Checkbox className="mt-0.5" disabled={already} checked={selected.includes(qq.id)} onCheckedChange={() => toggle(qq.id)} />
                          <div className="min-w-0 flex-1">
                            <MathText className="line-clamp-2" text={qq.textPreview || '(no text)'} />
                            <p className="text-muted-foreground mt-0.5 text-xs">
                              {qq.topic.subjectName} · {qq.topic.chapterName} · {qq.topic.topicName}
                            </p>
                          </div>
                          <div className="flex shrink-0 flex-col items-end gap-1">
                            <Badge variant="outline">{TYPE_LABEL[qq.type]}</Badge>
                            {already && <Badge variant="muted">In test</Badge>}
                            {qq.difficulty && <span className="text-muted-foreground text-xs">{titleCase(qq.difficulty)}</span>}
                          </div>
                        </label>
                      </li>
                    )
                  })}
                </ul>
              )}
        </div>
        {questions.data && <Pagination page={questions.data.page} totalPages={questions.data.totalPages} onChange={setPage} />}

        <DialogFooter>
          <span className="text-muted-foreground mr-auto self-center text-sm">{selected.length} selected</span>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={add.isPending}>Cancel</Button>
          <Button disabled={selected.length === 0} loading={add.isPending} onClick={() => add.mutate()}>Add {selected.length || ''}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
