import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, RefreshCw, Trophy } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { toast } from 'sonner'
import { adminApi, adminKeys, toastAdminError } from '@/api/admin'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { MathText } from '@/components/common/MathText'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatClock, formatDateTime, formatNumber } from '@/lib/format'
import { hasRole, useAuthStore } from '@/store/auth'
import type { QuestionStat } from '@/types/admin'

const FLAG: Record<NonNullable<QuestionStat['flag']>, { label: string; variant: 'destructive' | 'success' | 'warning' }> = {
  TOO_HARD: { label: 'Too hard', variant: 'destructive' },
  TOO_EASY: { label: 'Too easy', variant: 'success' },
  SKIPPED: { label: 'Often skipped', variant: 'warning' },
}

export default function TestStatsPage() {
  const { testId = '' } = useParams()
  const admin = hasRole(useAuthStore((s) => s.user), 'SUPER_ADMIN')
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const confirm = useConfirm()
  const stats = useQuery({ queryKey: adminKeys.stats(testId), queryFn: () => adminApi.testStats(testId) })
  const results = useQuery({
    queryKey: adminKeys.results(testId, page), queryFn: () => adminApi.testResults(testId, page), placeholderData: keepPreviousData,
  })
  const finalize = useMutation({
    mutationFn: () => adminApi.finalizeRanks(testId),
    onSuccess: (r) => {
      toast.success(`Final ranks computed for ${r.rankedCandidates ?? 0} candidates`)
      void qc.invalidateQueries({ queryKey: ['admin', 'test', testId] })
    },
    onError: toastAdminError,
  })
  const reEvaluate = useMutation({
    mutationFn: ({ attemptId, reason }: { attemptId: string; reason: string }) => adminApi.reEvaluate(attemptId, reason),
    onSuccess: () => { toast.success('Re-evaluation queued'); setTimeout(() => void qc.invalidateQueries({ queryKey: ['admin', 'test', testId] }), 2000) },
    onError: toastAdminError,
  })

  if (stats.isError) return <ErrorState error={stats.error} onRetry={() => stats.refetch()} />
  if (stats.isPending) return <PageLoader />
  const s = stats.data
  const sc = s.scores

  return (
    <>
      <PageHeader title={s.title} description="Test statistics"
                  actions={
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" asChild><Link to={`/admin/tests/${testId}`}><ArrowLeft /> Builder</Link></Button>
                      {admin && <Button loading={finalize.isPending} onClick={() => finalize.mutate()}><Trophy /> Finalize ranks</Button>}
                    </div>
                  } />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Candidates" value={formatNumber(sc.candidates, 0)}
              hint={Object.entries(s.attemptsByStatus).map(([k, v]) => `${v} ${k.toLowerCase().replace('_', ' ')}`).join(' · ')} />
        <Stat label="Average score" value={`${formatNumber(sc.average)} / ${formatNumber(s.maxScore)}`} hint={`Median ${formatNumber(sc.median)} · SD ${formatNumber(sc.stdDev)}`} />
        <Stat label="Highest / lowest" value={`${formatNumber(sc.highest)} / ${formatNumber(sc.lowest)}`} />
        <Stat label="Average accuracy" value={sc.averageAccuracy != null ? `${formatNumber(sc.averageAccuracy, 1)}%` : '–'}
              hint={sc.averageTimeSeconds != null ? `Average time ${formatClock(sc.averageTimeSeconds)}` : undefined} />
      </div>

      <Card className="mt-6">
        <CardHeader><CardTitle>Score distribution</CardTitle></CardHeader>
        <CardContent className="h-64">
          {s.distribution.length === 0 ? <p className="text-muted-foreground text-sm">No evaluated attempts yet.</p> : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={s.distribution.map((b) => ({ range: `${formatNumber(b.from, 0)}–${formatNumber(b.to, 0)}`, count: b.count }))}
                        margin={{ top: 5, right: 8, bottom: 0, left: -16 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="range" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v) => [v, 'Candidates']} />
                <Bar dataKey="count" fill="var(--primary)" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Question analysis</CardTitle>
          <CardDescription>Flags: under 20% accuracy = too hard, over 90% = too easy, attempted by under 30% = often skipped</CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto px-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-muted-foreground border-b text-left">
                <th className="px-4 py-2 font-medium">#</th>
                <th className="px-3 py-2 font-medium">Question</th>
                <th className="px-3 py-2 text-right font-medium">Attempted</th>
                <th className="px-3 py-2 text-right font-medium">Correct</th>
                <th className="px-3 py-2 text-right font-medium">Accuracy</th>
                <th className="px-3 py-2 text-right font-medium">Avg time</th>
                <th className="px-4 py-2 font-medium" />
              </tr>
            </thead>
            <tbody className="tabular-nums">
              {s.questions.map((q) => (
                <tr key={q.questionId} className="border-b last:border-0">
                  <td className="px-4 py-2">{q.number}</td>
                  <td className="max-w-md px-3 py-2">
                    <Link to={`/admin/questions/${q.questionId}`} className="line-clamp-1 hover:underline"><MathText as="span" text={q.question?.textPreview ?? q.questionId} /></Link>
                    <p className="text-muted-foreground text-xs">{q.section}</p>
                  </td>
                  <td className="px-3 py-2 text-right">{q.attempted} ({formatNumber(q.attemptRate, 0)}%)</td>
                  <td className="px-3 py-2 text-right">{q.correct}{q.partial ? ` +${q.partial}` : ''}</td>
                  <td className="px-3 py-2 text-right">{q.attempted ? `${formatNumber(q.accuracy, 0)}%` : '–'}</td>
                  <td className="px-3 py-2 text-right">{q.avgTimeSeconds != null ? formatClock(q.avgTimeSeconds) : '–'}</td>
                  <td className="px-4 py-2">{q.flag && <Badge variant={FLAG[q.flag].variant}>{FLAG[q.flag].label}</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card className="mt-6">
        <CardHeader><CardTitle>Results</CardTitle></CardHeader>
        <CardContent className="overflow-x-auto px-0">
          {results.isError ? <ErrorState error={results.error} onRetry={() => results.refetch()} /> :
            !results.data ? <p className="text-muted-foreground px-6 text-sm">Loading…</p> :
              results.data.content.length === 0 ? <div className="px-6"><EmptyState title="No results yet" /></div> : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-muted-foreground border-b text-left">
                      <th className="px-4 py-2 font-medium">Rank</th>
                      <th className="px-3 py-2 font-medium">Student</th>
                      <th className="px-3 py-2 text-right font-medium">Score</th>
                      <th className="px-3 py-2 text-right font-medium">Percentile</th>
                      <th className="px-3 py-2 text-right font-medium">Accuracy</th>
                      <th className="px-3 py-2 text-right font-medium">Time</th>
                      <th className="px-3 py-2 font-medium">Evaluated</th>
                      {admin && <th className="px-4 py-2" />}
                    </tr>
                  </thead>
                  <tbody className="tabular-nums">
                    {results.data.content.map((r) => (
                      <tr key={r.resultId} className="border-b last:border-0">
                        <td className="px-4 py-2">{r.ranked ? (r.rank ?? '–') : <Badge variant="muted">Practice</Badge>}</td>
                        <td className="px-3 py-2">{r.studentName}</td>
                        <td className="px-3 py-2 text-right">{formatNumber(r.score)} / {formatNumber(r.maxScore)}</td>
                        <td className="px-3 py-2 text-right">{formatNumber(r.percentile, 2)}</td>
                        <td className="px-3 py-2 text-right">{formatNumber(r.accuracy, 1)}%</td>
                        <td className="px-3 py-2 text-right">{formatClock(r.timeTakenSeconds)}</td>
                        <td className="text-muted-foreground px-3 py-2 text-xs">{formatDateTime(r.evaluatedAt)}</td>
                        {admin && (
                          <td className="px-4 py-2 text-right">
                            <Button size="sm" variant="ghost" disabled={reEvaluate.isPending}
                                    onClick={async () => {
                                      const ok = await confirm({ title: 'Re-evaluate the attempt of ' + r.studentName + '?', reason: 'required',
                                        description: 'It is scored again with the current answer key; rank and percentile may change.', confirmText: 'Re-evaluate' })
                                      if (ok) reEvaluate.mutate({ attemptId: r.attemptId, reason: ok.reason })
                                    }}>
                              <RefreshCw /> Re-evaluate
                            </Button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
        </CardContent>
        {results.data && <div className="px-6"><Pagination page={results.data.page} totalPages={results.data.totalPages} onChange={setPage} /></div>}
      </Card>
    </>
  )
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card className="gap-1 py-5">
      <CardContent>
        <p className="text-muted-foreground text-sm">{label}</p>
        <p className="text-2xl font-semibold">{value}</p>
        {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
      </CardContent>
    </Card>
  )
}
