import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { AlertTriangle, Gauge, Lightbulb, Rabbit, Turtle } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatClock } from '@/lib/format'
import { OUTCOME_COLORS } from '../resultColors'
import type { ReviewItem } from '@/types/exam'

interface Insight {
  tone: 'good' | 'warn' | 'info'
  icon: typeof Lightbulb
  text: string
}

/**
 * Time analysis + plain-language insights, derived only from real per-question data
 * (outcome, marks, timeSpentSeconds). "Suggested" pace is the exam's time distributed by
 * marks — clearly labelled as a guide, not a backend-provided expected time.
 */
export function TimeInsights({ items, durationSeconds, totalMarks, attemptId }:
  { items: ReviewItem[]; durationSeconds: number; totalMarks: number; attemptId: string }) {
  const navigate = useNavigate()

  const { rows, insights, avgWrongTime, rushedWrong, overtimeGain } = useMemo(() => {
    const suggestedFor = (marks: number) =>
      totalMarks > 0 && durationSeconds > 0 ? (durationSeconds * marks) / totalMarks : 0

    const rows = items.map((q) => {
      const suggested = suggestedFor(Number(q.marks))
      return {
        number: q.number,
        outcome: q.outcome,
        time: q.timeSpentSeconds,
        suggested: Math.round(suggested),
        marks: Number(q.marks),
      }
    })

    const attempted = items.filter((q) => q.outcome !== 'UNATTEMPTED')
    const wrong = items.filter((q) => q.outcome === 'INCORRECT')
    const avgWrongTime = wrong.length
      ? Math.round(wrong.reduce((s, q) => s + q.timeSpentSeconds, 0) / wrong.length)
      : 0

    // Rushed wrong: got it wrong AND spent well under the suggested pace.
    const rushedWrong = items.filter(
      (q) => q.outcome === 'INCORRECT' && q.timeSpentSeconds > 0
        && q.timeSpentSeconds < 0.4 * suggestedFor(Number(q.marks)),
    )
    // Overtime: correct but far over the suggested pace — time that could be reinvested.
    const overtime = items.filter(
      (q) => q.outcome === 'CORRECT' && q.timeSpentSeconds > 1.8 * suggestedFor(Number(q.marks))
        && suggestedFor(Number(q.marks)) > 0,
    )
    const overtimeGain = overtime.reduce(
      (s, q) => s + (q.timeSpentSeconds - Math.round(suggestedFor(Number(q.marks)))), 0,
    )
    const unattempted = items.filter((q) => q.outcome === 'UNATTEMPTED')

    const insights: Insight[] = []
    if (rushedWrong.length >= 2) {
      insights.push({
        tone: 'warn', icon: Rabbit,
        text: `You answered ${rushedWrong.length} questions in under half the suggested time and got them wrong. Slowing down on these could turn some into marks.`,
      })
    }
    if (overtime.length >= 2) {
      insights.push({
        tone: 'info', icon: Turtle,
        text: `You spent about ${formatClock(overtimeGain)} extra on ${overtime.length} questions you still got right. Banking that time lets you reach more questions.`,
      })
    }
    if (unattempted.length > 0 && durationSeconds > 0) {
      insights.push({
        tone: 'info', icon: Gauge,
        text: `${unattempted.length} question${unattempted.length > 1 ? 's were' : ' was'} left unattempted. A quick first pass on easy ones first can lift your score with no extra risk.`,
      })
    }
    if (wrong.length > 0) {
      insights.push({
        tone: 'warn', icon: AlertTriangle,
        text: `Around ${formatClock(wrong.reduce((s, q) => s + q.timeSpentSeconds, 0))} went into questions you got wrong. Reviewing these topics gives the fastest improvement.`,
      })
    }
    if (attempted.length > 0 && rushedWrong.length === 0 && overtime.length <= 1) {
      insights.push({
        tone: 'good', icon: Lightbulb,
        text: 'Your pacing looks steady — time per question stayed close to the suggested pace. Keep it up.',
      })
    }
    return { rows, insights, avgWrongTime, rushedWrong, overtimeGain }
  }, [items, durationSeconds, totalMarks])

  if (items.length === 0) return null
  const avgSuggested = rows.length ? Math.round(rows.reduce((s, r) => s + r.suggested, 0) / rows.length) : 0

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>Time analysis</CardTitle>
        <CardDescription>
          Time spent per question, coloured by outcome. The dashed line is the suggested pace
          (exam time shared across questions by marks). Tap a bar to review those questions.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ top: 5, right: 12, bottom: 0, left: -12 }}
                      onClick={(s) => {
                        const o = (s as { activePayload?: { payload?: { outcome?: string } }[] })
                          ?.activePayload?.[0]?.payload?.outcome
                        if (o) navigate(`/attempts/${attemptId}/solutions?filter=${o}`)
                      }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="number" tick={{ fontSize: 11 }} label={{ value: 'Question', position: 'insideBottom', offset: -2, fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `${Math.round(Number(v) / 60)}m`} />
              <Tooltip
                formatter={(v) => [formatClock(Number(v)), 'Time']}
                labelFormatter={(l) => `Question ${l}`}
                cursor={{ fill: 'var(--muted)', opacity: 0.4 }} />
              {avgSuggested > 0 && (
                <ReferenceLine y={avgSuggested} stroke="var(--muted-foreground)" strokeDasharray="4 4"
                               label={{ value: 'suggested', position: 'right', fontSize: 10, fill: 'var(--muted-foreground)' }} />
              )}
              <Bar dataKey="time" radius={[3, 3, 0, 0]} className="cursor-pointer">
                {rows.map((r) => (
                  <Cell key={r.number} fill={OUTCOME_COLORS[r.outcome.toLowerCase() as keyof typeof OUTCOME_COLORS] ?? OUTCOME_COLORS.unattempted} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <MiniStat label="Rushed wrong answers" value={String(rushedWrong.length)}
                    hint="wrong & answered very fast" tone={rushedWrong.length ? 'warn' : 'good'} />
          <MiniStat label="Avg time on wrong answers" value={formatClock(avgWrongTime)}
                    hint="time spent per wrong question" tone="info" />
          <MiniStat label="Reclaimable time" value={formatClock(overtimeGain)}
                    hint="overtime on questions you got right" tone="info" />
        </div>

        {insights.length > 0 && (
          <ul className="space-y-2">
            {insights.map((ins, i) => {
              const Icon = ins.icon
              return (
                <li key={i} className="flex items-start gap-3 rounded-lg border p-3 text-sm">
                  <span className={
                    ins.tone === 'good' ? 'text-success' : ins.tone === 'warn' ? 'text-warning' : 'text-primary'
                  }><Icon className="mt-0.5 size-4 shrink-0" /></span>
                  <span className="text-foreground/90">{ins.text}</span>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function MiniStat({ label, value, hint, tone }:
  { label: string; value: string; hint: string; tone: 'good' | 'warn' | 'info' }) {
  const color = tone === 'good' ? 'text-success' : tone === 'warn' ? 'text-warning' : 'text-foreground'
  return (
    <div className="rounded-lg border p-3">
      <p className="text-muted-foreground text-xs">{label}</p>
      <p className={`text-lg font-semibold tabular-nums ${color}`}>{value}</p>
      <p className="text-muted-foreground text-xs">{hint}</p>
    </div>
  )
}
