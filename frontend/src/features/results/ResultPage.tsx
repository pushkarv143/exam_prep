import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Award, BookOpenCheck, Clock, Crosshair, Hourglass, Loader2, Percent, Trophy } from 'lucide-react'
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { attemptsApi } from '@/api/attempts'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatClock, formatDateTime, formatNumber } from '@/lib/format'
import type { Result } from '@/types/exam'

export const OUTCOME_COLORS = {
  correct: 'var(--success)', incorrect: 'var(--destructive)', partial: 'var(--warning)', unattempted: 'var(--muted-foreground)',
}

export const resultKeys = {
  result: (id: string) => ['result', id] as const,
  comparison: (id: string) => ['comparison', id] as const,
  solutions: (id: string) => ['solutions', id] as const,
}

export default function ResultPage() {
  const { attemptId = '' } = useParams()
  const result = useQuery({
    queryKey: resultKeys.result(attemptId),
    queryFn: () => attemptsApi.result(attemptId),
    // Evaluation normally finishes within seconds of submission; poll until it does.
    refetchInterval: (q) => (q.state.data?.status === 'EVALUATING' ? 2000 : false),
  })

  if (result.isError) return <ErrorState error={result.error} onRetry={() => result.refetch()} />
  if (result.isPending) return <PageLoader />
  const r = result.data

  if (r.status === 'EVALUATING') {
    return (
      <EmptyState icon={<Loader2 className="text-primary size-8 animate-spin" />} title="Evaluating your answers…"
                  description="Your test has been submitted. The result appears here in a few seconds." />
    )
  }
  if (r.status === 'AWAITING_PUBLICATION') {
    return (
      <EmptyState icon={<Hourglass className="text-muted-foreground size-8" />} title="Result not published yet"
                  description={r.solutionsAvailableAt
                    ? `Results are released after the test window closes on ${formatDateTime(r.solutionsAvailableAt)}.`
                    : 'Results are released after the test window closes.'}
                  action={<Button variant="outline" asChild><Link to="/dashboard">Back to dashboard</Link></Button>} />
    )
  }
  return <ResultView r={r} />
}

