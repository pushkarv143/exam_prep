import { Award, Crosshair, Percent, Trophy } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { formatClock, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useCountUp } from '@/lib/useCountUp'
import type { Result } from '@/types/exam'

/** A calm animated radial gauge for the score percentage. */
function ScoreRing({ percentage, score, maxScore }: { percentage: number; score: number; maxScore: number }) {
  const animatedPct = useCountUp(percentage, 1100)
  const animatedScore = useCountUp(score, 1100)
  const radius = 74
  const circumference = 2 * Math.PI * radius
  const clamped = Math.max(0, Math.min(100, animatedPct))
  const offset = circumference - (clamped / 100) * circumference
  const tone = percentage >= 60 ? 'var(--success)' : percentage >= 35 ? 'var(--warning)' : 'var(--destructive)'

  return (
    <div className="relative grid size-44 shrink-0 place-items-center" role="img"
         aria-label={`Score ${formatNumber(score)} out of ${formatNumber(maxScore)}, ${formatNumber(percentage, 1)} percent`}>
      <svg viewBox="0 0 176 176" className="size-44 -rotate-90">
        <circle cx="88" cy="88" r={radius} fill="none" stroke="var(--muted)" strokeWidth="12" />
        <circle cx="88" cy="88" r={radius} fill="none" stroke={tone} strokeWidth="12" strokeLinecap="round"
                strokeDasharray={circumference} strokeDashoffset={offset} />
      </svg>
      <div className="absolute text-center">
        <div className="text-3xl font-bold tabular-nums">{formatNumber(animatedScore, 0)}</div>
        <div className="text-muted-foreground text-xs">of {formatNumber(maxScore, 0)} marks</div>
        <div className="mt-1 text-sm font-semibold tabular-nums" style={{ color: tone }}>
          {formatNumber(animatedPct, 1)}%
        </div>
      </div>
    </div>
  )
}

function Metric({ icon: Icon, label, value, hint }: { icon: typeof Award; label: string; value: string; hint?: string }) {
  return (
    <div className="flex items-start gap-3">
      <div className="bg-primary/10 text-primary rounded-lg p-2"><Icon className="size-5" /></div>
      <div className="min-w-0">
        <p className="text-muted-foreground text-sm">{label}</p>
        <p className="truncate text-xl font-semibold tabular-nums">{value}</p>
        {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
      </div>
    </div>
  )
}

/** Animated hero reveal: everything a student wants in the first second, no click-through. */
export function ScoreReveal({ r }: { r: Result }) {
  const pct = r.percentage != null ? Number(r.percentage) : 0
  const animatedPercentile = useCountUp(r.percentile != null ? Number(r.percentile) : 0, 1100, 150)
  const animatedRank = useCountUp(r.rank ?? 0, 1100, 150)

  return (
    <Card className={cn('overflow-hidden', 'animate-in fade-in slide-in-from-bottom-2 duration-500')}>
      <CardContent className="flex flex-col items-center gap-6 p-6 sm:flex-row sm:gap-8">
        <ScoreRing percentage={pct} score={Number(r.score ?? 0)} maxScore={Number(r.maxScore)} />
        <div className="grid w-full flex-1 gap-5 sm:grid-cols-2">
          {r.ranked ? (
            <>
              <Metric icon={Trophy} label={r.rankFinal ? 'Rank' : 'Rank (provisional)'}
                      value={r.rank != null ? `#${formatNumber(animatedRank, 0)}` : '–'}
                      hint={r.totalCandidates ? `of ${formatNumber(r.totalCandidates, 0)} students` : undefined} />
              <Metric icon={Percent} label="Percentile"
                      value={r.percentile != null ? formatNumber(animatedPercentile, 2) : '–'}
                      hint={r.percentile != null ? `Better than ${formatNumber(animatedPercentile, 1)}% of students` : undefined} />
            </>
          ) : (
            <div className="text-muted-foreground bg-muted/40 col-span-2 rounded-lg border border-dashed p-3 text-sm">
              This was a practice attempt, so it is not ranked. Your score and analysis are still saved below.
            </div>
          )}
          <Metric icon={Crosshair} label="Accuracy" value={`${formatNumber(r.accuracy, 1)}%`}
                  hint={`${r.correct ?? 0} correct · ${r.incorrect ?? 0} wrong`} />
          <Metric icon={Award} label="Time taken" value={formatClock(r.timeTakenSeconds ?? 0)}
                  hint={`${r.unattempted ?? 0} not attempted`} />
        </div>
      </CardContent>
    </Card>
  )
}
