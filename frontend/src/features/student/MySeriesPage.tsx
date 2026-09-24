import { Link } from 'react-router-dom'
import { BookOpen } from 'lucide-react'
import { useExams } from '@/api/catalog'
import { useMySeries } from '@/api/series'
import { PageHeader } from '@/components/layout/Layouts'
import { EmptyState, ErrorState } from '@/components/common/States'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { SeriesCard } from '@/features/series/SeriesCard'

export default function MySeriesPage() {
  const mine = useMySeries()
  const exams = useExams()
  const examById = new Map((exams.data ?? []).map((e) => [e.id, e]))

  return (
    <>
      <PageHeader title="My test series" description="Series you are enrolled in."
                  actions={<Button variant="outline" asChild><Link to="/series">Browse more</Link></Button>} />
      {mine.isError ? (
        <ErrorState error={mine.error} onRetry={() => mine.refetch()} />
      ) : mine.isPending ? (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-56 rounded-xl" />)}
        </div>
      ) : mine.data.length === 0 ? (
        <EmptyState icon={<BookOpen className="text-muted-foreground size-8" />} title="You have not enrolled in any series"
                    action={<Button asChild><Link to="/series">Explore test series</Link></Button>} />
      ) : (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {mine.data.map((s) => (
            <div key={s.id} className="relative"><SeriesCard series={s} exam={examById.get(s.examId)} /></div>
          ))}
        </div>
      )}
    </>
  )
}
