import { useQueries } from '@tanstack/react-query'
import { seriesApi, seriesKeys, useMySeries } from '@/api/series'
import type { PublicTest } from '@/types/domain'

export interface UpcomingTest extends PublicTest {
  seriesSlug: string
  seriesName: string
  startMs: number
}

/**
 * Aggregates upcoming/open live tests across the student's enrolled series. There is no single
 * "upcoming tests" endpoint, so this fans out over series details (cached by React Query).
 * Limited to the first 8 series to keep the dashboard light.
 */
export function useUpcomingTests(limitSeries = 8) {
  const mine = useMySeries()
  const slugs = (mine.data ?? []).slice(0, limitSeries)

  const details = useQueries({
    queries: slugs.map((s) => ({
      queryKey: seriesKeys.detail(s.slug),
      queryFn: () => seriesApi.detail(s.slug),
      staleTime: 60_000,
    })),
  })

  const now = Date.now()
  const tests: UpcomingTest[] = []
  for (const d of details) {
    const detail = d.data
    if (!detail) continue
    for (const t of detail.tests) {
      if (!t.startAt) continue
      const startMs = Date.parse(t.startAt)
      const endMs = t.endAt ? Date.parse(t.endAt) : Infinity
      // Live tests that are open now or start in the future and haven't closed.
      const relevant = (t.availability === 'UPCOMING' || t.availability === 'OPEN') && endMs > now
      if (relevant) {
        tests.push({ ...t, seriesSlug: detail.series.slug, seriesName: detail.series.name, startMs })
      }
    }
  }
  tests.sort((a, b) => a.startMs - b.startMs)

  return {
    tests,
    isPending: mine.isPending || (slugs.length > 0 && details.some((d) => d.isPending)),
    isError: mine.isError || details.some((d) => d.isError),
  }
}
