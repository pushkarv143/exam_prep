/** Mirrors of the attempt, result and solution DTOs. */

export type QuestionType = 'SINGLE_CORRECT' | 'MULTIPLE_CORRECT' | 'NUMERICAL' | 'MATCH' | 'PARAGRAPH'
export type AnswerState = 'NOT_VISITED' | 'NOT_ANSWERED' | 'ANSWERED' | 'MARKED_FOR_REVIEW' | 'ANSWERED_AND_MARKED'
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'EVALUATED' | 'CANCELLED'

export interface Media { url: string; alt?: string }
/** `pinned`: keeps its position when options are shuffled (e.g. "None of these"). */
export interface Option { id: string; text?: string; image?: string; pinned?: boolean }
export interface MatchItem { id: string; text: string }

export type Language = 'EN' | 'HI'
export type NumericFormat = 'INTEGER' | 'DECIMAL'

/** Student-visible text of a question in another language; option/match texts keyed by id. */
export interface StudentTranslation {
  text?: string
  options?: Record<string, string>
  matchLeft?: Record<string, string>
  matchRight?: Record<string, string>
}

export interface StudentAnswer {
  options?: string[]
  value?: string
  pairs?: Record<string, string>
}

export interface PaperQuestion {
  questionId: string
  number: number
  type: QuestionType
  marks: number
  negativeMarks: number
  partialMarking: boolean
  paragraphId?: string
  text?: string
  images: Media[]
  options: Option[]
  matchLeft: MatchItem[]
  matchRight: MatchItem[]
  /** Language of text/options; translations hold the others. */
  language?: Language
  translations?: Partial<Record<Language, StudentTranslation>>
  /** False when the author keeps the option order fixed. */
  shuffleOptions?: boolean
  numericFormat?: NumericFormat
}

export interface PaperSection {
  id: string
  name: string
  subjectId?: string
  instructions?: string
  maxQuestionsToAttempt?: number
  questions: PaperQuestion[]
}

export interface Passage {
  id: string
  text?: string
  images: Media[]
  language?: Language
  translations?: Partial<Record<Language, string>>
}

export interface Paper {
  testId: string
  title: string
  durationMinutes: number
  totalMarks: number
  totalQuestions: number
  sections: PaperSection[]
  passages: Record<string, Passage>
}

export interface AnswerStateDto {
  answer?: StudentAnswer
  state: AnswerState
  timeSpentSeconds: number
  visits: number
  seq: number
}

export interface AntiCheat {
  tabSwitchCount: number
  fullscreenExitCount: number
  maxTabSwitches: number
  autoSubmitted: boolean
}

export interface AttemptSession {
  attemptId: string
  testId: string
  attemptNo: number
  status: AttemptStatus
  startedAt: string
  deadlineAt: string
  serverNow: string
  remainingSeconds: number
  paper?: Paper
  answers: Record<string, AnswerStateDto>
  lastSeq: number
  antiCheat: AntiCheat
}

export interface AnswerChange {
  questionId: string
  seq: number
  answer: StudentAnswer | null
  markedForReview: boolean
  timeSpentSeconds: number
  visits: number
}

export interface AutosaveResult {
  applied: number
  rejected: { questionId: string; reason: string }[]
  serverNow: string
  remainingSeconds: number
}

export interface AttemptSummary {
  attemptId: string
  testId: string
  attemptNo: number
  status: AttemptStatus
  submitType?: 'MANUAL' | 'AUTO'
  startedAt: string
  deadlineAt: string
  submittedAt?: string
  answeredCount: number
  markedCount: number
  visitedCount: number
  tabSwitchCount: number
  fullscreenExitCount: number
}

export interface SectionInfo {
  name: string
  subjectId?: string
  questionCount: number
  maxQuestionsToAttempt?: number
  marks: number
}

export interface TestInfo {
  id: string
  seriesId?: string
  seriesSlug?: string
  title: string
  description?: string
  instructions?: string
  pattern: string
  durationMinutes: number
  totalMarks: number
  totalQuestions: number
  startAt?: string
  endAt?: string
  availability: 'NOT_PUBLISHED' | 'UPCOMING' | 'OPEN' | 'CLOSED'
  free: boolean
  maxAttempts: number
  hasAccess: boolean
  sections: SectionInfo[]
}

