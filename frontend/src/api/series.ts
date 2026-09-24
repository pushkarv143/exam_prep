import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost } from '@/lib/api'
import type { Page } from '@/types/api'
import type { Enrollment, PublicSeries, SeriesDetail } from '@/types/domain'

export interface SeriesQuery {
  examCode?: string
  free?: boolean
  q?: string
  page?: number
  size?: number
}

export const seriesApi = {
  list: (query: SeriesQuery) => apiGet<Page<PublicSeries>>('/public/series', query),
  detail: (slug: string) => apiGet<SeriesDetail>(`/public/series/${slug}`),
  mine: () => apiGet<PublicSeries[]>('/me/series'),
  enroll: (seriesId: string) => apiPost<Enrollment>(`/series/${seriesId}/enroll`),
}

export const seriesKeys = {
  all: ['series'] as const,
  list: (q: SeriesQuery) => ['series', 'list', q] as const,
  detail: (slug: string) => ['series', 'detail', slug] as const,
  mine: ['series', 'mine'] as const,
}

export function useSeriesList(query: SeriesQuery) {
  return useQuery({ queryKey: seriesKeys.list(query), queryFn: () => seriesApi.list(query),
    placeholderData: keepPreviousData })
}

export function useSeriesDetail(slug: string) {
  return useQuery({ queryKey: seriesKeys.detail(slug), queryFn: () => seriesApi.detail(slug) })
}

export function useMySeries(enabled = true) {
  return useQuery({ queryKey: seriesKeys.mine, queryFn: seriesApi.mine, enabled })
}

/** Free / batch enrollment. Refreshes every series view afterwards. */
export function useEnroll() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: seriesApi.enroll,
    onSuccess: () => qc.invalidateQueries({ queryKey: seriesKeys.all }),
  })
}
