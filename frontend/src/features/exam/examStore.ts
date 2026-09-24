import { create } from 'zustand'
import type {
  AnswerChange, AnswerState, AntiCheat, AttemptSession, Paper, PaperQuestion, StudentAnswer,
} from '@/types/exam'

/** Client-side state of one question. */
export interface LocalAnswer {
  answer: StudentAnswer | null
  marked: boolean
  visits: number
  time: number
  visited: boolean
}

export interface FlatQuestion extends PaperQuestion {
  sectionIndex: number
  sectionId: string
  flatIndex: number
}

export type SaveState = 'saved' | 'saving' | 'offline' | 'error'

interface ExamState {
  attemptId: string | null
  paper: Paper | null
  flat: FlatQuestion[]
  index: number
  answers: Record<string, LocalAnswer>
  /** Last answer the server accepted, used to roll back rejected changes. */
  confirmed: Record<string, StudentAnswer | null>
  /** qid → local version at the time it last changed (a pending change exists). */
  dirty: Record<string, number>
  versions: Record<string, number>
  /** qid → highest seq ever sent (or seen from the server) for that question. */
  sentSeq: Record<string, number>
  seq: number
  deadlineMs: number
  /** serverTime − clientTime, in ms. Added to Date.now() for a server-true clock. */
  offsetMs: number
  saveState: SaveState
  lastSavedAt: number | null
  antiCheat: AntiCheat
}

const EMPTY_ANTICHEAT: AntiCheat = { tabSwitchCount: 0, fullscreenExitCount: 0, maxTabSwitches: 0, autoSubmitted: false }

export const useExamStore = create<ExamState>(() => ({
  attemptId: null, paper: null, flat: [], index: 0, answers: {}, confirmed: {}, dirty: {}, versions: {}, sentSeq: {}, seq: 0,
  deadlineMs: 0, offsetMs: 0, saveState: 'saved', lastSavedAt: null, antiCheat: EMPTY_ANTICHEAT,
}))

// ---------------------------------------------------------------------------------------
// Pure helpers
// ---------------------------------------------------------------------------------------

export function hasAnswer(a: StudentAnswer | null | undefined): boolean {
  if (!a) return false
  return (a.options?.length ?? 0) > 0 || (a.value?.trim().length ?? 0) > 0 || Object.keys(a.pairs ?? {}).length > 0
}

/** NTA palette state of a question. */
export function paletteState(a: LocalAnswer | undefined): AnswerState {
  if (!a || !a.visited) return 'NOT_VISITED'
  const answered = hasAnswer(a.answer)
  if (answered && a.marked) return 'ANSWERED_AND_MARKED'
  if (answered) return 'ANSWERED'
  if (a.marked) return 'MARKED_FOR_REVIEW'
  return 'NOT_ANSWERED'
}

export function serverNow(): number {
  return Date.now() + useExamStore.getState().offsetMs
}

export function remainingMs(): number {
  return Math.max(0, useExamStore.getState().deadlineMs - serverNow())
}

// ---------------------------------------------------------------------------------------
// Offline backup: every local change is mirrored to localStorage until the server accepts it
// ---------------------------------------------------------------------------------------

/**
 * Pending local answers. `baseSeq` is the highest seq sent for that question when the
 * backup was written: if the server's seq for it is not newer, the server may be missing it.
 */
interface Backup {
  seq: number
  answers: Record<string, LocalAnswer & { baseSeq?: number }>
}

const backupKey = (attemptId: string) => `examprep-exam:${attemptId}`

function writeBackup() {
  const s = useExamStore.getState()
  if (!s.attemptId) return
  const pending: Backup['answers'] = {}
  for (const qid of Object.keys(s.dirty)) {
    if (s.answers[qid]) pending[qid] = { ...s.answers[qid], baseSeq: s.sentSeq[qid] ?? 0 }
  }
  try {
    if (Object.keys(pending).length === 0) {
      localStorage.removeItem(backupKey(s.attemptId))
    } else {
      localStorage.setItem(backupKey(s.attemptId), JSON.stringify({ seq: s.seq, answers: pending } satisfies Backup))
    }
  } catch {
    // Quota or private mode: the in-memory queue still retries.
  }
}

export function clearBackup(attemptId: string) {
  try {
    localStorage.removeItem(backupKey(attemptId))
  } catch {
    // ignore
  }
}

// ---------------------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------------------

/**
 * Loads a session from the server, then re-applies any changes from the local backup that
 * the server never received (crash, closed tab, network loss). Those are marked dirty
 * and sent with the next autosave.
 */
