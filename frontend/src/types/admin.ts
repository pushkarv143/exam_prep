/** Mirrors of the staff (admin/teacher) DTOs. Decimals arrive as JSON numbers. */
import type { Exam, ExamPattern, Role, User, UserStatus } from './domain'
import type { AnswerKey, Language, MatchItem, Media, NumericFormat, Option, QuestionTranslation, QuestionType } from './exam'

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type { Language }
export type QuestionStatus = 'DRAFT' | 'IN_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED'
export type SourceType = 'PYQ' | 'COACHING' | 'BOOK' | 'ORIGINAL'
export type CognitiveLevel = 'RECALL' | 'APPLY' | 'ANALYSE'
export type QuestionAction = 'EDIT' | 'SUBMIT' | 'CLAIM' | 'ASSIGN' | 'REQUEST_CHANGES' | 'APPROVE' | 'PUBLISH'
  | 'ARCHIVE' | 'RESTORE' | 'COMMENT'
export type TestStatus = 'DRAFT' | 'PUBLISHED' | 'LIVE' | 'COMPLETED' | 'ARCHIVED'
export type SeriesStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

// ---- catalog -----------------------------------------------------------------------

export interface TopicNode { id: string; name: string; displayOrder: number; active: boolean }
export interface ChapterNode { id: string; name: string; displayOrder: number; active: boolean; topics: TopicNode[] }
export interface SubjectNode { id: string; code: string; name: string; displayOrder: number; active: boolean; chapters: ChapterNode[] }
export interface CatalogTree { exam: Exam; subjects: SubjectNode[] }

export interface TopicPath {
  examId: string
  examCode: string
  subjectId: string
  subjectName: string
  chapterId: string
  chapterName: string
  topicId: string
  topicName: string
}

// ---- questions ---------------------------------------------------------------------

export interface QuestionContent {
  text?: string
  images: Media[]
  options: Option[]
  paragraph?: string
  matchLeft: MatchItem[]
  matchRight: MatchItem[]
  solution?: { text?: string; videoUrl?: string; images?: Media[] }
  /** false keeps the option order fixed even when the test shuffles options */
  shuffleOptions?: boolean
  numericFormat?: NumericFormat
}

export interface QuestionSummary {
  id: string
  type: QuestionType
  difficulty?: Difficulty
  language?: Language
  /** Primary language first, then translated languages. */
  languages?: Language[]
  topic: TopicPath
  subTopic?: string
  textPreview?: string
  marks: number
  negativeMarks: number
  status: QuestionStatus
  currentVersion: number
  /** The live version tests use (absent if never published). */
  publishedVersion?: number
  reviewerId?: string
  reviewerName?: string
  reviewDueAt?: string
  sourceType?: SourceType
  year?: number
  tags: string[]
  createdBy?: string
  createdAt: string
  updatedAt?: string
}

export interface ReviewState {
  reviewerId?: string
  reviewerName?: string
  submittedBy?: string
  submittedByName?: string
  requestedAt?: string
  dueAt?: string
  overdue: boolean
}

export interface Question {
  id: string
  type: QuestionType
  difficulty?: Difficulty
  language?: Language
  topic: TopicPath
  subTopic?: string
  parentId?: string
  content: QuestionContent
  answerKey: AnswerKey
  translations?: Partial<Record<Language, QuestionTranslation>>
  /** Per translated language: texts not translated yet, e.g. ["options.C", "solution"]. */
  missingTranslations?: Partial<Record<Language, string[]>>
  marks: number
  negativeMarks: number
  status: QuestionStatus
  sourceType?: SourceType
  source?: string
  year?: number
  pyqShift?: string
  expectedTimeSec?: number
  cognitiveLevel?: CognitiveLevel
  tags: string[]
  concepts?: string[]
  currentVersion: number
  publishedVersion?: number
  publishedAt?: string
  review?: ReviewState
  openComments: number
  usedInPublishedTests: number
  createdBy?: string
  createdByName?: string
  createdAt: string
  updatedAt: string
  /** What the current user may do now. */
  actions: QuestionAction[]
}

