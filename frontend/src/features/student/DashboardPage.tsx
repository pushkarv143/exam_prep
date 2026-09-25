import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight, Bell, BellRing, BookOpen, CalendarClock, Crosshair, Flame, Gauge, PlayCircle,
  Sparkles, Target, TrendingUp,
} from 'lucide-react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAnalytics } from '@/api/analytics'
import { useMySeries } from '@/api/series'
import { PageHeader } from '@/components/layout/Layouts'
import { EmptyState, ErrorState } from '@/components/common/States'
import { Countdown, formatCountdown, useCountdown } from '@/components/common/Countdown'
import { Sparkline } from '@/components/common/Sparkline'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatDateTime, formatNumber, plural } from '@/lib/format'
import { computeStreak } from '@/lib/streak'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/auth'
import { readActiveAttempt } from '@/features/exam/activeAttempt'
import type { AnalyticsOverview } from '@/types/domain'
import { useReminders } from './reminders'
import { useUpcomingTests, type UpcomingTest } from './useUpcomingTests'

function greeting(hour: number): string {
  if (hour < 5) return 'Still up'
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  const analytics = useAnalytics()
  const mySeries = useMySeries()
  const upcoming = useUpcomingTests()
  const active = readActiveAttempt()
  const firstName = user?.fullName.split(' ')[0] ?? 'there'

  return (
    <>
      <PageHeader
        title={`${greeting(new Date().getHours())}, ${firstName}`}
        description="Here is your plan for today and how your preparation is going."
        actions={<Button asChild><Link to="/series">Find a test series <ArrowRight /></Link></Button>}
      />

      {active && <ContinueCard title={active.title} attemptId={active.attemptId} deadlineMs={active.deadlineMs} />}

      {analytics.isError ? (
        <ErrorState error={analytics.error} onRetry={() => analytics.refetch()} />
      ) : analytics.isPending ? (
        <DashboardSkeleton />
      ) : (
        <Overview data={analytics.data} upcoming={upcoming} hasActive={!!active} />
      )}

      <UpcomingLiveTests upcoming={upcoming} />

      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">My test series</h2>
          <Link to="/my/series" className="text-primary text-sm hover:underline">View all</Link>
        </div>
        {mySeries.isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
          </div>
        ) : mySeries.data?.length ? (
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

function ContinueCard({ title, attemptId, deadlineMs }: { title: string; attemptId: string; deadlineMs: number }) {
  const left = useCountdown(deadlineMs)
  return (
    <Card className="border-primary/40 bg-primary/5 mb-6 animate-in fade-in slide-in-from-top-2 duration-500">
      <CardContent className="flex flex-col items-start gap-4 p-5 sm:flex-row sm:items-center">
        <div className="bg-primary/15 text-primary grid size-12 shrink-0 place-items-center rounded-xl">
          <PlayCircle className="size-6" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Continue where you left off</p>
          <p className="truncate font-semibold">{title}</p>
          <p className="text-muted-foreground text-sm">
            {left > 0 ? <>Time left: <span className="text-foreground font-medium tabular-nums">{formatCountdown(left)}</span></> : 'Test window ended'}
          </p>
        </div>
        <Button asChild size="lg" className="w-full sm:w-auto">
          <Link to={`/exam/${attemptId}`}>Resume test <ArrowRight /></Link>
        </Button>
      </CardContent>
    </Card>
  )
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)}
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <Skeleton className="h-72 rounded-xl" />
        <Skeleton className="h-72 rounded-xl" />
      </div>
    </div>
  )
}

