import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Check, Clock, PlayCircle, X } from 'lucide-react'
import { attemptsApi } from '@/api/attempts'
import { MathText } from '@/components/common/MathText'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { ZoomableImage } from '@/components/common/ZoomableImage'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { formatClock, formatNumber } from '@/lib/format'
import { LanguageSwitch } from '@/components/common/LanguageSwitch'
import { localize, paperLanguages } from '@/lib/localize'
import { cn } from '@/lib/utils'
import { useQuestionLanguage } from '@/store/language'
import type { Passage, ReviewItem } from '@/types/exam'
import { resultKeys } from './resultColors'
import { AskDoubtButton } from './components/AskDoubtButton'

type Filter = 'ALL' | 'CORRECT' | 'INCORRECT' | 'PARTIAL' | 'UNATTEMPTED' | 'MARKED'
const FILTER_KEYS: Filter[] = ['ALL', 'CORRECT', 'INCORRECT', 'PARTIAL', 'UNATTEMPTED', 'MARKED']

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'CORRECT', label: 'Correct' },
  { key: 'INCORRECT', label: 'Incorrect' },
  { key: 'PARTIAL', label: 'Partial' },
  { key: 'UNATTEMPTED', label: 'Not attempted' },
  { key: 'MARKED', label: 'Marked for review' },
]

const OUTCOME: Record<ReviewItem['outcome'], { label: string; variant: 'success' | 'destructive' | 'warning' | 'muted' }> = {
  CORRECT: { label: 'Correct', variant: 'success' },
  INCORRECT: { label: 'Incorrect', variant: 'destructive' },
  PARTIAL: { label: 'Partially correct', variant: 'warning' },
  UNATTEMPTED: { label: 'Not attempted', variant: 'muted' },
}

function matches(q: ReviewItem, f: Filter) {
  if (f === 'ALL') return true
  if (f === 'MARKED') return q.state === 'MARKED_FOR_REVIEW' || q.state === 'ANSWERED_AND_MARKED'
  return q.outcome === f
}

