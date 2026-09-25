import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { BookOpenCheck, Hourglass, Loader2, MessageCircleQuestion, Trophy } from 'lucide-react'
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ReferenceLine,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { attemptsApi, useTestInfo } from '@/api/attempts'
import { useAnalytics } from '@/api/analytics'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatClock, formatDateTime, formatNumber } from '@/lib/format'
import { OUTCOME_COLORS, resultKeys } from './resultColors'
import { ScoreReveal } from './components/ScoreReveal'
import { TimeInsights } from './components/TimeInsights'
import { ShareResult } from './components/ShareCard'
import type { Result } from '@/types/exam'

export { OUTCOME_COLORS, resultKeys }

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
  const navigate = useNavigate()
  const comparison = useQuery({
    queryKey: resultKeys.comparison(r.attemptId),
    queryFn: () => attemptsApi.comparison(r.attemptId),
    enabled: r.ranked,
  })
  const testInfo = useTestInfo(r.testId)
  const solutions = useQuery({
    queryKey: resultKeys.solutions(r.attemptId),
    queryFn: () => attemptsApi.solutions(r.attemptId),
    enabled: r.solutionsAvailable,
  })
  const analytics = useAnalytics()

  const goSolutions = (filter?: string) =>
    navigate(`/attempts/${r.attemptId}/solutions${filter ? `?filter=${filter}` : ''}`)

  const pie = [
    { name: 'Correct', key: 'CORRECT', value: r.correct ?? 0, color: OUTCOME_COLORS.correct },
    { name: 'Incorrect', key: 'INCORRECT', value: r.incorrect ?? 0, color: OUTCOME_COLORS.incorrect },
    { name: 'Partial', key: 'PARTIAL', value: r.partial ?? 0, color: OUTCOME_COLORS.partial },
    { name: 'Not attempted', key: 'UNATTEMPTED', value: r.unattempted ?? 0, color: OUTCOME_COLORS.unattempted },
  ].filter((p) => p.value > 0)
  const sections = r.sections.map((s) => ({ name: s.name, score: Number(s.score), max: Number(s.maxScore) }))
  const reviewItems = solutions.data?.sections.flatMap((s) => s.questions) ?? []

  // "You vs your past attempts" on this same test, with topper/class markers.
  const pastAttempts = (analytics.data?.trend ?? [])
    .filter((t) => t.testId === r.testId)
    .map((t, i) => ({ name: `Attempt ${i + 1}`, attemptId: t.attemptId, percentage: Number(t.percentage), you: t.attemptId === r.attemptId }))
  const showTrend = pastAttempts.length >= 2
  const maxScore = Number(r.maxScore) || 1
  const topperPct = comparison.data ? (Number(comparison.data.topper.score ?? 0) / maxScore) * 100 : undefined
  const averagePct = comparison.data ? (Number(comparison.data.average.score ?? 0) / maxScore) * 100 : undefined

  return (
    <>
      <PageHeader
        title={r.testTitle}
        description={`Attempt ${r.attemptNo}${r.evaluatedAt ? ` · evaluated ${formatDateTime(r.evaluatedAt)}` : ''}${r.ranked ? '' : ' · practice attempt (not ranked)'}`}
        actions={
          <div className="flex flex-wrap gap-2">
            <ShareResult r={r} />
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

      <ScoreReveal r={r} />

      <div className="mt-6 grid gap-6 lg:grid-cols-[360px_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Question outcomes</CardTitle>
            <CardDescription>Tap a slice to review those questions</CardDescription>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={pie} dataKey="value" nameKey="name" innerRadius={50} outerRadius={85} paddingAngle={2}
                     onClick={(d) => goSolutions((d?.payload as { key?: string })?.key)} className="cursor-pointer">
                  {pie.map((p) => <Cell key={p.name} fill={p.color} />)}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Section scores</CardTitle>
            <CardDescription>Your score against the maximum in each section</CardDescription>
          </CardHeader>
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

      {showTrend && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle>You vs your past attempts</CardTitle>
            <CardDescription>
              Your score % on this test over attempts{topperPct != null ? ', against the topper and class average' : ''}
            </CardDescription>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={pastAttempts} margin={{ top: 5, right: 12, bottom: 0, left: -16 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 12 }} />
                <Tooltip formatter={(v) => [`${formatNumber(Number(v), 1)}%`, 'Score']} />
                {topperPct != null && (
                  <ReferenceLine y={topperPct} stroke="var(--success)" strokeDasharray="4 4"
                                 label={{ value: 'Topper', position: 'right', fontSize: 10, fill: 'var(--success)' }} />
                )}
                {averagePct != null && (
                  <ReferenceLine y={averagePct} stroke="var(--muted-foreground)" strokeDasharray="4 4"
                                 label={{ value: 'Class avg', position: 'right', fontSize: 10, fill: 'var(--muted-foreground)' }} />
                )}
                <Line type="monotone" dataKey="percentage" stroke="var(--primary)" strokeWidth={2} dot={{ r: 4 }} />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      )}

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

      {r.solutionsAvailable && (
        <TimeInsights items={reviewItems} attemptId={r.attemptId}
                      durationSeconds={(testInfo.data?.durationMinutes ?? 0) * 60}
                      totalMarks={Number(r.maxScore)} />
      )}

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
            <CardDescription>Weakest topics first · practise the ones in red</CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground border-b text-left">
                  <th className="py-2 pr-2 font-medium">Topic</th>
                  <th className="px-2 text-right font-medium">Questions</th>
                  <th className="px-2 text-right font-medium">Correct</th>
                  <th className="px-2 text-right font-medium">Score</th>
                  <th className="px-2 text-right font-medium">Accuracy</th>
                  <th className="pl-2 text-right font-medium">Practise</th>
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
                    <td className="px-2 text-right">
                      <Badge variant={t.attempted === 0 ? 'muted' : Number(t.accuracy) < 50 ? 'destructive' : 'success'}>
                        {t.attempted === 0 ? '–' : `${formatNumber(t.accuracy, 0)}%`}
                      </Badge>
                    </td>
                    <td className="pl-2 text-right">
                      <Button variant="ghost" size="sm" className="h-7 px-2 text-xs"
                              onClick={() => navigate(`/series?q=${encodeURIComponent(t.topicName ?? '')}`)}
                              title="Practise similar questions on this topic">
                        <MessageCircleQuestion className="size-3.5" /> Practise
                      </Button>
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