function Overview({ data, upcoming, hasActive }:
  { data: AnalyticsOverview; upcoming: ReturnType<typeof useUpcomingTests>; hasActive: boolean }) {
  const { summary } = data
  const streak = useMemo(() => computeStreak(data.trend), [data.trend])
  const percentiles = data.trend.map((t) => t.percentile).filter((p): p is number => p != null)
  const weakest = data.weakTopics[0]

  if (summary.testsTaken === 0) {
    return (
      <>
        <RecommendedAction data={data} upcoming={upcoming} hasActive={hasActive} streak={0} />
        <EmptyState icon={<TrendingUp className="text-muted-foreground size-8" />} title="No tests taken yet"
                    description="Your scores, ranks and weak topics will appear here after your first mock test." />
      </>
    )
  }

  const chart = data.trend.map((t, i) => ({
    name: `#${i + 1}`, title: t.testTitle, percentage: Number(t.percentage), percentile: t.percentile ?? null,
  }))

  return (
    <div className="space-y-6">
      <RecommendedAction data={data} upcoming={upcoming} hasActive={hasActive} streak={streak} />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon={Flame} iconClass="text-orange-500 bg-orange-500/10" label="Study streak"
              value={streak > 0 ? plural(streak, 'day') : 'Start today'}
              hint={streak > 0 ? 'Keep it going!' : 'Take a test to begin'} />
        <Stat icon={BookOpen} label="Tests taken" value={String(summary.testsTaken)}
              hint={`${summary.questionsAttempted} questions attempted`} />
        <Card className="gap-2 py-5">
          <CardContent className="flex items-start gap-3">
            <div className="bg-primary/10 text-primary rounded-lg p-2"><Gauge className="size-5" /></div>
            <div className="min-w-0 flex-1">
              <p className="text-muted-foreground text-sm">Percentile trend</p>
              <p className="text-2xl font-semibold">{summary.bestPercentile != null ? formatNumber(summary.bestPercentile, 1) : '–'}</p>
              {percentiles.length >= 2
                ? <Sparkline values={percentiles} />
                : <p className="text-muted-foreground text-xs">Best percentile so far</p>}
            </div>
          </CardContent>
        </Card>
        <Stat icon={Crosshair} label="Accuracy" value={`${formatNumber(summary.averageAccuracy, 1)}%`}
              hint={`Avg score ${formatNumber(summary.averagePercentage, 1)}%`} />
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

      {weakest && (
        <p className="text-muted-foreground text-sm">
          <Target className="mr-1 inline size-4" />
          Weakest topic right now: <span className="text-foreground font-medium">{weakest.topicName}</span>
          {' '}({formatNumber(weakest.accuracy, 0)}% accuracy).{' '}
          <Link to={`/series?q=${encodeURIComponent(weakest.topicName ?? '')}`} className="text-primary hover:underline">Practise it →</Link>
        </p>
      )}
    </div>
  )
}

/** One clear "do this next" card, chosen from live tests, weak topics and history. */
function RecommendedAction({ data, upcoming, hasActive, streak }:
  { data: AnalyticsOverview; upcoming: ReturnType<typeof useUpcomingTests>; hasActive: boolean; streak: number }) {
  if (hasActive) return null

  const now = Date.now()
  const openNow = upcoming.tests.find((t) => t.availability === 'OPEN')
  const soon = upcoming.tests.find((t) => t.startMs > now && t.startMs - now < 24 * 3600_000)
  const weak = data.weakTopics[0]

  let title: string
  let desc: string
  let to: string
  let cta: string
  if (openNow) {
    title = 'A live test is open now'
    desc = `${openNow.title} · ${openNow.durationMinutes} min · ${openNow.totalMarks} marks`
    to = `/tests/${openNow.id}`
    cta = 'Start now'
  } else if (soon) {
    title = 'Your next live test is coming up'
    desc = `${soon.title} starts in ${formatCountdown(soon.startMs - now)}`
    to = `/tests/${soon.id}`
    cta = 'View test'
  } else if (weak) {
    title = `Strengthen ${weak.topicName}`
    desc = `This is your weakest topic at ${formatNumber(weak.accuracy, 0)}% accuracy. A focused set will help most.`
    to = `/series?q=${encodeURIComponent(weak.topicName ?? '')}`
    cta = 'Practise now'
  } else if (data.summary.testsTaken === 0) {
    title = 'Take your first mock test'
    desc = 'Pick a free series and attempt a full-length paper to unlock analysis.'
    to = '/series?type=free'
    cta = 'Browse free series'
  } else {
    title = 'Keep your momentum going'
    desc = streak > 0 ? `You are on a ${plural(streak, 'day')} streak — take another mock to extend it.` : 'Attempt a new mock test to keep improving.'
    to = '/series'
    cta = 'Find a test'
  }

  return (
    <Card className="from-primary/10 to-accent/30 border-primary/30 bg-gradient-to-br">
      <CardContent className="flex flex-col items-start gap-4 p-5 sm:flex-row sm:items-center">
        <div className="bg-primary/15 text-primary grid size-11 shrink-0 place-items-center rounded-xl">
          <Sparkles className="size-5" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Recommended next</p>
          <p className="font-semibold">{title}</p>
          <p className="text-muted-foreground text-sm">{desc}</p>
        </div>
        <Button asChild className="w-full sm:w-auto"><Link to={to}>{cta} <ArrowRight /></Link></Button>
      </CardContent>
    </Card>
  )
}

