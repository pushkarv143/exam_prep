import { Link } from 'react-router-dom'
import { ArrowRight, Award, BookOpen, Crosshair, Gauge, TrendingUp } from 'lucide-react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAnalytics } from '@/api/analytics'
import { useMySeries } from '@/api/series'
import { PageHeader } from '@/components/layout/Layouts'
import { EmptyState, ErrorState } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatNumber, plural } from '@/lib/format'
import { useAuthStore } from '@/store/auth'
import type { AnalyticsOverview } from '@/types/domain'

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  const analytics = useAnalytics()
  const mySeries = useMySeries()

  return (
    <>
      <PageHeader
        title={`Hi, ${user?.fullName.split(' ')[0] ?? 'there'}`}
        description="Here is how your preparation is going."
        actions={<Button asChild><Link to="/series">Find a test series <ArrowRight /></Link></Button>}
      />

      {analytics.isError ? (
        <ErrorState error={analytics.error} onRetry={() => analytics.refetch()} />
      ) : analytics.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)}
        </div>
      ) : (
        <Overview data={analytics.data} />
      )}

      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">My test series</h2>
          <Link to="/my/series" className="text-primary text-sm hover:underline">View all</Link>
        </div>
        {mySeries.isPending ? <Skeleton className="h-24 rounded-xl" /> : mySeries.data?.length ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {mySeries.data.slice(0, 3).map((s) => (
              <Card key={s.id} className="gap-3 py-5">
                <CardHeader>
                  <CardTitle className="text-base"><Link to={`/series/${s.slug}`} className="hover:underline">{s.name}</Link></CardTitle>
                  <CardDescription>{plural(s.testCount, 'test')}{s.myAccess?.expiresAt ? ` · till ${formatDate(s.myAccess.expiresAt)}` : ''}</CardDescription>
                </CardHeader>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState icon={<BookOpen className="text-muted-foreground size-8" />} title="No test series yet"
                      description="Start with a free series to take your first mock."
                      action={<Button asChild variant="outline"><Link to="/series?type=free">Browse free series</Link></Button>} />
        )}
      </section>
    </>
  )
}

function Stat({ icon: Icon, label, value, hint }: { icon: typeof Gauge; label: string; value: string; hint?: string }) {
  return (
    <Card className="gap-2 py-5">
      <CardContent className="flex items-start gap-3">
        <div className="bg-primary/10 text-primary rounded-lg p-2"><Icon className="size-5" /></div>
        <div>
          <p className="text-muted-foreground text-sm">{label}</p>
          <p className="text-2xl font-semibold">{value}</p>
          {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  )
}

function Overview({ data }: { data: AnalyticsOverview }) {
  const { summary } = data
  if (summary.testsTaken === 0) {
    return (
      <EmptyState icon={<TrendingUp className="text-muted-foreground size-8" />} title="No tests taken yet"
                  description="Your scores, ranks and weak topics will appear here after your first mock test." />
    )
  }
  const chart = data.trend.map((t, i) => ({
    name: `#${i + 1}`, title: t.testTitle, percentage: Number(t.percentage), percentile: t.percentile ?? null,
  }))
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon={BookOpen} label="Tests taken" value={String(summary.testsTaken)} />
        <Stat icon={Gauge} label="Average score" value={`${formatNumber(summary.averagePercentage, 1)}%`} />
        <Stat icon={Crosshair} label="Accuracy" value={`${formatNumber(summary.averageAccuracy, 1)}%`}
              hint={`${summary.questionsAttempted} questions attempted`} />
        <Stat icon={Award} label="Best percentile" value={summary.bestPercentile != null ? formatNumber(summary.bestPercentile, 2) : '–'} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <Card>
          <CardHeader>
            <CardTitle>Score trend</CardTitle>
            <CardDescription>Score % across your tests (oldest to newest)</CardDescription>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chart} margin={{ top: 5, right: 12, bottom: 0, left: -16 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} unit="%" />
                <Tooltip formatter={(v) => [`${v}%`, 'Score']}
                         labelFormatter={(_, p) => p?.[0]?.payload?.title ?? ''} />
                <Line type="monotone" dataKey="percentage" stroke="var(--primary)" strokeWidth={2} dot={{ r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Focus areas</CardTitle>
            <CardDescription>Topics under 50% accuracy (with {data.minAttempts}+ attempts)</CardDescription>
          </CardHeader>
          <CardContent>
            {data.weakTopics.length === 0 ? (
              <p className="text-muted-foreground text-sm">
                No weak topics yet. Attempt more questions to get topic insights.
              </p>
            ) : (
              <ul className="space-y-3">
                {data.weakTopics.slice(0, 6).map((t) => (
                  <li key={t.topicId} className="flex items-center justify-between gap-3 text-sm">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{t.topicName}</p>
                      <p className="text-muted-foreground truncate text-xs">{t.subjectName} · {t.chapterName}</p>
                    </div>
                    <Badge variant="destructive">{formatNumber(t.accuracy, 0)}%</Badge>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Recent results</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="divide-y text-sm">
            {[...data.trend].reverse().slice(0, 5).map((t) => (
              <li key={t.attemptId} className="flex items-center justify-between gap-3 py-2.5">
                <div className="min-w-0">
                  <Link to={`/attempts/${t.attemptId}/result`} className="truncate font-medium hover:underline">{t.testTitle}</Link>
                  <p className="text-muted-foreground text-xs">
                    {formatDate(t.evaluatedAt)}{t.practice ? ' · practice' : t.rank ? ` · rank #${t.rank}` : ''}
                  </p>
                </div>
                <span className="shrink-0 tabular-nums">
                  {formatNumber(t.score)} / {formatNumber(t.maxScore)}
                  <span className="text-muted-foreground ml-2">({formatNumber(t.percentage, 1)}%)</span>
                </span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  )
}
