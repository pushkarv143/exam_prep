import type { CognitiveLevel, QuestionStatus, SeriesStatus, SourceType, TestStatus } from '@/types/admin'
import type { PartialRule, QuestionType } from '@/types/exam'

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

export const QUESTION_STATUS: Record<QuestionStatus, Variant> = {
  DRAFT: 'muted', IN_REVIEW: 'warning', CHANGES_REQUESTED: 'destructive', APPROVED: 'default', PUBLISHED: 'success',
  ARCHIVED: 'outline',
}
export const QUESTION_STATUS_LABEL: Record<QuestionStatus, string> = {
  DRAFT: 'Draft', IN_REVIEW: 'In review', CHANGES_REQUESTED: 'Changes requested', APPROVED: 'Approved',
  PUBLISHED: 'Published', ARCHIVED: 'Archived',
}
export const QUESTION_STATUSES = Object.keys(QUESTION_STATUS_LABEL) as QuestionStatus[]

export const SOURCE_TYPES: { value: SourceType; label: string }[] = [
  { value: 'PYQ', label: 'Previous-year question' },
  { value: 'COACHING', label: 'Coaching material' },
  { value: 'BOOK', label: 'Book' },
  { value: 'ORIGINAL', label: 'Original' },
]
export const COGNITIVE_LEVELS: { value: CognitiveLevel; label: string; hint: string }[] = [
  { value: 'RECALL', label: 'Recall', hint: 'Remember a fact or formula' },
  { value: 'APPLY', label: 'Apply', hint: 'Use a concept in a standard situation' },
  { value: 'ANALYSE', label: 'Analyse', hint: 'Combine concepts or reason through a new situation' },
]
export const PARTIAL_RULES: { value: PartialRule; label: string; hint: string }[] = [
  { value: 'JEE_ADVANCED', label: 'JEE Advanced', hint: '+1 per correct option chosen (for 4 marks), if no wrong option' },
  { value: 'PROPORTIONAL', label: 'Proportional', hint: 'marks × chosen ÷ correct options, if no wrong option' },
  { value: 'NONE', label: 'All or nothing', hint: 'No partial marks, even if the test allows them' },
]
export const LANGUAGE_LABEL = { EN: 'English', HI: 'हिन्दी' } as const
export const TEST_STATUS: Record<TestStatus, Variant> = {
  DRAFT: 'warning', PUBLISHED: 'default', LIVE: 'success', COMPLETED: 'outline', ARCHIVED: 'muted',
}
export const SERIES_STATUS: Record<SeriesStatus, Variant> = { DRAFT: 'warning', PUBLISHED: 'success', ARCHIVED: 'muted' }

export const titleCase = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ')