export default function SolutionsPage() {
  const { attemptId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const urlFilter = (params.get('filter')?.toUpperCase() ?? 'ALL') as Filter
  const [filter, setFilterState] = useState<Filter>(FILTER_KEYS.includes(urlFilter) ? urlFilter : 'ALL')
  const [sectionId, setSectionId] = useState<string>(params.get('section') ?? 'ALL')
  const review = useQuery({ queryKey: resultKeys.solutions(attemptId), queryFn: () => attemptsApi.solutions(attemptId) })
  const result = useQuery({ queryKey: resultKeys.result(attemptId), queryFn: () => attemptsApi.result(attemptId) })
  const testTitle = result.data?.testTitle ?? 'this test'

  const setFilter = (f: Filter) => {
    setFilterState(f)
    // Keep the URL shareable/back-friendly without a full navigation.
    const next = new URLSearchParams(params)
    if (f === 'ALL') next.delete('filter'); else next.set('filter', f)
    setParams(next, { replace: true })
  }

  const visible = useMemo(() => {
    if (!review.data) return []
    return review.data.sections
      .filter((s) => sectionId === 'ALL' || s.sectionId === sectionId)
      .map((s) => ({ ...s, questions: s.questions.filter((q) => matches(q, filter)) }))
      .filter((s) => s.questions.length > 0)
  }, [review.data, filter, sectionId])

  if (review.isError) return <ErrorState error={review.error} onRetry={() => review.refetch()} />
  if (review.isPending) return <PageLoader />
  const all = review.data.sections.flatMap((s) => s.questions)
  const languages = paperLanguages(all)

  return (
    <>
      <PageHeader title="Solutions" description="Your answers, the correct answers and explanations"
                  actions={
                    <div className="flex items-center gap-3">
                      <LanguageSwitch languages={languages} />
                      <Button variant="outline" asChild><Link to={`/attempts/${attemptId}/result`}><ArrowLeft /> Back to result</Link></Button>
                    </div>
                  } />

      <div className="bg-background/95 sticky top-0 z-10 -mx-1 mb-6 space-y-3 px-1 py-2 backdrop-blur">
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Sections">
          {[{ sectionId: 'ALL', name: 'All sections' }, ...review.data.sections].map((s) => (
            <Button key={s.sectionId} size="sm" role="tab" aria-selected={sectionId === s.sectionId}
                    variant={sectionId === s.sectionId ? 'default' : 'outline'} onClick={() => setSectionId(s.sectionId)}>
              {s.name}
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2" aria-label="Filter by outcome">
          {FILTERS.map((f) => (
            <button key={f.key} type="button" onClick={() => setFilter(f.key)} aria-pressed={filter === f.key}
                    className={cn('rounded-full border px-3 py-1 text-xs font-medium',
                      filter === f.key ? 'bg-primary text-primary-foreground border-primary' : 'hover:bg-muted')}>
              {f.label} ({all.filter((q) => matches(q, f.key)).length})
            </button>
          ))}
        </div>
      </div>

      {visible.length === 0 ? (
        <EmptyState title="No questions match this filter" />
      ) : (
        <div className="space-y-8">
          {visible.map((s) => (
            <section key={s.sectionId}>
              <h2 className="mb-3 text-lg font-semibold">{s.name}</h2>
              <div className="space-y-4">
                {s.questions.map((q) => (
                  <ReviewCard key={q.questionId} q={q} testTitle={testTitle}
                              passage={q.paragraphId ? review.data.passages[q.paragraphId] : undefined} />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </>
  )
}

function ReviewCard({ q: original, passage, testTitle }: { q: ReviewItem; passage?: Passage; testTitle: string }) {
  const language = useQuestionLanguage((s) => s.language)
  const q = localize(original, language)
  const translated = (original.language ?? 'EN') !== language ? original.translations?.[language] : undefined
  const solutionText = translated?.solution || q.solution?.text
  const passageText = passage && (passage.language ?? 'EN') !== language ? passage.translations?.[language] ?? passage.text : passage?.text
  const o = OUTCOME[q.outcome]
  const awarded = Number(q.marksAwarded)
  return (
    <Card className="py-5">
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-center gap-2 border-b pb-3">
          <span className="font-semibold">Q{q.number}</span>
          <Badge variant={o.variant}>{o.label}</Badge>
          <span className={cn('text-sm font-medium tabular-nums', awarded > 0 ? 'text-success' : awarded < 0 ? 'text-destructive' : 'text-muted-foreground')}>
            {awarded > 0 ? '+' : ''}{formatNumber(awarded)} / {formatNumber(q.marks)}
          </span>
          <span className="text-muted-foreground ml-auto flex items-center gap-1 text-xs"><Clock className="size-3.5" /> {formatClock(q.timeSpentSeconds)}</span>
          <AskDoubtButton testTitle={testTitle} questionNumber={q.number} questionText={q.text} />
        </div>

        {passage && (
          <details className="bg-muted/50 rounded-lg border p-3 text-sm">
            <summary className="cursor-pointer font-medium">Passage</summary>
            <MathText text={passageText} className="mt-2" />
          </details>
        )}
        <MathText text={q.text} />
        {q.images.map((img) => (
          <ZoomableImage key={img.url} src={img.url} alt={img.alt ?? 'Question figure'} />
        ))}

        {(q.type === 'SINGLE_CORRECT' || q.type === 'MULTIPLE_CORRECT') && (
          <ul className="grid gap-2">
            {q.options.map((opt, i) => {
              const correct = q.correctAnswer.options?.includes(opt.id) ?? false
              const chosen = q.yourAnswer?.options?.includes(opt.id) ?? false
              return (
                <li key={opt.id} className={cn('flex items-start gap-3 rounded-lg border p-3 text-sm',
                  correct && 'border-success bg-success/10', chosen && !correct && 'border-destructive bg-destructive/10')}>
                  <span className="grid size-6 shrink-0 place-items-center rounded-full border text-xs font-semibold">{i + 1}</span>
                  <span className="min-w-0 flex-1">
                    <MathText text={opt.text} as="span" />
                    {opt.image && <ZoomableImage src={opt.image} alt={`Option ${i + 1}`} thumbClassName="mt-2" className="max-h-32" />}
                  </span>
                  {correct && <Check className="text-success size-4 shrink-0" aria-label="Correct option" />}
                  {chosen && !correct && <X className="text-destructive size-4 shrink-0" aria-label="Your wrong choice" />}
                  {chosen && <span className="text-muted-foreground shrink-0 text-xs">Your answer</span>}
                </li>
              )
            })}
          </ul>
        )}

        {q.type === 'NUMERICAL' && (
          <div className="grid gap-2 text-sm sm:grid-cols-2">
            <p>Your answer: <strong className="tabular-nums">{q.yourAnswer?.value ?? '—'}</strong></p>
            <p>Correct answer: <strong className="text-success tabular-nums">
              {q.correctAnswer.value}{q.correctAnswer.tolerance ? ` (± ${q.correctAnswer.tolerance})` : ''}
            </strong></p>
          </div>
        )}

        {q.type === 'MATCH' && (
          <table className="text-sm">
            <thead><tr className="text-muted-foreground text-left"><th className="pr-6 font-medium">Item</th><th className="pr-6 font-medium">Your match</th><th className="font-medium">Correct</th></tr></thead>
            <tbody>
              {q.matchLeft.map((l) => {
                const mine = q.yourAnswer?.pairs?.[l.id]
                const right = q.correctAnswer.pairs?.[l.id]
                return (
                  <tr key={l.id}>
                    <td className="pr-6 py-1"><strong>{l.id}.</strong> <MathText text={l.text} as="span" /></td>
                    <td className={cn('pr-6', mine && (mine === right ? 'text-success' : 'text-destructive'))}>{mine ?? '—'}</td>
                    <td className="text-success font-medium">{right}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}

        {(solutionText || q.solution?.videoUrl || q.solution?.images?.length) && (
          <div className="bg-accent/40 space-y-2 rounded-lg border p-4 text-sm">
            <p className="font-semibold">Solution</p>
            {solutionText && <MathText text={solutionText} />}
            {q.solution?.images?.map((img) => <ZoomableImage key={img.url} src={img.url} alt={img.alt ?? 'Solution figure'} />)}
            {q.solution?.videoUrl && /^https?:\/\//.test(q.solution?.videoUrl) && (
              <a href={q.solution?.videoUrl} target="_blank" rel="noopener noreferrer" className="text-primary inline-flex items-center gap-1 font-medium hover:underline">
                <PlayCircle className="size-4" /> Watch video solution
              </a>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
