import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { MathText } from '@/components/common/MathText'
import { ZoomableImage } from '@/components/common/ZoomableImage'
import { Badge } from '@/components/ui/badge'
import { localize } from '@/lib/localize'
import { cn } from '@/lib/utils'
import { useQuestionLanguage } from '@/store/language'
import type { StudentAnswer } from '@/types/exam'
import {
  currentSectionAnsweredCount, hasAnswer, setAnswer, useExamStore, type FlatQuestion,
} from '../examStore'

const NUMERIC_TYPING = /^-?\d{0,10}(\.\d{0,6})?$/
const INTEGER_TYPING = /^-?\d{0,10}$/

/** Renders the current question and its answer input for all 4 answerable types. */
export function QuestionPanel({ question: original }: { question: FlatQuestion }) {
  const language = useQuestionLanguage((s) => s.language)
  const question = localize(original, language)
  const local = useExamStore((s) => s.answers[question.questionId])
  const paper = useExamStore((s) => s.paper)!
  const section = paper.sections[question.sectionIndex]
  const passage = question.paragraphId ? paper.passages[question.paragraphId] : undefined
  const passageText = passage && (passage.language ?? 'EN') !== language ? passage.translations?.[language] ?? passage.text : passage?.text
  const answer = local?.answer ?? null

  /** Enforces "attempt any N" before touching state (the server enforces it too). */
  const change = (next: StudentAnswer | null) => {
    const limit = section.maxQuestionsToAttempt
    if (limit && hasAnswer(next) && !hasAnswer(answer) && currentSectionAnsweredCount(section.id) >= limit) {
      toast.error(`You can answer only ${limit} questions in ${section.name}. Clear another answer first.`)
      return
    }
    setAnswer(question.questionId, next)
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
        <h2 className="text-lg font-semibold">Question {question.number}</h2>
        <div className="flex flex-wrap gap-2 text-xs">
          <Badge variant="success">+{question.marks}</Badge>
          {question.negativeMarks > 0 && <Badge variant="destructive">−{question.negativeMarks}</Badge>}
          <Badge variant="outline">{TYPE_LABEL[question.type]}</Badge>
          {section.maxQuestionsToAttempt && (
            <Badge variant="warning">Attempt any {section.maxQuestionsToAttempt}</Badge>
          )}
        </div>
      </div>

      {passage && (
        <div className="bg-muted/50 rounded-lg border p-4 text-sm">
          <p className="text-muted-foreground mb-2 text-xs font-semibold uppercase">Read the passage</p>
          <MathText text={passageText} />
          <Images images={passage.images} />
        </div>
      )}

      <MathText text={question.text} className="text-base" />
      <Images images={question.images} />

      {(question.type === 'SINGLE_CORRECT' || question.type === 'MULTIPLE_CORRECT') && (
        <ChoiceInput question={question} selected={answer?.options ?? []}
                     onChange={(opts) => change(opts.length ? { options: opts } : null)} />
      )}
      {question.type === 'NUMERICAL' && (
        <NumericInput key={question.questionId} value={answer?.value ?? ''} integer={question.numericFormat === 'INTEGER'}
                      onChange={(v) => change(v.trim() ? { value: v.trim() } : null)} />
      )}
      {question.type === 'MATCH' && (
        <MatchInput question={question} pairs={answer?.pairs ?? {}}
                    onChange={(pairs) => change(Object.keys(pairs).length ? { pairs } : null)} />
      )}
    </div>
  )
}

const TYPE_LABEL: Record<string, string> = {
  SINGLE_CORRECT: 'Single correct', MULTIPLE_CORRECT: 'One or more correct', NUMERICAL: 'Numerical', MATCH: 'Match',
  PARAGRAPH: 'Paragraph',
}

function Images({ images }: { images?: { url: string; alt?: string }[] }) {
  if (!images?.length) return null
  return (
    <div className="flex flex-wrap gap-3">
      {images.map((img) => (
        <ZoomableImage key={img.url} src={img.url} alt={img.alt ?? 'Question figure'}
                       className="max-h-72 max-w-full object-contain" />
      ))}
    </div>
  )
}