function UpcomingLiveTests({ upcoming }: { upcoming: ReturnType<typeof useUpcomingTests> }) {
  const reminders = useReminders()
  if (upcoming.isPending) {
    return (
      <section className="mt-8">
        <h2 className="mb-3 text-lg font-semibold">Upcoming live tests</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)}
        </div>
      </section>
    )
  }
  if (upcoming.tests.length === 0) return null

  return (
    <section className="mt-8">
      <div className="mb-3 flex items-center gap-2">
        <CalendarClock className="text-primary size-5" />
        <h2 className="text-lg font-semibold">Upcoming live tests</h2>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {upcoming.tests.slice(0, 4).map((t) => (
          <UpcomingCard key={t.id} t={t} on={reminders.isOn(t.id)} onToggle={() => reminders.toggle(t.id)} />
        ))}
      </div>
    </section>
  )
}

function UpcomingCard({ t, on, onToggle }: { t: UpcomingTest; on: boolean; onToggle: () => void }) {
  const isOpen = t.availability === 'OPEN'
  return (
    <Card className="gap-3 py-5">
      <CardContent className="space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate font-semibold">{t.title}</p>
            <p className="text-muted-foreground text-xs">{t.seriesName} · {t.durationMinutes} min · {t.totalMarks} marks</p>
          </div>
          <Badge variant={isOpen ? 'success' : 'secondary'}>{isOpen ? 'Live now' : 'Upcoming'}</Badge>
        </div>
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm">
            {isOpen ? (
              <span className="text-success font-medium">Open now{t.endAt ? ` · closes ${formatDateTime(t.endAt)}` : ''}</span>
            ) : (
              <span className="text-muted-foreground">
                Starts in <Countdown target={t.startMs} className="text-foreground font-medium tabular-nums" />
              </span>
            )}
          </p>
          <div className="flex items-center gap-1">
            {!isOpen && (
              <Button variant="ghost" size="sm" aria-pressed={on} onClick={onToggle}
                      className={cn('h-8 px-2', on && 'text-primary')}
                      title={on ? 'Reminder on' : 'Remind me'}>
                {on ? <BellRing className="size-4" /> : <Bell className="size-4" />}
                <span className="ml-1 text-xs">{on ? 'Reminder on' : 'Remind me'}</span>
              </Button>
            )}
            <Button asChild size="sm" variant={isOpen ? 'success' : 'outline'}>
              <Link to={`/tests/${t.id}`}>{isOpen ? 'Start' : 'Details'}</Link>
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function Stat({ icon: Icon, label, value, hint, iconClass }:
  { icon: typeof Gauge; label: string; value: string; hint?: string; iconClass?: string }) {
  return (
    <Card className="gap-2 py-5">
      <CardContent className="flex items-start gap-3">
        <div className={cn('rounded-lg p-2', iconClass ?? 'bg-primary/10 text-primary')}><Icon className="size-5" /></div>
        <div className="min-w-0">
          <p className="text-muted-foreground text-sm">{label}</p>
          <p className="text-2xl font-semibold">{value}</p>
          {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  )
}
