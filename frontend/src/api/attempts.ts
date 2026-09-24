import { useQuery } from '@tanstack/react-query'
import { API_BASE_URL, apiGet, apiPost, apiPut } from '@/lib/api'
import { useAuthStore } from '@/store/auth'
import type {
  AnswerChange, AntiCheat, AttemptEventType, AttemptSession, AttemptSummary, AutosaveResult, Comparison,
  Leaderboard, Result, SolutionReview, TestInfo,
} from '@/types/exam'

export const attemptsApi = {
  testInfo: (testId: string) => apiGet<TestInfo>(`/tests/${testId}`),
  myAttempts: (testId: string) => apiGet<AttemptSummary[]>(`/tests/${testId}/attempts`),
  start: (testId: string) => apiPost<AttemptSession>(`/tests/${testId}/attempts`),
  session: (attemptId: string) => apiGet<AttemptSession>(`/attempts/${attemptId}`),
  autosave: (attemptId: string, changes: AnswerChange[]) =>
    apiPut<AutosaveResult>(`/attempts/${attemptId}/answers`, { changes }),
  events: (attemptId: string, events: { type: AttemptEventType; clientTs: string; details?: Record<string, string> }[]) =>
    apiPost<AntiCheat>(`/attempts/${attemptId}/events`, { events }),
  submit: (attemptId: string) => apiPost<AttemptSummary>(`/attempts/${attemptId}/submit`),
  result: (attemptId: string) => apiGet<Result>(`/attempts/${attemptId}/result`),
  solutions: (attemptId: string) => apiGet<SolutionReview>(`/attempts/${attemptId}/solutions`),
  comparison: (attemptId: string) => apiGet<Comparison>(`/attempts/${attemptId}/comparison`),
  leaderboard: (testId: string) => apiGet<Leaderboard>(`/tests/${testId}/leaderboard`, { limit: 100 }),
}

/**
 * Last-chance save while the page is closing. `fetch(..., {keepalive: true})` survives
 * page unload (unlike axios), and unlike sendBeacon it can carry the Authorization header.
 */
export function autosaveKeepalive(attemptId: string, changes: AnswerChange[]) {
  const token = useAuthStore.getState().accessToken
  if (!token || changes.length === 0) return
  try {
    void fetch(`${API_BASE_URL}/attempts/${attemptId}/answers`, {
      method: 'PUT',
      keepalive: true,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ changes }),
    })
  } catch {
    // Best effort. The localStorage backup is resent on the next load.
  }
}

export function useTestInfo(testId: string) {
  return useQuery({ queryKey: ['test-info', testId], queryFn: () => attemptsApi.testInfo(testId) })
}

export function useMyAttempts(testId: string) {
  return useQuery({ queryKey: ['my-attempts', testId], queryFn: () => attemptsApi.myAttempts(testId) })
}
