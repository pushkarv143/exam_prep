import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, ChevronLeft, ChevronRight, CloudOff, Grid3x3, Loader2, Maximize, Minimize } from 'lucide-react'
import { toast } from 'sonner'
import { attemptsApi } from '@/api/attempts'
import { ErrorState, PageLoader } from '@/components/common/States'
import { DarkModeToggle, ThemeMenu } from '@/components/common/ThemeMenu'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { errorMessage } from '@/lib/errors'
import { formatClock } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/auth'
import type { AnswerState } from '@/types/exam'
import { Palette, STATE_STYLE, StateBadge } from './components/Palette'
import { QuestionPanel } from './components/QuestionPanel'
import {
  clearBackup, initExam, paletteState, remainingMs, setAnswer, setMarked, tickCurrent, useExamStore, visit,
} from './examStore'
import { useAntiCheat } from './useAntiCheat'
import { useAutosave } from './useAutosave'

export default function ExamPage() {
  const { attemptId = '' } = useParams()
  const navigate = useNavigate()
  const session = useQuery({
    queryKey: ['attempt-session', attemptId],
    queryFn: () => attemptsApi.session(attemptId),
    staleTime: Infinity, gcTime: 0, refetchOnWindowFocus: false, retry: 3,
  })
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!session.data) return
    if (session.data.status !== 'IN_PROGRESS' || !session.data.paper) {
      navigate(`/attempts/${attemptId}/result`, { replace: true })
      return
    }
    initExam(session.data)
    setReady(true)
  }, [session.data, attemptId, navigate])

  if (session.isError) return <ErrorState error={session.error} onRetry={() => session.refetch()} />
  if (!ready) return <PageLoader label="Loading your test…" />
  return <ExamScreen attemptId={attemptId} />
}