export type AttemptEventType = 'TAB_SWITCH' | 'WINDOW_BLUR' | 'FULLSCREEN_EXIT' | 'FULLSCREEN_ENTER' | 'COPY' | 'PASTE'
  | 'CONTEXT_MENU' | 'DEVTOOLS_OPEN' | 'NETWORK_OFFLINE' | 'NETWORK_ONLINE'

// ---- results ------------------------------------------------------------------------

export type ResultStatus = 'EVALUATING' | 'AWAITING_PUBLICATION' | 'READY'

export interface SectionResult {
  sectionId: string
  name: string
  subjectId?: string
  total: number
  attempted: number
  correct: number
  incorrect: number
  partial: number
  unattempted: number
  score: number
  maxScore: number
  accuracy: number
  timeSpentSeconds: number
}

export interface TopicResult {
  topicId: string
  topicName?: string
  chapterName?: string
  subjectName?: string
  total: number
  attempted: number
  correct: number
  incorrect: number
  partial: number
  score: number
  maxScore: number
  accuracy: number
  timeSpentSeconds: number
}

export interface Result {
  attemptId: string
  testId: string
  testTitle: string
  status: ResultStatus
  attemptNo: number
  ranked: boolean
  score?: number
  maxScore: number
  percentage?: number
  rank?: number
  percentile?: number
  rankFinal: boolean
  totalCandidates?: number
  correct?: number
  incorrect?: number
  partial?: number
  unattempted?: number
  accuracy?: number
  timeTakenSeconds?: number
  evaluatedAt?: string
  sections: SectionResult[]
  topics: TopicResult[]
  solutionsAvailable: boolean
  solutionsAvailableAt?: string
}

export type PartialRule = 'JEE_ADVANCED' | 'PROPORTIONAL' | 'NONE'

/** NUMERICAL: value (+ tolerance) or a min/max range. MULTIPLE_CORRECT: partial rule (absent = JEE Advanced). */
export interface AnswerKey {
  options?: string[]
  value?: number
  tolerance?: number
  pairs?: Record<string, string>
  min?: number
  max?: number
  partial?: PartialRule
}

/** A full translation (staff and solution review): student texts plus the solution. */
export interface QuestionTranslation extends StudentTranslation {
  paragraph?: string
  solution?: string
}

export interface ReviewItem {
  number: number
  questionId: string
  type: QuestionType
  paragraphId?: string
  text?: string
  images: Media[]
  options: Option[]
  matchLeft: MatchItem[]
  matchRight: MatchItem[]
  yourAnswer?: StudentAnswer
  state: AnswerState
  correctAnswer: AnswerKey
  outcome: 'CORRECT' | 'INCORRECT' | 'PARTIAL' | 'UNATTEMPTED'
  marksAwarded: number
  marks: number
  negativeMarks: number
  timeSpentSeconds: number
  solution?: { text?: string; videoUrl?: string; images?: Media[] }
  language?: Language
  translations?: Partial<Record<Language, QuestionTranslation>>
}

export interface SolutionReview {
  attemptId: string
  testId: string
  sections: { sectionId: string; name: string; questions: ReviewItem[] }[]
  passages: Record<string, Passage>
}

export interface LeaderboardEntry { rank: number; name: string; score: number; timeTakenSeconds: number; you: boolean }

export interface Leaderboard {
  testId: string
  totalCandidates: number
  entries: LeaderboardEntry[]
  me?: LeaderboardEntry
  myPercentile?: number
}

export interface Performance { score?: number; accuracy?: number; timeTakenSeconds?: number; correct?: number; incorrect?: number }

export interface Comparison {
  attemptId: string
  testId: string
  candidates: number
  you: Performance
  topper: Performance
  average: Performance
  sections: { sectionId: string; name: string; you: number; topper?: number; average?: number; maxScore: number }[]
}
