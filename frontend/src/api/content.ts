import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiGet, apiPost, apiPut } from '@/lib/api'
import type { Question, QuestionSnapshot } from '@/types/admin'
import type {
  BulkAction, BulkResult, ContentSettings, PublishResult, QuestionActivity, QuestionVersion, QueueCounts, Reviewer,
} from './types'

/**
 * Content studio API (A2): review workflow, comments, version history and queues.
 * Workflow calls return the updated question so the studio re-renders from one response.
 */
export interface PublishOutcome {
  question: Question
  published?: PublishResult
}

export interface VersionDetail {
  info: QuestionVersion
  snapshot: QuestionSnapshot
}

const q = (id: string) => `/admin/questions/${id}`

export const contentApi = {
  submit: (id: string, body: { assigneeId?: string; note?: string } = {}) => apiPost<Question>(`${q(id)}/submit`, body),
  claim: (id: string) => apiPost<Question>(`${q(id)}/claim`),
  assign: (id: string, assigneeId: string | null) => apiPost<Question>(`${q(id)}/assign`, { assigneeId }),
  requestChanges: (id: string, comment: string) => apiPost<Question>(`${q(id)}/request-changes`, { comment }),
  approve: (id: string, comment: string, publish: boolean) =>
    apiPost<PublishOutcome>(`${q(id)}/approve`, { comment: comment || undefined, publish }),
  publish: (id: string, propagate: boolean) => apiPost<PublishOutcome>(`${q(id)}/publish`, { propagate }),
  restore: (id: string) => apiPost<Question>(`${q(id)}/restore`),
  reviewers: (id: string) => apiGet<Reviewer[]>(`${q(id)}/reviewers`),
  bulk: (action: BulkAction, ids: string[], comment?: string) =>
    apiPost<BulkResult>('/admin/questions/bulk', { action, ids, comment }),

  activity: (id: string) => apiGet<QuestionActivity[]>(`${q(id)}/activity`),
  comment: (id: string, body: string, field?: string) =>
    apiPost<QuestionActivity[]>(`${q(id)}/comments`, { body, field: field || undefined }),
  resolveComment: (id: string, commentId: string, resolved: boolean) =>
    apiPost<QuestionActivity[]>(`${q(id)}/comments/${commentId}/resolve?resolved=${resolved}`),

  versions: (id: string) => apiGet<QuestionVersion[]>(`${q(id)}/versions`),
  version: (id: string, version: number) => apiGet<VersionDetail>(`${q(id)}/versions/${version}`),
  restoreVersion: (id: string, version: number, note?: string) =>
    apiPost<Question>(`${q(id)}/versions/${version}/restore`, { note: note || undefined }),

  queues: () => apiGet<QueueCounts>('/admin/questions/queues'),
  subTopics: (topicId: string) => apiGet<string[]>('/admin/questions/sub-topics', { topicId }),
  settings: () => apiGet<ContentSettings>('/admin/content/settings'),
  updateSettings: (body: ContentSettings) => apiPut<ContentSettings>('/admin/content/settings', body),

  updateTestVersions: (testId: string) => apiPost<{ updated: number }>(`/admin/tests/${testId}/questions/update-versions`),
}

export const contentKeys = {
  activity: (id: string) => ['admin', 'question', id, 'activity'] as const,
  versions: (id: string) => ['admin', 'question', id, 'versions'] as const,
  version: (id: string, v: number) => ['admin', 'question', id, 'version', v] as const,
  reviewers: (id: string) => ['admin', 'question', id, 'reviewers'] as const,
  queues: ['admin', 'questions', 'queues'] as const,
  subTopics: (topicId: string) => ['admin', 'sub-topics', topicId] as const,
  settings: ['admin', 'content', 'settings'] as const,
}

export function useQueueCounts(enabled = true) {
  return useQuery({ queryKey: contentKeys.queues, queryFn: contentApi.queues, enabled, refetchInterval: 60_000,
    placeholderData: keepPreviousData })
}