function ExamScreen({ attemptId }: { attemptId: string }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const paper = useExamStore((s) => s.paper)!
  const flat = useExamStore((s) => s.flat)
  const index = useExamStore((s) => s.index)
  const saveState = useExamStore((s) => s.saveState)
  const answers = useExamStore((s) => s.answers)
  const question = flat[index]

  const [started, setStarted] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [fullscreen, setFullscreen] = useState(!!document.fullscreenElement)
  const finishing = useRef(false)

  const goToResult = useCallback(() => {
    if (finishing.current) return
    finishing.current = true
    clearBackup(attemptId)
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined)
    void qc.invalidateQueries({ queryKey: ['my-attempts'] })
    navigate(`/attempts/${attemptId}/result`, { replace: true })
  }, [attemptId, navigate, qc])

  const { flush, markFinished } = useAutosave(attemptId, goToResult)
  const { warning, dismissWarning } = useAntiCheat(attemptId, started, goToResult)

  const submit = useCallback(async (auto: boolean) => {
    if (finishing.current) return
    setSubmitting(true)
    try {
      await flush(true)
      markFinished()
      await attemptsApi.submit(attemptId)
      if (auto) toast.info('Time is up. Your test has been submitted.')
      goToResult()
    } catch (e) {
      setSubmitting(false)
      toast.error(`Could not submit: ${errorMessage(e)}. Your answers are saved; please try again.`)
    }
  }, [attemptId, flush, goToResult, markFinished])

  // Time spent per question: counts only while this tab is visible.
  useEffect(() => {
    const t = setInterval(() => {
      if (document.visibilityState === 'visible') tickCurrent()
    }, 1000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    const onFs = () => setFullscreen(!!document.fullscreenElement)
    document.addEventListener('fullscreenchange', onFs)
    return () => document.removeEventListener('fullscreenchange', onFs)
  }, [])

  // Warn before closing the tab mid-test.
  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (!finishing.current) e.preventDefault()
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [])

  const enterFullscreen = () => document.documentElement.requestFullscreen?.().catch(() => undefined)
  const go = (i: number) => {
    if (i < 0 || i >= flat.length) return
    visit(i)
    void flush()
  }
  const qid = question.questionId
  const marked = answers[qid]?.marked ?? false

  if (!started) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="max-w-md space-y-4 text-center">
          <h1 className="text-2xl font-semibold">{paper.title}</h1>
          <p className="text-muted-foreground">
            The test runs in full-screen mode. Leaving full screen or switching tabs is recorded. Your answers are
            saved automatically. If you get disconnected, simply reopen this page.
          </p>
          <Button size="lg" onClick={() => { void enterFullscreen(); setStarted(true) }}>
            <Maximize /> Enter full screen &amp; begin
          </Button>
          <button type="button" className="text-muted-foreground block w-full text-sm underline" onClick={() => setStarted(true)}>
            Continue without full screen
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-background flex h-screen flex-col select-none">
      {/* Header */}
      <header className="bg-primary text-primary-foreground flex items-center gap-3 px-4 py-2">
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{paper.title}</p>
          <p className="truncate text-xs opacity-80">Candidate: {user?.fullName}</p>
        </div>
        <SaveIndicator state={saveState} />
        <Timer onExpire={() => void submit(true)} />
        <div className="hidden gap-1 sm:flex">
          <DarkModeToggle variant="secondary" />
          <ThemeMenu variant="secondary" />
        </div>
        <Button variant="secondary" size="icon" onClick={() => (fullscreen ? void document.exitFullscreen() : void enterFullscreen())}
                aria-label={fullscreen ? 'Exit full screen' : 'Enter full screen'}>
          {fullscreen ? <Minimize /> : <Maximize />}
        </Button>
      </header>

      {warning && (
        <div className="flex items-center gap-2 bg-amber-100 px-4 py-2 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100" role="alert">
          <AlertTriangle className="size-4 shrink-0" /> <span className="flex-1">{warning}</span>
          <button type="button" className="underline" onClick={dismissWarning}>Dismiss</button>
        </div>
      )}
      {!fullscreen && (
        <div className="bg-muted flex items-center justify-between gap-2 px-4 py-1.5 text-xs">
          <span>You are not in full-screen mode.</span>
          <button type="button" className="text-primary font-medium underline" onClick={() => void enterFullscreen()}>Enter full screen</button>
        </div>
      )}

      {/* Section tabs */}
      <nav className="flex gap-1 overflow-x-auto border-b px-2" aria-label="Sections">
        {paper.sections.map((s, si) => {
          const first = flat.find((q) => q.sectionIndex === si)
          const active = question.sectionIndex === si
          return (
            <button key={s.id} type="button" onClick={() => first && go(first.flatIndex)}
                    className={cn('shrink-0 border-b-2 px-4 py-2.5 text-sm font-medium whitespace-nowrap',
                      active ? 'border-primary text-primary' : 'text-muted-foreground border-transparent hover:text-foreground')}>
              {s.name}
            </button>
          )
        })}
      </nav>

      <div className="flex min-h-0 flex-1">
        <main className="min-w-0 flex-1 overflow-y-auto p-4 sm:p-6">
          <QuestionPanel question={question} />
        </main>
        <aside className={cn('bg-card w-80 shrink-0 overflow-y-auto border-l p-4',
          'fixed inset-y-0 right-0 z-40 shadow-xl transition-transform lg:static lg:z-auto lg:translate-x-0 lg:shadow-none',
          paletteOpen ? 'translate-x-0' : 'translate-x-full')}>
          <div className="mb-3 flex items-center justify-between lg:hidden">
            <span className="font-medium">Questions</span>
            <Button variant="ghost" size="sm" onClick={() => setPaletteOpen(false)}>Close</Button>
          </div>
          <Palette onNavigate={() => { setPaletteOpen(false); void flush() }} />
          <Button variant="destructive" className="mt-6 w-full" onClick={() => setConfirmOpen(true)}>Submit test</Button>
        </aside>
      </div>

      {/* Action bar */}
      <footer className="flex flex-wrap items-center gap-2 border-t px-3 py-2 sm:px-4">
        <Button variant="outline" className="border-violet-600 text-violet-700"
                onClick={() => { setMarked(qid, !marked); if (!marked) go(index + 1) }}>
          {marked ? 'Unmark review' : 'Mark for review & next'}
        </Button>
        <Button variant="outline" onClick={() => setAnswer(qid, null)}>Clear response</Button>
        <div className="flex-1" />
        <Button variant="outline" size="icon" className="lg:hidden" aria-label="Open question palette"
                onClick={() => setPaletteOpen(true)}><Grid3x3 /></Button>
        <Button variant="outline" disabled={index === 0} onClick={() => go(index - 1)}><ChevronLeft /> Previous</Button>
        <Button variant="success" onClick={() => (index === flat.length - 1 ? setConfirmOpen(true) : go(index + 1))}>
          {index === flat.length - 1 ? 'Save & finish' : 'Save & next'} <ChevronRight />
        </Button>
      </footer>

      <SubmitDialog open={confirmOpen} onOpenChange={setConfirmOpen} submitting={submitting}
                    onConfirm={() => void submit(false)} />
    </div>
  )
}

