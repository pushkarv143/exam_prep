import { Link } from 'react-router-dom'
import { CalendarClock, Clock, Lock, PlayCircle, Target } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Countdown } from '@/components/common/Countdown'
import { formatDateTime, formatDuration, formatNumber } from '@/lib/format'
import { readActiveAttempt } from '@/features/exam/activeAttempt'
import type { PublicTest, TestAvailability } from '@/types/domain'

const AVAILABILITY: Record<TestAvailability, { label: string; variant: 'success' | 'warning' | 'muted' }> = {
  OPEN: { label: 'Open now', variant: 'success' },
  UPCOMING: { label: 'Upcoming', variant: 'warning' },
  CLOSED: { label: 'Closed', variant: 'muted' },
  NOT_PUBLISHED: { label: 'Not published', variant: 'muted' },
}

const PATTERN: Record<string, string> = { JEE_MAIN: 'JEE Main', JEE_ADVANCED: 'JEE Advanced', NEET: 'NEET', CUSTOM: 'Practice' }

/** A muted, human lock/state reason — never a bare disabled button. */
function LockReason({ text }: { text: string }) {
  return <span className="text-muted-foreground flex items-center gap-1.5 text-sm"><Lock className="size-4" /> {text}</span>
}

/** Tests in a series. The action depends on the visitor's access (undefined = anonymous). */
export function TestList({ tests, signedIn }: { tests: PublicTest[]; signedIn: boolean }) {
  const active = signedIn ? readActiveAttempt() : null

  return (
    <ul className="divide-y rounded-xl border bg-card">
      {tests.map((t) => {
        const a = AVAILABILITY[t.availability]
        const resumable = active?.testId === t.id
        return (
          <li key={t.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-medium">{t.title}</h3>
                <Badge variant={a.variant}>{a.label}</Badge>
                {resumable && <Badge variant="warning">In progress</Badge>}
                {t.free && <Badge variant="success">Free sample</Badge>}
                <Badge variant="outline">{PATTERN[t.pattern] ?? t.pattern}</Badge>
              </div>
              <div className="text-muted-foreground mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-sm">
                <span className="flex items-center gap-1"><Clock className="size-4" /> {formatDuration(t.durationMinutes)}</span>
                <span className="flex items-center gap-1"><Target className="size-4" /> {t.totalQuestions} Q · {formatNumber(t.totalMarks)} marks</span>
                {t.availability === 'UPCOMING' && t.startAt ? (
                  <span className="text-warning flex items-center gap-1 font-medium">
                    <CalendarClock className="size-4" /> Opens in <Countdown target={Date.parse(t.startAt)} className="tabular-nums" />
                  </span>
                ) : (t.startAt || t.endAt) && (
                  <span className="flex items-center gap-1">
                    <CalendarClock className="size-4" />
                    {t.startAt ? formatDateTime(t.startAt) : 'Now'} – {t.endAt ? formatDateTime(t.endAt) : 'open'}
                  </span>
                )}
              </div>
            </div>
            <div className="shrink-0">
              <TestAction t={t} signedIn={signedIn} resumable={resumable} />
            </div>
          </li>
        )
      })}
    </ul>
  )
}

function TestAction({ t, signedIn, resumable }: { t: PublicTest; signedIn: boolean; resumable: boolean }) {
  if (!signedIn) {
    return t.free
      ? <Button variant="outline" size="sm" asChild><Link to="/login">Log in to attempt</Link></Button>
      : <LockReason text="Enroll in this series to unlock" />
  }

  if (resumable) {
    return (
      <Button size="sm" asChild variant="success">
        <Link to={`/exam/${readActiveAttempt()?.attemptId}`}><PlayCircle className="size-4" /> Resume</Link>
      </Button>
    )
  }

  if (!t.accessible) {
    return <LockReason text={t.free ? 'Log in to attempt' : 'Enroll in this series to unlock'} />
  }

  switch (t.availability) {
    case 'OPEN':
      return <Button size="sm" asChild><Link to={`/tests/${t.id}`}>Start</Link></Button>
    case 'UPCOMING':
      return (
        <div className="text-right">
          <Button size="sm" variant="outline" asChild><Link to={`/tests/${t.id}`}>View details</Link></Button>
          <p className="text-muted-foreground mt-1 text-xs">Not open yet</p>
        </div>
      )
    case 'CLOSED':
      return <LockReason text={t.endAt ? `Closed on ${formatDateTime(t.endAt)}` : 'Test window closed'} />
    default:
      return <LockReason text="Not published yet" />
  }
}
