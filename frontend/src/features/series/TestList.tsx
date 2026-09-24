import { Link } from 'react-router-dom'
import { CalendarClock, Clock, Lock, Target } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatDateTime, formatDuration, formatNumber } from '@/lib/format'
import type { PublicTest, TestAvailability } from '@/types/domain'

const AVAILABILITY: Record<TestAvailability, { label: string; variant: 'success' | 'warning' | 'muted' }> = {
  OPEN: { label: 'Open now', variant: 'success' },
  UPCOMING: { label: 'Upcoming', variant: 'warning' },
  CLOSED: { label: 'Closed', variant: 'muted' },
  NOT_PUBLISHED: { label: 'Not published', variant: 'muted' },
}

const PATTERN: Record<string, string> = { JEE_MAIN: 'JEE Main', JEE_ADVANCED: 'JEE Advanced', NEET: 'NEET', CUSTOM: 'Practice' }

/** Tests in a series. The action depends on the visitor's access (undefined = anonymous). */
export function TestList({ tests, signedIn }: { tests: PublicTest[]; signedIn: boolean }) {
  return (
    <ul className="divide-y rounded-xl border bg-card">
      {tests.map((t) => {
        const a = AVAILABILITY[t.availability]
        return (
          <li key={t.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-medium">{t.title}</h3>
                <Badge variant={a.variant}>{a.label}</Badge>
                {t.free && <Badge variant="success">Free sample</Badge>}
                <Badge variant="outline">{PATTERN[t.pattern] ?? t.pattern}</Badge>
              </div>
              <div className="text-muted-foreground mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-sm">
                <span className="flex items-center gap-1"><Clock className="size-4" /> {formatDuration(t.durationMinutes)}</span>
                <span className="flex items-center gap-1"><Target className="size-4" /> {t.totalQuestions} Q · {formatNumber(t.totalMarks)} marks</span>
                {(t.startAt || t.endAt) && (
                  <span className="flex items-center gap-1">
                    <CalendarClock className="size-4" />
                    {t.startAt ? formatDateTime(t.startAt) : 'Now'} – {t.endAt ? formatDateTime(t.endAt) : 'open'}
                  </span>
                )}
              </div>
            </div>
            <div className="shrink-0">
              {!signedIn ? (
                t.free ? <Button variant="outline" size="sm" asChild><Link to="/login">Log in to attempt</Link></Button>
                  : <span className="text-muted-foreground flex items-center gap-1 text-sm"><Lock className="size-4" /> Enroll to unlock</span>
              ) : t.accessible ? (
                <Button size="sm" asChild variant={t.availability === 'OPEN' ? 'default' : 'outline'}>
                  <Link to={`/tests/${t.id}`}>{t.availability === 'OPEN' ? 'Attempt' : 'View'}</Link>
                </Button>
              ) : (
                <span className="text-muted-foreground flex items-center gap-1 text-sm"><Lock className="size-4" /> Locked</span>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
