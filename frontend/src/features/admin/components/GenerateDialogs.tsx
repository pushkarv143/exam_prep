import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { adminApi, useCatalogTree } from '@/api/admin'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { errorMessage } from '@/lib/errors'
import type { AdminTest, Difficulty, GenerationReport, TestSection } from '@/types/admin'
import { DIFFICULTIES, titleCase } from '../labels'

function ReportView({ report }: { report: GenerationReport }) {
  return report.shortfalls.length === 0 ? (
    <Alert><AlertTitle>Added {report.added} question(s)</AlertTitle></Alert>
  ) : (
    <Alert variant="destructive">
      <AlertTitle>Added {report.added}; not enough questions for some rules</AlertTitle>
      <AlertDescription>
        <ul className="mt-1 list-disc pl-4">
          {report.shortfalls.map((s, i) => <li key={i}>{s.sectionName}: {s.rule}: found {s.found} of {s.requested}</li>)}
        </ul>
      </AlertDescription>
    </Alert>
  )
}

/** Adds N random questions to one section, optionally narrowed by chapter and difficulty. */
export function GenerateSectionDialog({ test, section, open, onOpenChange }: {
  test: AdminTest; section: TestSection; open: boolean; onOpenChange: (o: boolean) => void
}) {
  const qc = useQueryClient()
  const tree = useCatalogTree(test.examId)
  const missing = Math.max(1, (section.targetCount ?? 0) - section.questionCount)
  const [count, setCount] = useState(String(missing))
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('')
  const [chapterIds, setChapterIds] = useState<string[]>([])
  const [strict, setStrict] = useState(false)
  const [fresh, setFresh] = useState(true)
  const [report, setReport] = useState<GenerationReport | null>(null)
  useEffect(() => {
    if (open) { setCount(String(missing)); setReport(null); setChapterIds([]); setDifficulty('') }
  }, [open, missing])
  const chapters = tree.data?.subjects.filter((s) => !section.subjectId || s.id === section.subjectId).flatMap((s) => s.chapters) ?? []

  const run = useMutation({
    mutationFn: () => adminApi.generate(test.id, [{
      sectionId: section.id, count: Number(count), difficulty: difficulty || undefined,
      chapterIds: chapterIds.length ? chapterIds : undefined,
      types: section.questionType ? [section.questionType] : undefined,
    }], strict, fresh),
    onSuccess: (r) => {
      setReport(r)
      void qc.invalidateQueries({ queryKey: ['admin', 'test', test.id] })
      if (r.shortfalls.length === 0) toast.success(`Added ${r.added} question(s)`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const valid = /^\d+$/.test(count) && +count >= 1 && +count <= 200

  return (
    <Dialog open={open} onOpenChange={(o) => !run.isPending && onOpenChange(o)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Auto-fill {section.name}</DialogTitle>
          <DialogDescription>Picks random active questions{section.questionType ? ` of type ${titleCase(section.questionType)}` : ''} from the bank.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-2"><Label htmlFor="g-count">Number of questions</Label>
              <Input id="g-count" inputMode="numeric" value={count} onChange={(e) => setCount(e.target.value)} aria-invalid={!valid} /></div>
            <div className="grid gap-2"><Label htmlFor="g-diff">Difficulty</Label>
              <NativeSelect id="g-diff" value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}>
                <option value="">Any</option>
                {DIFFICULTIES.map((d) => <option key={d} value={d}>{titleCase(d)}</option>)}
              </NativeSelect></div>
          </div>
          {chapters.length > 0 && (
            <div className="grid gap-2">
              <Label>Chapters (none selected = all)</Label>
              <div className="grid max-h-44 gap-1 overflow-y-auto rounded-md border p-2 text-sm sm:grid-cols-2">
                {chapters.map((c) => (
                  <label key={c.id} className="flex items-center gap-2">
                    <Checkbox checked={chapterIds.includes(c.id)}
                              onCheckedChange={(v) => setChapterIds((ids) => (v ? [...ids, c.id] : ids.filter((x) => x !== c.id)))} />
                    {c.name}
                  </label>
                ))}
              </div>
            </div>
          )}
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={fresh} onCheckedChange={setFresh} /> Skip questions used in other published tests</label>
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={strict} onCheckedChange={setStrict} /> Strict: add nothing if there are not enough questions</label>
          {report && <ReportView report={report} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={run.isPending}>Close</Button>
          <Button disabled={!valid} loading={run.isPending} onClick={() => run.mutate()}>Generate</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Fills every section of a pattern test up to its target, with a difficulty mix. */
export function GeneratePatternDialog({ test, open, onOpenChange }: { test: AdminTest; open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient()
  const [mix, setMix] = useState({ easy: '30', medium: '50', hard: '20' })
  const [strict, setStrict] = useState(false)
  const [fresh, setFresh] = useState(true)
  const [report, setReport] = useState<GenerationReport | null>(null)
  useEffect(() => { if (open) setReport(null) }, [open])
  const total = Number(mix.easy) + Number(mix.medium) + Number(mix.hard)

  const run = useMutation({
    mutationFn: () => adminApi.generateFromPattern(test.id, {
      easyPercent: Number(mix.easy), mediumPercent: Number(mix.medium), hardPercent: Number(mix.hard),
      strict, excludeUsedInPublishedTests: fresh,
    }),
    onSuccess: (r) => {
      setReport(r)
      void qc.invalidateQueries({ queryKey: ['admin', 'test', test.id] })
      if (r.shortfalls.length === 0) toast.success(`Added ${r.added} question(s)`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !run.isPending && onOpenChange(o)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Generate the full paper</DialogTitle>
          <DialogDescription>Fills each section up to its target count with random active questions from its subject.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4">
          <div className="grid grid-cols-3 gap-3">
            {(['easy', 'medium', 'hard'] as const).map((k) => (
              <div key={k} className="grid gap-2">
                <Label htmlFor={`mix-${k}`}>{titleCase(k)} %</Label>
                <Input id={`mix-${k}`} inputMode="numeric" value={mix[k]} onChange={(e) => setMix({ ...mix, [k]: e.target.value.replace(/\D/g, '') })} />
              </div>
            ))}
          </div>
          {total !== 100 && <p className="text-destructive text-sm">The mix must add up to 100% (now {total}%).</p>}
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={fresh} onCheckedChange={setFresh} /> Skip questions used in other published tests</label>
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={strict} onCheckedChange={setStrict} /> Strict: add nothing if any section falls short</label>
          {report && <ReportView report={report} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={run.isPending}>Close</Button>
          <Button disabled={total !== 100} loading={run.isPending} onClick={() => run.mutate()}>Generate</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
