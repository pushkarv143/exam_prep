import { Link } from 'react-router-dom'
import { CalendarClock, CheckCircle2, FileText } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate, formatPrice, plural } from '@/lib/format'
import type { Exam, PublicSeries } from '@/types/domain'

export function validityText(s: PublicSeries): string | null {
  if (s.validityDays) return `${s.validityDays} days access`
  if (s.validUntil) return `Access till ${formatDate(s.validUntil)}`
  return null
}

export function SeriesCard({ series, exam }: { series: PublicSeries; exam?: Exam }) {
  const access = series.myAccess
  return (
    <Card className="group hover:border-primary/40 h-full gap-4 transition-shadow hover:shadow-md">
      <CardHeader className="gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {exam && <Badge variant="secondary">{exam.name}</Badge>}
          {series.free ? <Badge variant="success">Free</Badge> : null}
          {access?.hasAccess && <Badge variant="success"><CheckCircle2 /> Enrolled</Badge>}
        </div>
        <CardTitle className="text-lg leading-snug">
          <Link to={`/series/${series.slug}`} className="after:absolute after:inset-0 group-hover:underline">
            {series.name}
          </Link>
        </CardTitle>
      </CardHeader>
      <CardContent className="text-muted-foreground flex-1 space-y-2 text-sm">
        {series.description && <p className="line-clamp-2">{series.description}</p>}
        <div className="flex flex-wrap gap-x-4 gap-y-1">
          <span className="flex items-center gap-1"><FileText className="size-4" /> {plural(series.testCount, 'test')}</span>
          {validityText(series) && (
            <span className="flex items-center gap-1"><CalendarClock className="size-4" /> {validityText(series)}</span>
          )}
        </div>
      </CardContent>
      <CardFooter className="justify-between">
        <span className="text-xl font-semibold" data-testid="series-price">
          {series.free ? 'Free' : formatPrice(series.price)}
        </span>
        <span className="text-primary text-sm font-medium">View details →</span>
      </CardFooter>
    </Card>
  )
}
