import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, apiDelete, apiGet, apiPatch, apiPost, apiPut } from '@/lib/api'
import type { ApiEnvelope, Page } from '@/types/api'
import type {
  AddQuestionsResult, AdminDashboard, AdminResultRow, AdminSeries, AdminTest, AdminUser, Batch, CatalogTree,
  CreateTestRequest, CreateUserRequest, GenerationReport, GenerationRule, ImportReport, Language, PatternInfo,
  Question, QuestionFilter, QuestionRequest, QuestionSummary, SectionRequest, SeriesRequest, SeriesStatus, StoredFile,
  TestDetail, TestQuestion, TestSection, TestStats, TestStatus, UpdateTestRequest, UserStatus, ValidationReport,
} from '@/types/admin'
import type { Exam, Role } from '@/types/domain'

/** Drops empty filter values so they are not sent as `?x=`. */
function clean<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')) as Partial<T>
}

export const adminApi = {
  // catalog
  exams: () => apiGet<Exam[]>('/admin/catalog/exams'),
  tree: (examId: string) => apiGet<CatalogTree>(`/admin/catalog/exams/${examId}/tree`, { includeInactive: false }),

  // questions
  questions: (f: QuestionFilter) => apiGet<Page<QuestionSummary>>('/admin/questions', clean({ ...f, sort: 'createdAt,desc' })),
  question: (id: string) => apiGet<Question>(`/admin/questions/${id}`),
  createQuestion: (body: QuestionRequest) => apiPost<Question>('/admin/questions', body),
  updateQuestion: (id: string, body: QuestionRequest) => apiPut<Question>(`/admin/questions/${id}`, body),
  archiveQuestion: (id: string) => apiDelete<void>(`/admin/questions/${id}`),
  importQuestions: async (file: File, dryRun: boolean, autoCreateCatalog: boolean) => {
    const form = new FormData()
    form.append('file', file)
    const res = await api.post<ApiEnvelope<ImportReport>>('/admin/questions/import', form,
      { params: { dryRun, autoCreateCatalog }, timeout: 120_000 })
    return res.data.data as ImportReport
  },
  importTemplate: async () => {
    const res = await api.get<Blob>('/admin/questions/import/template', { responseType: 'blob' })
    return res.data
  },
  upload: async (file: File, category: 'QUESTION_IMAGE' | 'THUMBNAIL' | 'SOLUTION_PDF') => {
    const form = new FormData()
    form.append('file', file)
    const res = await api.post<ApiEnvelope<StoredFile>>('/admin/files', form, { params: { category }, timeout: 60_000 })
    return res.data.data as StoredFile
  },

  // tests
  patterns: () => apiGet<PatternInfo[]>('/admin/tests/patterns'),
  tests: (p: { seriesId?: string; examId?: string; status?: TestStatus; q?: string; page?: number; size?: number }) =>
    apiGet<Page<AdminTest>>('/admin/tests', clean({ ...p, sort: 'createdAt,desc' })),
  test: (id: string) => apiGet<TestDetail>(`/admin/tests/${id}`),
  createTest: (body: CreateTestRequest) => apiPost<TestDetail>('/admin/tests', body),
  updateTest: (id: string, body: UpdateTestRequest) => apiPut<TestDetail>(`/admin/tests/${id}`, body),
  deleteTest: (id: string) => apiDelete<void>(`/admin/tests/${id}`),
  validateTest: (id: string) => apiGet<ValidationReport>(`/admin/tests/${id}/validation`),
  testAction: (id: string, action: 'publish' | 'unpublish' | 'archive') => apiPost<AdminTest>(`/admin/tests/${id}/${action}`),
  addSection: (id: string, body: SectionRequest) => apiPost<TestSection>(`/admin/tests/${id}/sections`, body),
  updateSection: (id: string, sectionId: string, body: SectionRequest) =>
    apiPut<TestSection>(`/admin/tests/${id}/sections/${sectionId}`, body),
  deleteSection: (id: string, sectionId: string) => apiDelete<void>(`/admin/tests/${id}/sections/${sectionId}`),
  addQuestions: (id: string, sectionId: string, questionIds: string[]) =>
    apiPost<AddQuestionsResult>(`/admin/tests/${id}/sections/${sectionId}/questions`, { questionIds }),
  reorder: (id: string, sectionId: string, testQuestionIds: string[]) =>
    apiPut<void>(`/admin/tests/${id}/sections/${sectionId}/order`, { testQuestionIds }),
  updateTestQuestion: (id: string, tqId: string, body: { marks: number; negativeMarks: number; partialMarking: boolean }) =>
    apiPut<TestQuestion>(`/admin/tests/${id}/questions/${tqId}`, body),
  removeTestQuestion: (id: string, tqId: string) => apiDelete<void>(`/admin/tests/${id}/questions/${tqId}`),
  generate: (id: string, rules: GenerationRule[], strict: boolean, excludeUsedInPublishedTests: boolean) =>
    apiPost<GenerationReport>(`/admin/tests/${id}/generate`, { rules, strict, excludeUsedInPublishedTests }),
  generateFromPattern: (id: string, body: { easyPercent: number; mediumPercent: number; hardPercent: number;
    language?: Language; strict: boolean; excludeUsedInPublishedTests: boolean }) =>
    apiPost<GenerationReport>(`/admin/tests/${id}/generate-from-pattern`, body),

  // results & stats
  testStats: (id: string) => apiGet<TestStats>(`/admin/tests/${id}/stats`),
  testResults: (id: string, page: number) =>
    apiGet<Page<AdminResultRow>>(`/admin/tests/${id}/results`, { page, size: 50, sort: 'score,desc' }),
  finalizeRanks: (id: string) => apiPost<Record<string, number>>(`/admin/tests/${id}/rankings/finalize`),
  reEvaluate: (attemptId: string) => apiPost<Record<string, boolean>>(`/admin/attempts/${attemptId}/re-evaluate`),
  dashboard: () => apiGet<AdminDashboard>('/admin/dashboard'),

  // series & batches
  series: (p: { examId?: string; status?: SeriesStatus; q?: string; page?: number; size?: number }) =>
    apiGet<Page<AdminSeries>>('/admin/series', clean({ ...p, sort: 'createdAt,desc' })),
  createSeries: (body: SeriesRequest) => apiPost<AdminSeries>('/admin/series', body),
  updateSeries: (id: string, body: SeriesRequest) => apiPut<AdminSeries>(`/admin/series/${id}`, body),
  seriesAction: (id: string, action: 'publish' | 'unpublish' | 'archive') => apiPost<AdminSeries>(`/admin/series/${id}/${action}`),
  batches: () => apiGet<Page<Batch>>('/admin/batches', { size: 200 }),

  // users
  users: (p: { q?: string; status?: UserStatus; role?: Role; page?: number }) =>
    apiGet<Page<AdminUser>>('/admin/users', clean({ ...p, size: 25 })),
  createUser: (body: CreateUserRequest) => apiPost<AdminUser>('/admin/users', body),
  setUserStatus: (id: string, status: UserStatus) => apiPatch<AdminUser>(`/admin/users/${id}/status`, { status }),
  setUserRoles: (id: string, roles: Role[]) => apiPut<AdminUser>(`/admin/users/${id}/roles`, { roles }),
}

