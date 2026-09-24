import { useQuery } from '@tanstack/react-query'
import { apiGet } from '@/lib/api'
import type { AnalyticsOverview } from '@/types/domain'

export const analyticsApi = {
  overview: (examCode?: string) => apiGet<AnalyticsOverview>('/me/analytics', examCode ? { examCode } : undefined),
}

export function useAnalytics(examCode?: string) {
  return useQuery({ queryKey: ['analytics', examCode ?? 'all'], queryFn: () => analyticsApi.overview(examCode) })
}
