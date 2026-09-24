import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Medal } from 'lucide-react'
import { attemptsApi, useTestInfo } from '@/api/attempts'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { formatClock, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { LeaderboardEntry } from '@/types/exam'

const MEDAL = ['text-amber-500', 'text-slate-400', 'text-orange-700']

export default function LeaderboardPage() {
  const { testId = '' } = useParams()
  const info = useTestInfo(testId)
  const board = useQuery({
    queryKey: ['leaderboard', testId],
    queryFn: () => attemptsApi.leaderboard(testId),
    refetchInterval: 30_000,
  })

  if (board.isError) return <ErrorState error={board.error} onRetry={() => board.refetch()} />
  if (board.isPending) return <PageLoader />
  const b = board.data
  const meListed = b.entries.some((e) => e.you)

  return (
    <>
      <PageHeader title="Leaderboard" description={info.data?.title}
                  actions={<Button variant="outline" asChild><Link to={`/tests/${testId}`}><ArrowLeft /> Back to test</Link></Button>} />
      {b.me && (
        <Card className="mb-6 py-5">
          <CardContent className="flex flex-wrap items-center gap-x-8 gap-y-2">
            <div><p className="text-muted-foreground text-sm">Your rank</p><p className="text-2xl font-semibold">#{b.me.rank}</p></div>
            <div><p className="text-muted-foreground text-sm">Score</p><p className="text-2xl font-semibold">{formatNumber(b.me.score)}</p></div>
            {b.myPercentile != null && (
              <div><p className="text-muted-foreground text-sm">Percentile</p><p className="text-2xl font-semibold">{formatNumber(b.myPercentile, 2)}</p></div>
            )}
            <p className="text-muted-foreground text-sm">among {formatNumber(b.totalCandidates, 0)} candidates</p>
          </CardContent>
        </Card>
      )}
      {b.entries.length === 0 ? (
        <EmptyState title="No ranked results yet" description="Ranks appear after candidates submit and their results are evaluated." />
      ) : (
        <Card className="py-0">
          <CardContent className="overflow-x-auto px-0">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground border-b text-left">
                  <th className="w-20 px-4 py-3 font-medium">Rank</th>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 text-right font-medium">Score</th>
                  <th className="px-4 py-3 text-right font-medium">Time</th>
                </tr>
              </thead>
              <tbody className="tabular-nums">
                {b.entries.map((e) => <Row key={`${e.rank}-${e.name}-${e.timeTakenSeconds}`} e={e} />)}
                {b.me && !meListed && (
                  <>
                    <tr><td colSpan={4} className="text-muted-foreground px-4 py-1 text-center">⋮</td></tr>
                    <Row e={{ ...b.me, you: true }} />
                  </>
                )}
              </tbody>
            </table>
          </CardContent>
        </Card>
      )}
    </>
  )
}

function Row({ e }: { e: LeaderboardEntry }) {
  return (
    <tr className={cn('border-b last:border-0', e.you && 'bg-accent font-medium')}>
      <td className="px-4 py-2.5">
        <span className="flex items-center gap-1">
          {e.rank <= 3 && <Medal className={cn('size-4', MEDAL[e.rank - 1])} aria-hidden />}#{e.rank}
        </span>
      </td>
      <td className="px-4 py-2.5">{e.name}{e.you && <span className="text-primary ml-2 text-xs">(you)</span>}</td>
      <td className="px-4 py-2.5 text-right">{formatNumber(e.score)}</td>
      <td className="px-4 py-2.5 text-right">{formatClock(e.timeTakenSeconds)}</td>
    </tr>
  )
}