/** Options are listed in the (possibly shuffled) order given; labels are positional (1)(2)(3)(4), like NTA. */
function ChoiceInput({ question, selected, onChange }:
                       { question: FlatQuestion; selected: string[]; onChange: (ids: string[]) => void }) {
  const multi = question.type === 'MULTIPLE_CORRECT'
  const toggle = (id: string) => {
    if (multi) onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id])
    else onChange(selected.includes(id) ? [] : [id])
  }

  // Keyboard: number keys 1-9 pick the matching option (like NTA). Ignored while typing elsewhere.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return
      const el = document.activeElement
      if (el && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) return
      const n = Number(e.key)
      if (!Number.isInteger(n) || n < 1 || n > question.options.length) return
      e.preventDefault()
      toggle(question.options[n - 1].id)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [question.options, selected, multi])

  return (
    <div className="grid gap-3" role={multi ? 'group' : 'radiogroup'} aria-label="Options">
      {question.options.map((o, i) => {
        const checked = selected.includes(o.id)
        return (
          <button key={o.id} type="button" role={multi ? 'checkbox' : 'radio'} aria-checked={checked}
                  aria-keyshortcuts={`${i + 1}`}
                  onClick={() => toggle(o.id)}
                  className={cn('flex w-full items-start gap-3 rounded-lg border p-3 text-left transition-colors',
                    checked ? 'border-primary bg-accent' : 'hover:bg-muted/60')}>
            <span className={cn('mt-0.5 grid size-6 shrink-0 place-items-center border text-xs font-semibold',
              multi ? 'rounded' : 'rounded-full',
              checked ? 'bg-primary border-primary text-primary-foreground' : 'bg-background')}>
              {i + 1}
            </span>
            <span className="min-w-0 flex-1">
              <MathText text={o.text} as="span" />
              {o.image && <ZoomableImage src={o.image} alt={`Option ${i + 1}`} className="mt-2 max-h-40" />}
            </span>
          </button>
        )
      })}
      {multi && <p className="text-muted-foreground text-xs">Select one or more options.</p>}
    </div>
  )
}

/** Numeric answer box. Local text state allows transient inputs like "-" or "2." while typing. */
function NumericInput({ value, onChange, integer }: { value: string; onChange: (v: string) => void; integer?: boolean }) {
  const [text, setText] = useState(value)
  useEffect(() => setText(value), [value])
  return (
    <div className="max-w-xs space-y-2">
      <label htmlFor="numeric-answer" className="text-sm font-medium">Your answer</label>
      <input id="numeric-answer" inputMode={integer ? 'numeric' : 'decimal'} autoComplete="off" value={text}
             onChange={(e) => {
               const v = e.target.value
               if (!(integer ? INTEGER_TYPING : NUMERIC_TYPING).test(v)) return
               setText(v)
               if (v === '' || /\d$/.test(v)) onChange(v)
             }}
             className="border-input focus-visible:ring-ring/50 h-12 w-full rounded-md border px-3 text-lg tabular-nums outline-none focus-visible:ring-[3px]" />
      <p className="text-muted-foreground text-xs">
        {integer ? 'Enter a whole number (no decimal point).' : 'Enter an integer or decimal (up to 6 decimal places).'}
      </p>
    </div>
  )
}

function MatchInput({ question, pairs, onChange }:
                      { question: FlatQuestion; pairs: Record<string, string>; onChange: (p: Record<string, string>) => void }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="space-y-3">
        {question.matchLeft.map((l) => (
          <div key={l.id} className="flex items-center gap-3 rounded-lg border p-3">
            <span className="font-semibold">{l.id}.</span>
            <MathText text={l.text} as="span" className="flex-1" />
            <select aria-label={`Match for ${l.id}`} value={pairs[l.id] ?? ''}
                    onChange={(e) => {
                      const next = { ...pairs }
                      if (e.target.value) next[l.id] = e.target.value
                      else delete next[l.id]
                      onChange(next)
                    }}
                    className="border-input h-9 rounded-md border px-2 text-sm">
              <option value="">—</option>
              {question.matchRight.map((r) => <option key={r.id} value={r.id}>{r.id}</option>)}
            </select>
          </div>
        ))}
      </div>
      <div className="space-y-3">
        {question.matchRight.map((r) => (
          <div key={r.id} className="bg-muted/40 flex gap-3 rounded-lg border p-3">
            <span className="font-semibold">{r.id}.</span>
            <MathText text={r.text} as="span" />
          </div>
        ))}
      </div>
    </div>
  )
}
