import type { QuestionStatus, SeriesStatus, TestStatus } from '@/types/admin'
import type { QuestionType } from '@/types/exam'

export const QUESTION_TYPES: { value: QuestionType; label: string }[] = [
  { value: 'SINGLE_CORRECT', label: 'Single correct' },
  { value: 'MULTIPLE_CORRECT', label: 'Multiple correct' },
  { value: 'NUMERICAL', label: 'Numerical' },
  { value: 'MATCH', label: 'Match the columns' },
  { value: 'PARAGRAPH', label: 'Paragraph (passage)' },
]

export const TYPE_LABEL = Object.fromEntries(QUESTION_TYPES.map((t) => [t.value, t.label])) as Record<QuestionType, string>

export const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const

type Variant = 'success' | 'warning' | 'muted' | 'default' | 'destructive' | 'outline'

export const QUESTION_STATUS: Record<QuestionStatus, Variant> = { DRAFT: 'warning', ACTIVE: 'success', ARCHIVED: 'muted' }
export const TEST_STATUS: Record<TestStatus, Variant> = {
  DRAFT: 'warning', PUBLISHED: 'default', LIVE: 'success', COMPLETED: 'outline', ARCHIVED: 'muted',
}
export const SERIES_STATUS: Record<SeriesStatus, Variant> = { DRAFT: 'warning', PUBLISHED: 'success', ARCHIVED: 'muted' }

export const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ')
