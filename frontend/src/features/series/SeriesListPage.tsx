import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Search } from 'lucide-react'
import { useExams } from '@/api/catalog'
import { useSeriesList } from '@/api/series'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { SeriesCard } from './SeriesCard'

const PAGE_SIZE = 12

/** Filters live in the URL (?exam=JEE_MAIN&type=free&q=…&page=1), so results are shareable and survive refresh. */
export default function SeriesListPage() {
  const [params, setParams] = useSearchParams()
  const exam = params.get('exam') ?? ''
  const type = params.get('type') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? '1') - 1)
  const [search, setSearch] = useState(params.get('q') ?? '')

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    if (!('page' in patch)) next.delete('page')
    setParams(next, { replace: true })
  }

  // Debounce typing, so each keystroke does not fire a request.
  useEffect(() => {
    const t = setTimeout(() => {
      if ((params.get('q') ?? '') !== search.trim()) update({ q: search.trim() })
    }, 350)
    return () => clearTimeout(t)
  }, [search])

  const exams = useExams()
  const examById = useMemo(() => new Map((exams.data ?? []).map((e) => [e.id, e])), [exams.data])
  const list = useSeriesList({
    examCode: exam || undefined,
    free: type === 'free' ? true : type === 'paid' ? false : undefined,
    q: params.get('q') || undefined,
    page,
    size: PAGE_SIZE,
  })

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <div className="mb-8">
        <h1 className="text-3xl font-semibold tracking-tight">Test series</h1>
        <p className="text-muted-foreground mt-2">Full-length and chapter-wise mocks on the latest exam patterns.</p>
      </div>

      <div className="mb-6 grid gap-3 sm:grid-cols-[1fr_200px_160px]">
        <div className="relative">
          <Search className="text-muted-foreground absolute top-1/2 left-3 size-4 -translate-y-1/2" />
          <Input className="pl-9" placeholder="Search test series" value={search} aria-label="Search test series"
                 onChange={(e) => setSearch(e.target.value)} />
        </div>
        <NativeSelect value={exam} onChange={(e) => update({ exam: e.target.value })} aria-label="Exam">
          <option value="">All exams</option>
          {exams.data?.map((e) => <option key={e.code} value={e.code}>{e.name}</option>)}
        </NativeSelect>
        <NativeSelect value={type} onChange={(e) => update({ type: e.target.value })} aria-label="Price">
          <option value="">Free &amp; paid</option>
          <option value="free">Free only</option>
          <option value="paid">Paid only</option>
        </NativeSelect>
      </div>

      {list.isError ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} />
      ) : list.isPending ? (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-56 rounded-xl" />)}
        </div>
      ) : list.data.content.length === 0 ? (
        <EmptyState title="No test series found" description="Try a different exam or clear the search." />
      ) : (
        <div className={list.isPlaceholderData ? 'opacity-60 transition-opacity' : undefined}>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {list.data.content.map((s) => (
              <div key={s.id} className="relative"><SeriesCard series={s} exam={examById.get(s.examId)} /></div>
            ))}
          </div>
          <div className="mt-8">
            <Pagination page={list.data.page} totalPages={list.data.totalPages}
                        onChange={(p) => update({ page: String(p + 1) })} />
          </div>
        </div>
      )}
    </div>
  )
}
