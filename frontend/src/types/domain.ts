/**
 * Frontend mirrors of the backend DTOs. Decimal amounts arrive as JSON numbers, and all
 * timestamps as ISO-8601 strings (UTC).
 */

/** Role codes are data since Admin Portal 2.0 (built-ins: STUDENT, TEACHER, SUPER_ADMIN, CONTENT_MANAGER, ...). */
export type Role = string
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED'

export interface User {
  id: string
  email: string
  phone?: string
  fullName: string
  roles: Role[]
  status: UserStatus
  emailVerified: boolean
  avatarUrl?: string
  targetExamCode?: string
  city?: string
  state?: string
  lastLoginAt?: string
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt: string
  user: User
}

/**
 * First login step. With 2FA the server returns only a short-lived mfaToken; exchange it with
 * the 6-digit code at /auth/login/mfa. Otherwise it is a normal AuthResponse.
 */
export type LoginResponse = AuthResponse | { mfaRequired: true; mfaToken: string }

export function isMfaChallenge(r: LoginResponse): r is { mfaRequired: true; mfaToken: string } {
  return 'mfaRequired' in r && r.mfaRequired === true
}

export interface Exam {
  id: string
  code: string
  name: string
  description?: string
  active: boolean
  displayOrder: number
}

export type EnrollmentSource = 'FREE' | 'PAYMENT' | 'ADMIN' | 'BATCH'

export interface MyAccess {
  enrolled: boolean
  hasAccess: boolean
  source?: EnrollmentSource
  expiresAt?: string
}

export interface PublicSeries {
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
  testCount: number
  /** Absent for anonymous visitors. */
  myAccess?: MyAccess
}

export type ExamPattern = 'JEE_MAIN' | 'JEE_ADVANCED' | 'NEET' | 'CUSTOM'
export type TestAvailability = 'NOT_PUBLISHED' | 'UPCOMING' | 'OPEN' | 'CLOSED'

export interface PublicTest {
  id: string
  title: string
  description?: string
  pattern: ExamPattern
  durationMinutes: number
  totalMarks: number
  totalQuestions: number
  startAt?: string
  endAt?: string
  availability: TestAvailability
  free: boolean
  displayOrder: number
  /** Absent for anonymous visitors. */
  accessible?: boolean
}

export interface SeriesDetail {
  series: PublicSeries
  tests: PublicTest[]
}

export interface Enrollment {
  id: string
  userId: string
  seriesId: string
  source: EnrollmentSource
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED'
  enrolledAt: string
  expiresAt?: string
  paymentId?: string
  active: boolean
}

export interface Checkout {
  paymentId: string
  provider: 'RAZORPAY' | 'MOCK'
  keyId: string
  orderId: string
  amountMinor: number
  currency: string
  name: string
  description: string
  prefillName?: string
  prefillEmail?: string
  prefillContact?: string
  mock: boolean
}

export interface Payment {
  id: string
  seriesId: string
  amount: number
  currency: string
  provider: 'RAZORPAY' | 'MOCK'
  status: 'CREATED' | 'PAID' | 'FAILED' | 'REFUNDED'
  receipt: string
  providerOrderId?: string
  providerPaymentId?: string
  failureReason?: string
  paidAt?: string
  createdAt: string
}

export interface VerifyPaymentResponse {
  payment: Payment
  enrollment: Enrollment
}

// ---- analytics ----------------------------------------------------------------------

export interface AnalyticsSummary {
  testsTaken: number
  averagePercentage: number
  averageAccuracy: number
  bestPercentile?: number
  questionsAttempted: number
  totalTimeSeconds: number
}

export interface TrendPoint {
  attemptId: string
  testId: string
  testTitle: string
  score: number
  maxScore: number
  percentage: number
  rank?: number
  percentile?: number
  practice: boolean
  evaluatedAt: string
}

export interface SubjectStrength {
  subjectId: string
  subjectName?: string
  attempted: number
  correct: number
  incorrect: number
  accuracy: number
  scorePercentage: number
  timeSpentSeconds: number
}

export interface TopicStrength {
  topicId: string
  topicName?: string
  chapterName?: string
  subjectName?: string
  total: number
  attempted: number
  correct: number
  incorrect: number
  accuracy: number
  scorePercentage: number
  avgSecondsPerQuestion: number
}

export interface AnalyticsOverview {
  summary: AnalyticsSummary
  trend: TrendPoint[]
  subjects: SubjectStrength[]
  weakTopics: TopicStrength[]
  strongTopics: TopicStrength[]
  allTopics: TopicStrength[]
  minAttempts: number
}