export function initExam(session: AttemptSession) {
  const paper = session.paper!
  const flat: FlatQuestion[] = []
  paper.sections.forEach((s, si) => s.questions.forEach((q) =>
    flat.push({ ...q, sectionIndex: si, sectionId: s.id, flatIndex: flat.length })))

  const answers: Record<string, LocalAnswer> = {}
  const confirmed: Record<string, StudentAnswer | null> = {}
  const sentSeq: Record<string, number> = {}
  for (const [qid, a] of Object.entries(session.answers)) {
    sentSeq[qid] = a.seq ?? 0
    answers[qid] = {
      answer: a.answer ?? null,
      marked: a.state === 'MARKED_FOR_REVIEW' || a.state === 'ANSWERED_AND_MARKED',
      visits: a.visits, time: a.timeSpentSeconds, visited: true,
    }
    confirmed[qid] = a.answer ?? null
  }

  let seq = session.lastSeq
  const dirty: Record<string, number> = {}
  const versions: Record<string, number> = {}
  try {
    const raw = localStorage.getItem(backupKey(session.attemptId))
    if (raw) {
      const backup = JSON.parse(raw) as Backup
      for (const [qid, { baseSeq = 0, ...local }] of Object.entries(backup.answers)) {
        const serverSeq = session.answers[qid]?.seq ?? 0
        // A newer server seq means a later change of ours already arrived: the backup is stale.
        if (serverSeq <= baseSeq && flat.some((q) => q.questionId === qid)) {
          answers[qid] = { ...local, time: Math.max(local.time, answers[qid]?.time ?? 0) }
          dirty[qid] = 1
          versions[qid] = 1
        }
      }
      seq = Math.max(seq, backup.seq ?? 0)
    }
  } catch {
    // corrupt backup: ignore it
  }

  // Resume at the first question that is not yet answered.
  const firstOpen = flat.findIndex((q) => !hasAnswer(answers[q.questionId]?.answer))
  useExamStore.setState({
    attemptId: session.attemptId, paper, flat, answers, confirmed, dirty, versions, sentSeq, seq,
    index: firstOpen >= 0 ? firstOpen : 0,
    antiCheat: session.antiCheat, saveState: 'saved', lastSavedAt: Date.now(),
  })
  syncClock(session.serverNow, session.remainingSeconds)
  visit(useExamStore.getState().index)
}

export function syncClock(serverNowIso: string, remainingSeconds: number) {
  const now = Date.parse(serverNowIso)
  useExamStore.setState({ offsetMs: now - Date.now(), deadlineMs: now + remainingSeconds * 1000 })
}

function touch(qid: string, update: (a: LocalAnswer) => LocalAnswer) {
  useExamStore.setState((s) => {
    const current = s.answers[qid] ?? { answer: null, marked: false, visits: 0, time: 0, visited: true }
    const version = (s.versions[qid] ?? 0) + 1
    return {
      answers: { ...s.answers, [qid]: update(current) },
      versions: { ...s.versions, [qid]: version },
      dirty: { ...s.dirty, [qid]: version },
    }
  })
  writeBackup()
}

export function visit(index: number) {
  const q = useExamStore.getState().flat[index]
  if (!q) return
  useExamStore.setState({ index })
  touch(q.questionId, (a) => ({ ...a, visited: true, visits: a.visits + 1 }))
}

export function setAnswer(qid: string, answer: StudentAnswer | null) {
  touch(qid, (a) => ({ ...a, answer: hasAnswer(answer) ? answer : null, visited: true }))
}

export function setMarked(qid: string, marked: boolean) {
  touch(qid, (a) => ({ ...a, marked }))
}

/** Adds one second to the question on screen (called by a 1 s ticker while the tab is visible). */
export function tickCurrent() {
  const s = useExamStore.getState()
  const q = s.flat[s.index]
  if (!q) return
  // The time change stays local until the next flush. It is marked dirty but not versioned, so it
  // never blocks the dirty flag of an answer being cleared.
  useExamStore.setState((st) => ({
    answers: { ...st.answers, [q.questionId]: { ...(st.answers[q.questionId] ?? { answer: null, marked: false, visits: 1, visited: true, time: 0 }), time: (st.answers[q.questionId]?.time ?? 0) + 1 } },
    dirty: { ...st.dirty, [q.questionId]: st.dirty[q.questionId] ?? (st.versions[q.questionId] ?? 0) },
  }))
}

/** Builds the next autosave batch. Every change gets a fresh seq. */
export function takeChanges(): { changes: AnswerChange[]; sentVersions: Record<string, number> } {
  const s = useExamStore.getState()
  let seq = s.seq
  const changes: AnswerChange[] = []
  const sentVersions: Record<string, number> = {}
  const sentSeq = { ...s.sentSeq }
  for (const qid of Object.keys(s.dirty)) {
    const a = s.answers[qid]
    if (!a) continue
    seq += 1
    changes.push({ questionId: qid, seq, answer: a.answer, markedForReview: a.marked, timeSpentSeconds: a.time,
      visits: a.visits })
    sentVersions[qid] = s.versions[qid] ?? 0
    sentSeq[qid] = seq
  }
  useExamStore.setState({ seq, sentSeq })
  if (changes.length) writeBackup()
  return { changes, sentVersions }
}

/**
 * Applies an autosave response. A question stays dirty if it changed again while the
 * request was in flight. Rejected answers are rolled back to the last confirmed value.
 */
export function applySaveResult(sent: AnswerChange[], sentVersions: Record<string, number>,
                                rejected: { questionId: string; reason: string }[]) {
  const rejectedIds = new Set(rejected.map((r) => r.questionId))
  useExamStore.setState((s) => {
    const dirty = { ...s.dirty }
    const confirmed = { ...s.confirmed }
    const answers = { ...s.answers }
    for (const c of sent) {
      if ((s.versions[c.questionId] ?? 0) === sentVersions[c.questionId]) delete dirty[c.questionId]
      if (rejectedIds.has(c.questionId)) {
        answers[c.questionId] = { ...answers[c.questionId], answer: s.confirmed[c.questionId] ?? null }
      } else {
        confirmed[c.questionId] = c.answer
      }
    }
    return { dirty, confirmed, answers, saveState: 'saved', lastSavedAt: Date.now() }
  })
  writeBackup()
}

export function currentSectionAnsweredCount(sectionId: string): number {
  const s = useExamStore.getState()
  return s.flat.filter((q) => q.sectionId === sectionId && hasAnswer(s.answers[q.questionId]?.answer)).length
}