export const adminKeys = {
  exams: ['admin', 'exams'] as const,
  tree: (examId: string) => ['admin', 'tree', examId] as const,
  questions: (f: QuestionFilter) => ['admin', 'questions', f] as const,
  question: (id: string) => ['admin', 'question', id] as const,
  tests: (p: object) => ['admin', 'tests', p] as const,
  test: (id: string) => ['admin', 'test', id] as const,
  validation: (id: string) => ['admin', 'test', id, 'validation'] as const,
  stats: (id: string) => ['admin', 'test', id, 'stats'] as const,
  results: (id: string, page: number) => ['admin', 'test', id, 'results', page] as const,
  series: (p: object) => ['admin', 'series', p] as const,
  users: (p: object) => ['admin', 'users', p] as const,
}

export function useAdminExams() {
  return useQuery({ queryKey: adminKeys.exams, queryFn: adminApi.exams, staleTime: 10 * 60_000 })
}

export function useCatalogTree(examId: string | undefined) {
  return useQuery({
    queryKey: adminKeys.tree(examId ?? ''),
    queryFn: () => adminApi.tree(examId!),
    enabled: !!examId,
    staleTime: 5 * 60_000,
  })
}

export function usePatterns() {
  return useQuery({ queryKey: ['admin', 'patterns'], queryFn: adminApi.patterns, staleTime: Infinity })
}

export function useAdminQuestions(f: QuestionFilter, enabled = true) {
  return useQuery({ queryKey: adminKeys.questions(f), queryFn: () => adminApi.questions(f), placeholderData: keepPreviousData, enabled })
}

export function useAllSeries() {
  return useQuery({ queryKey: adminKeys.series({ all: true }), queryFn: () => adminApi.series({ size: 200 }), staleTime: 60_000 })
}

/** Triggers a browser download of a Blob. */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