function ResultView({ r }: { r: Result }) {
  const comparison = useQuery({
    queryKey: resultKeys.comparison(r.attemptId),
    queryFn: () => attemptsApi.comparison(r.attemptId),
    enabled: r.ranked,
  })
  const pie = [
    { name: 'Correct', value: r.correct ?? 0, color: OUTCOME_COLORS.correct },
    { name: 'Incorrect', value: r.incorrect ?? 0, color: OUTCOME_COLORS.incorrect },
    { name: 'Partial', value: r.partial ?? 0, color: OUTCOME_COLORS.partial },
    { name: 'Not attempted', value: r.unattempted ?? 0, color: OUTCOME_COLORS.unattempted },
  ].filter((p) => p.value > 0)
  const sections = r.sections.map((s) => ({ name: s.name, score: Number(s.score), max: Number(s.maxScore) }))

  return (
    <>
      <PageHeader
        title={r.testTitle}
        description={`Attempt ${r.attemptNo}${r.evaluatedAt ? ` · evaluated ${formatDateTime(r.evaluatedAt)}` : ''}${r.ranked ? '' : ' · practice attempt (not ranked)'}`}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" asChild><Link to={`/tests/${r.testId}/leaderboard`}><Trophy /> Leaderboard</Link></Button>
            {r.solutionsAvailable ? (
              <Button asChild><Link to={`/attempts/${r.attemptId}/solutions`}><BookOpenCheck /> Solutions</Link></Button>
            ) : (
              <Button disabled title={r.solutionsAvailableAt ? `Available ${formatDateTime(r.solutionsAvailableAt)}` : undefined}>
                <BookOpenCheck /> Solutions {r.solutionsAvailableAt ? `from ${formatDateTime(r.solutionsAvailableAt)}` : 'later'}
              </Button>
            )}
          </div>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Stat icon={Award} label="Score" value={`${formatNumber(r.score)} / ${formatNumber(r.maxScore)}`}
              hint={`${formatNumber(r.percentage, 1)}%`} />
        <Stat icon={Trophy} label={r.rankFinal ? 'Rank' : 'Rank (provisional)'}
              value={r.rank != null ? `#${r.rank}` : '–'}
              hint={r.totalCandidates ? `of ${formatNumber(r.totalCandidates, 0)}` : undefined} />
        <Stat icon={Percent} label="Percentile" value={r.percentile != null ? formatNumber(r.percentile, 2) : '–'} />
        <Stat icon={Crosshair} label="Accuracy" value={`${formatNumber(r.accuracy, 1)}%`}
              hint={`${r.correct ?? 0} correct · ${r.incorrect ?? 0} wrong`} />
        <Stat icon={Clock} label="Time taken" value={formatClock(r.timeTakenSeconds ?? 0)} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[360px_1fr]">
        <Card>
          <CardHeader><CardTitle>Question outcomes</CardTitle></CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={pie} dataKey="value" nameKey="name" innerRadius={50} outerRadius={85} paddingAngle={2}>
                  {pie.map((p) => <Cell key={p.name} fill={p.color} />)}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Section scores</CardTitle></CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={sections} margin={{ top: 5, right: 12, bottom: 0, left: -12 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend />
                <Bar dataKey="score" name="Your score" fill="var(--primary)" radius={[4, 4, 0, 0]} />
                <Bar dataKey="max" name="Maximum" fill="var(--muted-foreground)" fillOpacity={0.3} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader><CardTitle>Section analysis</CardTitle></CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-muted-foreground border-b text-left">
                <th className="py-2 pr-2 font-medium">Section</th>
                <th className="px-2 text-right font-medium">Score</th>
                <th className="px-2 text-right font-medium">Attempted</th>
                <th className="px-2 text-right font-medium">Correct</th>
                <th className="px-2 text-right font-medium">Wrong</th>
                <th className="px-2 text-right font-medium">Accuracy</th>
                <th className="pl-2 text-right font-medium">Time</th>
              </tr>
            </thead>
            <tbody className="tabular-nums">
              {r.sections.map((s) => (
                <tr key={s.sectionId} className="border-b last:border-0">
                  <td className="py-2 pr-2">{s.name}</td>
                  <td className="px-2 text-right">{formatNumber(s.score)} / {formatNumber(s.maxScore)}</td>
                  <td className="px-2 text-right">{s.attempted} / {s.total}</td>
                  <td className="px-2 text-right">{s.correct}{s.partial ? ` (+${s.partial} partial)` : ''}</td>
                  <td className="px-2 text-right">{s.incorrect}</td>
                  <td className="px-2 text-right">{formatNumber(s.accuracy, 1)}%</td>
                  <td className="pl-2 text-right">{formatClock(s.timeSpentSeconds)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      {r.ranked && comparison.data && comparison.data.candidates > 1 && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle>You vs the topper and the average</CardTitle>
            <CardDescription>Among {formatNumber(comparison.data.candidates, 0)} ranked candidates</CardDescription>
          </CardHeader>
          <CardContent className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={comparison.data.sections.map((s) => ({
                name: s.name, You: Number(s.you), Topper: Number(s.topper ?? 0), Average: Number(s.average ?? 0),
              }))} margin={{ top: 5, right: 12, bottom: 0, left: -12 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip formatter={(v) => formatNumber(Number(v), 2)} />
                <Legend />
                <Bar dataKey="You" fill="var(--primary)" radius={[4, 4, 0, 0]} />
                <Bar dataKey="Topper" fill="var(--success)" radius={[4, 4, 0, 0]} />
                <Bar dataKey="Average" fill="var(--muted-foreground)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      )}

      {r.topics.length > 0 && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle>Topic-wise performance</CardTitle>
            <CardDescription>Weakest topics first</CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground border-b text-left">
                  <th className="py-2 pr-2 font-medium">Topic</th>
                  <th className="px-2 text-right font-medium">Questions</th>
                  <th className="px-2 text-right font-medium">Correct</th>
                  <th className="px-2 text-right font-medium">Score</th>
                  <th className="pl-2 text-right font-medium">Accuracy</th>
                </tr>
              </thead>
              <tbody className="tabular-nums">
                {[...r.topics].sort((a, b) => Number(a.accuracy) - Number(b.accuracy)).map((t) => (
                  <tr key={t.topicId} className="border-b last:border-0">
                    <td className="py-2 pr-2">
                      <p className="font-medium">{t.topicName ?? 'Untagged'}</p>
                      <p className="text-muted-foreground text-xs">{[t.subjectName, t.chapterName].filter(Boolean).join(' · ')}</p>
                    </td>
                    <td className="px-2 text-right">{t.attempted} / {t.total}</td>
                    <td className="px-2 text-right">{t.correct}</td>
                    <td className="px-2 text-right">{formatNumber(t.score)} / {formatNumber(t.maxScore)}</td>
                    <td className="pl-2 text-right">
                      <Badge variant={t.attempted === 0 ? 'muted' : Number(t.accuracy) < 50 ? 'destructive' : 'success'}>
                        {t.attempted === 0 ? '–' : `${formatNumber(t.accuracy, 0)}%`}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}
    </>
  )
}

function Stat({ icon: Icon, label, value, hint }: { icon: typeof Award; label: string; value: string; hint?: string }) {
  return (
    <Card className="gap-2 py-5">
      <CardContent className="flex items-start gap-3">
        <div className="bg-primary/10 text-primary rounded-lg p-2"><Icon className="size-5" /></div>
        <div className="min-w-0">
          <p className="text-muted-foreground text-sm">{label}</p>
          <p className="truncate text-xl font-semibold">{value}</p>
          {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  )
}