export interface QuestionRequest {
  type: QuestionType
  difficulty?: Difficulty
  language?: Language
  topicId: string
  subTopic?: string | null
  parentId?: string | null
  content: QuestionContent
  answerKey?: AnswerKey
  translations?: Partial<Record<Language, QuestionTranslation>>
  marks?: number | null
  negativeMarks?: number | null
  sourceType?: SourceType | null
  source?: string | null
  year?: number | null
  pyqShift?: string | null
  expectedTimeSec?: number | null
  cognitiveLevel?: CognitiveLevel | null
  tags?: string[]
  concepts?: string[]
  /** The version the editor loaded; a stale one is rejected with QUESTION_VERSION_CONFLICT. */
  baseVersion?: number
  changeNote?: string
}

export interface QuestionFilter {
  examId?: string
  subjectId?: string
  chapterId?: string
  topicId?: string
  type?: QuestionType
  difficulty?: Difficulty
  status?: QuestionStatus
  /** Only questions tests can use (a published version exists, not archived). */
  live?: boolean
  translated?: Language
  sourceType?: SourceType
  cognitiveLevel?: CognitiveLevel
  tag?: string
  concept?: string
  q?: string
  mine?: boolean
  /** "me", "none" or a user id (review queues) */
  reviewer?: string
  overdue?: boolean
  sort?: string
  page?: number
  size?: number
}

/** A saved version of a question (the snapshot stored in question_versions). */
export interface QuestionSnapshot {
  type: QuestionType
  difficulty?: Difficulty
  language?: Language
  topicId?: string
  subTopic?: string
  parentId?: string
  content: QuestionContent
  answerKey: AnswerKey
  translations?: Partial<Record<Language, QuestionTranslation>>
  marks?: number
  negativeMarks?: number
  sourceType?: SourceType
  source?: string
  year?: number
  pyqShift?: string
  expectedTimeSec?: number
  cognitiveLevel?: CognitiveLevel
  tags?: string[]
  concepts?: string[]
}

export interface ImportReport {
  totalRows: number
  validRows: number
  importedRows: number
  dryRun: boolean
  errors: { row: number; message: string }[]
}

export interface StoredFile { key: string; url?: string; contentType: string; size: number }

// ---- tests -------------------------------------------------------------------------

export interface AdminTest {
  id: string
  seriesId?: string
  examId: string
  title: string
  description?: string
  instructions?: string
  pattern: ExamPattern
  durationMinutes: number
  totalMarks: number
  totalQuestions: number
  startAt?: string
  endAt?: string
  status: TestStatus
  free: boolean
  shuffleQuestions: boolean
  shuffleOptions: boolean
  maxAttempts: number
  showResultImmediately: boolean
  displayOrder: number
  ranksComputedAt?: string
  createdAt: string
  updatedAt: string
}

export interface TestQuestion {
  id: string
  questionId: string
  displayOrder: number
  marks: number
  negativeMarks: number
  partialMarking: boolean
  /** The pinned question version; compare with question.publishedVersion. */
  questionVersion: number
  passageVersion?: number
  question: QuestionSummary
}

export interface TestSection {
  id: string
  subjectId?: string
  name: string
  instructions?: string
  displayOrder: number
  defaultMarks?: number
  defaultNegativeMarks?: number
  maxQuestionsToAttempt?: number
  questionType?: QuestionType
  targetCount?: number
  questionCount: number
  sectionMarks: number
  questions: TestQuestion[]
}

export interface TestDetail { test: AdminTest; sections: TestSection[] }

export interface CreateTestRequest {
  seriesId?: string | null
  examId: string
  title: string
  description?: string
  instructions?: string
  pattern: ExamPattern
  durationMinutes?: number | null
  startAt?: string | null
  endAt?: string | null
  free: boolean
  shuffleQuestions: boolean
  shuffleOptions: boolean
  maxAttempts?: number | null
  showResultImmediately?: boolean
  displayOrder: number
}

