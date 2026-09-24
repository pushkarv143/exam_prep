import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { AnswerState } from '@/types/exam'
import { paletteState, useExamStore, visit } from '../examStore'

/** NTA colour scheme for the question palette. */
export const STATE_STYLE: Record<AnswerState, { label: string; className: string }> = {
  NOT_VISITED: { label: 'Not visited', className: 'bg-muted text-foreground border-border' },
  NOT_ANSWERED: { label: 'Not answered', className: 'bg-red-600 text-white border-red-700 rounded-b-xl' },
  ANSWERED: { label: 'Answered', className: 'bg-green-600 text-white border-green-700 rounded-t-xl' },
  MARKED_FOR_REVIEW: { label: 'Marked for review', className: 'bg-violet-700 text-white border-violet-800 rounded-full' },
  ANSWERED_AND_MARKED: { label: 'Answered & marked for review (will be evaluated)',
    className: 'bg-violet-700 text-white border-violet-800 rounded-full' },
}

export function StateBadge({ state, number, size = 'md' }: { state: AnswerState; number: number | string; size?: 'sm' | 'md' }) {
  return (
    <span className={cn('relative grid place-items-center border font-semibold tabular-nums',
      size === 'md' ? 'size-10 text-sm' : 'size-7 text-xs', STATE_STYLE[state].className,
      state === 'NOT_VISITED' && 'rounded-md')}>
      {number}
      {state === 'ANSWERED_AND_MARKED' && (
        <Check className="absolute -right-1 -bottom-1 size-4 rounded-full bg-green-600 p-0.5 text-white" />
      )}
    </span>
  )
}

export function Palette({ onNavigate }: { onNavigate?: () => void }) {
  const flat = useExamStore((s) => s.flat)
  const answers = useExamStore((s) => s.answers)
  const index = useExamStore((s) => s.index)
  const paper = useExamStore((s) => s.paper)
  const current = flat[index]

  const counts: Record<AnswerState, number> = {
    NOT_VISITED: 0, NOT_ANSWERED: 0, ANSWERED: 0, MARKED_FOR_REVIEW: 0, ANSWERED_AND_MARKED: 0,
  }
  flat.forEach((q) => counts[paletteState(answers[q.questionId])]++)
  const section = paper?.sections[current?.sectionIndex ?? 0]

  return (
    <div className="flex h-full flex-col gap-4">
      <ul className="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
        {(Object.keys(STATE_STYLE) as AnswerState[]).map((s) => (
          <li key={s} className={cn('flex items-center gap-2', s === 'ANSWERED_AND_MARKED' && 'col-span-2')}>
            <StateBadge state={s} number={counts[s]} size="sm" />
            <span className="text-muted-foreground">{STATE_STYLE[s].label}</span>
          </li>
        ))}
      </ul>
      <div>
        <p className="bg-primary text-primary-foreground rounded-t-md px-3 py-1.5 text-sm font-medium">{section?.name}</p>
        <div className="grid grid-cols-5 gap-2 rounded-b-md border p-3" role="list" aria-label="Question palette">
          {flat.filter((q) => q.sectionIndex === current?.sectionIndex).map((q) => (
            <button key={q.questionId} type="button" role="listitem"
                    onClick={() => { visit(q.flatIndex); onNavigate?.() }}
                    aria-label={`Question ${q.number}: ${STATE_STYLE[paletteState(answers[q.questionId])].label}`}
                    aria-current={q.flatIndex === index ? 'true' : undefined}
                    className={cn('justify-self-center rounded-md outline-offset-2',
                      q.flatIndex === index && 'ring-primary ring-2 ring-offset-2')}>
              <StateBadge state={paletteState(answers[q.questionId])} number={q.number} />
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