function Timer({ onExpire }: { onExpire: () => void }) {
  const [ms, setMs] = useState(remainingMs())
  const fired = useRef(false)
  useEffect(() => {
    const t = setInterval(() => {
      const left = remainingMs()
      setMs(left)
      if (left <= 0 && !fired.current) {
        fired.current = true
        onExpire()
      }
    }, 250)
    return () => clearInterval(t)
  }, [onExpire])
  const seconds = Math.ceil(ms / 1000)
  return (
    <div className={cn('rounded-md px-3 py-1 text-center font-mono tabular-nums',
      seconds <= 300 ? 'animate-pulse bg-red-600 text-white' : 'bg-white/15')}
         role="timer" aria-label="Time left">
      <span className="block text-[10px] tracking-wide uppercase opacity-80">Time left</span>
      <span className="text-lg font-semibold">{formatClock(seconds)}</span>
    </div>
  )
}

function SaveIndicator({ state }: { state: string }) {
  if (state === 'offline') {
    return <span className="flex items-center gap-1 rounded bg-amber-400 px-2 py-1 text-xs font-medium text-amber-950"><CloudOff className="size-3.5" /> Offline, saving locally</span>
  }
  if (state === 'error') {
    return <span className="rounded bg-amber-400 px-2 py-1 text-xs font-medium text-amber-950">Retrying save…</span>
  }
  if (state === 'saving') {
    return <span className="flex items-center gap-1 text-xs opacity-80"><Loader2 className="size-3.5 animate-spin" /> Saving</span>
  }
  return <span className="hidden text-xs opacity-80 sm:inline">All changes saved</span>
}

function SubmitDialog({ open, onOpenChange, onConfirm, submitting }:
                        { open: boolean; onOpenChange: (o: boolean) => void; onConfirm: () => void; submitting: boolean }) {
  const paper = useExamStore((s) => s.paper)!
  const flat = useExamStore((s) => s.flat)
  const answers = useExamStore((s) => s.answers)
  const states: AnswerState[] = ['ANSWERED', 'NOT_ANSWERED', 'NOT_VISITED', 'MARKED_FOR_REVIEW', 'ANSWERED_AND_MARKED']
  return (
    <Dialog open={open} onOpenChange={(o) => !submitting && onOpenChange(o)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Submit the test?</DialogTitle>
          <DialogDescription>You cannot change your answers after submitting.</DialogDescription>
        </DialogHeader>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-muted-foreground border-b text-left">
                <th className="py-2 pr-2 font-medium">Section</th>
                {states.map((s) => (
                  <th key={s} className="px-1 py-2 text-center font-medium"><StateBadge state={s} number="" size="sm" /></th>
                ))}
              </tr>
            </thead>
            <tbody>
              {paper.sections.map((sec, si) => {
                const qs = flat.filter((q) => q.sectionIndex === si)
                return (
                  <tr key={sec.id} className="border-b last:border-0">
                    <td className="py-2 pr-2">{sec.name}</td>
                    {states.map((s) => (
                      <td key={s} className="text-center tabular-nums">
                        {qs.filter((q) => paletteState(answers[q.questionId]) === s).length}
                      </td>
                    ))}
                  </tr>
                )
              })}
            </tbody>
          </table>
          <p className="text-muted-foreground mt-2 text-xs">
            {states.map((s) => STATE_STYLE[s].label).join(' · ')}
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" disabled={submitting} onClick={() => onOpenChange(false)}>Back to test</Button>
          <Button variant="destructive" loading={submitting} onClick={onConfirm}>Submit</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