export interface UpdateTestRequest extends Omit<CreateTestRequest, 'examId' | 'pattern'> {
  durationMinutes: number
  maxAttempts: number
  showResultImmediately: boolean
}

export interface SectionRequest {
  name: string
  subjectId?: string | null
  instructions?: string
  displayOrder: number
  defaultMarks?: number | null
  defaultNegativeMarks?: number | null
  maxQuestionsToAttempt?: number | null
  questionType?: QuestionType | null
  targetCount?: number | null
}

export interface ValidationReport { publishable: boolean; errors: string[]; warnings: string[] }

export interface PatternSection {
  subjectCode: string
  name: string
  type: QuestionType
  count: number
  marks: number
  negativeMarks: number
  maxAttempt?: number
}

export interface PatternInfo {
  code: ExamPattern
  displayName: string
  defaultDurationMinutes?: number
  totalQuestions: number
  totalMarks: number
  sections: PatternSection[]
}

export interface AddQuestionsResult { added: number; skippedAlreadyInTest: string[]; expandedParagraphs: string[] }

export interface GenerationRule {
  sectionId: string
  count: number
  difficulty?: Difficulty
  chapterIds?: string[]
  topicIds?: string[]
  types?: QuestionType[]
  language?: Language
}

export interface GenerationReport {
  added: number
  shortfalls: { sectionId: string; sectionName: string; rule: string; requested: number; found: number }[]
}

// ---- series, batches, users ----------------------------------------------------------

export interface AdminSeries {
  id: string
  examId: string
  name: string
  slug: string
  description?: string
  thumbnailUrl?: string
  price: number
  currency: string
  free: boolean
  validityDays?: number
  validUntil?: string
  batchRestricted: boolean
  batchIds: string[]
  status: SeriesStatus
  testCount: number
  enrollmentCount: number
  createdAt: string
}

export interface SeriesRequest {
  examId: string
  name: string
  description?: string
  thumbnailUrl?: string
  price: number
  free: boolean
  validityDays?: number | null
  validUntil?: string | null
  batchRestricted: boolean
  batchIds: string[]
}

export interface Batch {
  id: string
  code: string
  name: string
  description?: string
  examId?: string
  startDate?: string
  endDate?: string
  active: boolean
  memberCount: number
  createdAt: string
}

export interface AdminUser extends User {
  lastLoginAt?: string
  createdAt: string
}

export interface CreateUserRequest {
  fullName: string
  email: string
  phone?: string
  password: string
  roles: Role[]
}

export type { UserStatus }

// ---- dashboard & stats -------------------------------------------------------------

export interface DailyPoint { date: string; count: number; amount?: number }

export interface AdminDashboard {
  usersByRole: Record<string, number>
  newStudentsLast7Days: number
  testsByStatus: Record<string, number>
  liveAttempts: number
  attemptsToday: number
  attemptsTotal: number
  attemptsDaily: DailyPoint[]
  revenue: {
    total: number
    last30Days: number
    today: number
    paidPayments: number
    daily: DailyPoint[]
    topSeries: { seriesId: string; name: string; revenue: number; payments: number }[]
  }
}

export interface QuestionStat {
  number: number
  questionId: string
  section: string
  question?: QuestionSummary
  attempted: number
  attemptRate: number
  correct: number
  incorrect: number
  partial: number
  accuracy: number
  avgTimeSeconds?: number
  flag?: 'TOO_HARD' | 'TOO_EASY' | 'SKIPPED'
}

export interface TestStats {
  testId: string
  title: string
  maxScore: number
  attemptsByStatus: Record<string, number>
  scores: {
    candidates: number
    average?: number
    median?: number
    highest?: number
    lowest?: number
    stdDev?: number
    averageAccuracy?: number
    averageTimeSeconds?: number
  }
  distribution: { from: number; to: number; count: number }[]
  questions: QuestionStat[]
}

export interface AdminResultRow {
  resultId: string
  attemptId: string
  userId: string
  studentName: string
  score: number
  maxScore: number
  rank?: number
  percentile?: number
  ranked: boolean
  accuracy: number
  timeTakenSeconds: number
  evaluatedAt: string
}
