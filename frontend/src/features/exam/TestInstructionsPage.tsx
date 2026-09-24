import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CalendarClock, Clock, ListChecks, Lock, PlayCircle, Target, Trophy } from 'lucide-react'
import { attemptsApi, useMyAttempts, useTestInfo } from '@/api/attempts'
import { MathText } from '@/components/common/MathText'
import { ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatDuration, formatNumber, plural } from '@/lib/format'
import type { AttemptSummary, TestInfo } from '@/types/exam'

const GENERAL_INSTRUCTIONS = [
  'The clock is set on the server. The countdown at the top right shows the time left. When it reaches zero, the test is submitted automatically.',
  'Answers are saved automatically every few seconds and whenever you move between questions. If your connection drops, keep working: answers are kept on this device and sent when you are back online.',
  'If the browser closes, open the test again from this page to resume. The timer keeps running while you are away.',
  'The question palette shows each question\'s status: not visited, not answered, answered, marked for review, or answered and marked. Answered-and-marked questions are evaluated.',
  'Switching tabs, leaving full screen, copy and paste are recorded.',
]

export default function TestInstructionsPage() {
  const { testId = '' } = useParams()
  const info = useTestInfo(testId)
  const attempts = useMyAttempts(testId)

  if (info.isError) return <ErrorState error={info.error} onRetry={() => info.refetch()} />
  if (info.isPending) return <PageLoader />
  const t = info.data

  return (
    <>
      <PageHeader title={t.title} description={t.description}
                  actions={t.seriesSlug && <Button variant="outline" asChild><Link to={`/series/${t.seriesSlug}`}>Back to series</Link></Button>} />
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <Card>
            <CardContent className="grid gap-4 sm:grid-cols-3">
              <Fact icon={Clock} label="Duration" value={formatDuration(t.durationMinutes)} />
              <Fact icon={ListChecks} label="Questions" value={String(t.totalQuestions)} />
              <Fact icon={Target} label="Maximum marks" value={formatNumber(t.totalMarks)} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Sections</CardTitle></CardHeader>
            <CardContent className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-b text-left">
                    <th className="py-2 font-medium">Section</th>
                    <th className="py-2 text-right font-medium">Questions</th>
                    <th className="py-2 text-right font-medium">Marks</th>
                  </tr>
                </thead>
                <tbody>
                  {t.sections.map((s) => (
                    <tr key={s.name} className="border-b last:border-0">
                      <td className="py-2">
                        {s.name}
                        {s.maxQuestionsToAttempt && (
                          <span className="text-muted-foreground ml-2 text-xs">(attempt any {s.maxQuestionsToAttempt})</span>
                        )}
                      </td>
                      <td className="py-2 text-right tabular-nums">{s.questionCount}</td>
                      <td className="py-2 text-right tabular-nums">{formatNumber(s.marks)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Instructions</CardTitle></CardHeader>
            <CardContent className="space-y-4 text-sm">
              {t.instructions && <MathText text={t.instructions} className="whitespace-pre-line" />}
              <ol className="list-decimal space-y-2 pl-5">
                {GENERAL_INSTRUCTIONS.map((i) => <li key={i}>{i}</li>)}
              </ol>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <StartCard test={t} attempts={attempts.data ?? []} attemptsLoading={attempts.isPending} />
          {(attempts.data?.length ?? 0) > 0 && <AttemptHistory attempts={attempts.data!} />}
          {t.availability !== 'UPCOMING' && (
            <Button variant="outline" className="w-full" asChild>
              <Link to={`/tests/${t.id}/leaderboard`}><Trophy /> Leaderboard</Link>
            </Button>
          )}
        </div>
      </div>
    </>
  )
}

function Fact({ icon: Icon, label, value }: { icon: typeof Clock; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3">
      <div className="bg-primary/10 text-primary rounded-lg p-2"><Icon className="size-5" /></div>
      <div>
        <p className="text-muted-foreground text-xs">{label}</p>
        <p className="font-semibold">{value}</p>
      </div>
    </div>
  )
}

function StartCard({ test, attempts, attemptsLoading }: { test: TestInfo; attempts: AttemptSummary[]; attemptsLoading: boolean }) {
  const navigate = useNavigate()
  const [agreed, setAgreed] = useState(false)
  const start = useMutation({
    mutationFn: () => attemptsApi.start(test.id),
    onSuccess: (s) => navigate(`/exam/${s.attemptId}`),
  })
  const inProgress = attempts.find((a) => a.status === 'IN_PROGRESS')
  const used = attempts.filter((a) => a.status !== 'CANCELLED').length
  const left = Math.max(0, test.maxAttempts - used)

  let blocker: string | null = null
  if (!test.hasAccess) blocker = 'You need to enroll in this test series to attempt this test.'
  else if (test.availability === 'UPCOMING') blocker = `This test opens on ${formatDateTime(test.startAt)}.`
  else if (test.availability === 'CLOSED') blocker = 'This test window has closed.'
  else if (test.availability === 'NOT_PUBLISHED') blocker = 'This test is not published yet.'
  else if (!inProgress && left === 0) blocker = 'You have used all your attempts for this test.'

  return (
    <Card>
      <CardHeader>
        <CardTitle>{inProgress ? 'Resume your attempt' : 'Ready to begin?'}</CardTitle>
        <CardDescription>
          {inProgress ? 'Your timer is still running.' : `${plural(left, 'attempt')} left of ${test.maxAttempts}`}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {(test.startAt || test.endAt) && (
          <p className="text-muted-foreground flex items-center gap-2 text-sm">
            <CalendarClock className="size-4" />
            {test.startAt ? formatDateTime(test.startAt) : 'Now'} – {test.endAt ? formatDateTime(test.endAt) : 'open'}
          </p>
        )}
        {blocker ? (
          <Alert>
            <Lock className="size-4" />
            <AlertTitle>Not available</AlertTitle>
            <AlertDescription>
              {blocker}
              {!test.hasAccess && test.seriesSlug && (
                <Link to={`/series/${test.seriesSlug}`} className="text-primary mt-1 block font-medium hover:underline">View the series</Link>
              )}
            </AlertDescription>
          </Alert>
        ) : (
          <>
            {!inProgress && (
              <label className="flex items-start gap-2 text-sm">
                <Checkbox checked={agreed} onCheckedChange={(v) => setAgreed(v === true)} className="mt-0.5" />
                I have read the instructions. I will not use unfair means during the test.
              </label>
            )}
            {start.isError && <p className="text-destructive text-sm" role="alert">{errorMessage(start.error)}</p>}
            <Button size="lg" className="w-full" disabled={attemptsLoading || (!inProgress && !agreed)}
                    loading={start.isPending} onClick={() => start.mutate()}>
              <PlayCircle /> {inProgress ? 'Resume test' : 'Start test'}
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  )
}

const STATUS_BADGE: Record<string, { label: string; variant: 'success' | 'warning' | 'muted' | 'default' }> = {
  IN_PROGRESS: { label: 'In progress', variant: 'warning' },
  SUBMITTED: { label: 'Evaluating', variant: 'default' },
  EVALUATED: { label: 'Evaluated', variant: 'success' },
  CANCELLED: { label: 'Cancelled', variant: 'muted' },
}

function AttemptHistory({ attempts }: { attempts: AttemptSummary[] }) {
  return (
    <Card>
      <CardHeader><CardTitle className="text-base">Your attempts</CardTitle></CardHeader>
      <CardContent>
        <ul className="divide-y text-sm">
          {attempts.map((a) => {
            const b = STATUS_BADGE[a.status]
            return (
              <li key={a.attemptId} className="flex items-center justify-between gap-2 py-2">
                <div>
                  <p className="font-medium">Attempt {a.attemptNo} <Badge variant={b.variant} className="ml-1">{b.label}</Badge></p>
                  <p className="text-muted-foreground text-xs">{formatDateTime(a.submittedAt ?? a.startedAt)}</p>
                </div>
                {a.status === 'IN_PROGRESS' ? (
                  <Button size="sm" variant="outline" asChild><Link to={`/exam/${a.attemptId}`}>Resume</Link></Button>
                ) : a.status !== 'CANCELLED' && (
                  <Button size="sm" variant="outline" asChild><Link to={`/attempts/${a.attemptId}/result`}>Result</Link></Button>
                )}
              </li>
            )
          })}
        </ul>
      </CardContent>
    </Card>
  )
}
