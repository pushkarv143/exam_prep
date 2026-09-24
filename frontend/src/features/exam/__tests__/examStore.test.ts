import { beforeEach, describe, expect, it } from 'vitest'
import type { AttemptSession } from '@/types/exam'
import {
  applySaveResult, currentSectionAnsweredCount, hasAnswer, initExam, paletteState, remainingMs, setAnswer, setMarked,
  takeChanges, tickCurrent, useExamStore, visit,
} from '../examStore'

const q = (id: string, number: number) => ({
  questionId: id, number, type: 'SINGLE_CORRECT' as const, marks: 4, negativeMarks: 1, partialMarking: false,
  images: [], options: [{ id: 'A', text: 'a' }, { id: 'B', text: 'b' }], matchLeft: [], matchRight: [],
})

function session(overrides: Partial<AttemptSession> = {}): AttemptSession {
  return {
    attemptId: 'att-1', testId: 't1', attemptNo: 1, status: 'IN_PROGRESS',
    startedAt: '2026-01-01T10:00:00Z', deadlineAt: '2026-01-01T13:00:00Z', serverNow: new Date().toISOString(),
    remainingSeconds: 3600,
    paper: {
      testId: 't1', title: 'Mock', durationMinutes: 180, totalMarks: 12, totalQuestions: 3, passages: {},
      sections: [
        { id: 's1', name: 'Physics', questions: [q('q1', 1), q('q2', 2)] },
        { id: 's2', name: 'Chemistry', maxQuestionsToAttempt: 1, questions: [q('q3', 3)] },
      ],
    },
    answers: {}, lastSeq: 0,
    antiCheat: { tabSwitchCount: 0, fullscreenExitCount: 0, maxTabSwitches: 0, autoSubmitted: false },
    ...overrides,
  }
}

describe('examStore', () => {
  beforeEach(() => localStorage.clear())

  it('derives NTA palette states', () => {
    expect(paletteState(undefined)).toBe('NOT_VISITED')
    const base = { answer: null, marked: false, visits: 1, time: 0, visited: true }
    expect(paletteState(base)).toBe('NOT_ANSWERED')
    expect(paletteState({ ...base, answer: { options: ['A'] } })).toBe('ANSWERED')
    expect(paletteState({ ...base, marked: true })).toBe('MARKED_FOR_REVIEW')
    expect(paletteState({ ...base, marked: true, answer: { options: ['A'] } })).toBe('ANSWERED_AND_MARKED')
    expect(hasAnswer({ value: '  ' })).toBe(false)
    expect(hasAnswer({ pairs: { P: '1' } })).toBe(true)
  })

  it('initialises from the server, resumes at the first unanswered question and visits it', () => {
    initExam(session({ answers: { q1: { answer: { options: ['B'] }, state: 'ANSWERED', timeSpentSeconds: 30, visits: 1, seq: 1 } }, lastSeq: 1 }))
    const s = useExamStore.getState()
    expect(s.flat.map((f) => f.questionId)).toEqual(['q1', 'q2', 'q3'])
    expect(s.index).toBe(1)
    expect(s.answers.q2.visited).toBe(true)
    expect(remainingMs()).toBeGreaterThan(3_590_000)
  })

  it('sends changes with increasing seq and clears them once saved', () => {
    initExam(session())
    setAnswer('q1', { options: ['A'] })
    setMarked('q1', true)
    const { changes, sentVersions } = takeChanges()
    const q1 = changes.find((c) => c.questionId === 'q1')!
    expect(q1).toMatchObject({ answer: { options: ['A'] }, markedForReview: true })
    expect(new Set(changes.map((c) => c.seq)).size).toBe(changes.length)
    applySaveResult(changes, sentVersions, [])
    expect(useExamStore.getState().dirty).toEqual({})
    expect(useExamStore.getState().saveState).toBe('saved')
    expect(localStorage.getItem('examprep-exam:att-1')).toBeNull()
  })

  it('keeps a question dirty if it changed while the save was in flight', () => {
    initExam(session())
    setAnswer('q1', { options: ['A'] })
    const { changes, sentVersions } = takeChanges()
    setAnswer('q1', { options: ['B'] })
    applySaveResult(changes, sentVersions, [])
    expect(useExamStore.getState().dirty.q1).toBeDefined()
  })

  it('rolls back an answer the server rejected', () => {
    initExam(session())
    setAnswer('q3', { options: ['A'] })
    const { changes, sentVersions } = takeChanges()
    applySaveResult(changes, sentVersions, [{ questionId: 'q3', reason: 'limit' }])
    expect(useExamStore.getState().answers.q3.answer).toBeNull()
  })

  it('backs up unsaved changes and restores them after a reload', () => {
    initExam(session())
    setAnswer('q2', { options: ['B'] })
    takeChanges() // request sent but never acknowledged (tab closed)
    expect(localStorage.getItem('examprep-exam:att-1')).not.toBeNull()

    initExam(session())   // reload: the server never saw the answer
    const s = useExamStore.getState()
    expect(s.answers.q2.answer).toEqual({ options: ['B'] })
    expect(s.dirty.q2).toBeDefined()
    expect(s.seq).toBeGreaterThan(0)
  })

  it('ignores a backup entry when the server has a newer change for that question', () => {
    localStorage.setItem('examprep-exam:att-1', JSON.stringify({ seq: 2, answers: {
      q2: { answer: { options: ['A'] }, marked: false, visits: 1, time: 5, visited: true, baseSeq: 2 },
      q3: { answer: { options: ['B'] }, marked: false, visits: 1, time: 5, visited: true, baseSeq: 2 },
    } }))
    initExam(session({
      lastSeq: 7,
      answers: { q2: { answer: { options: ['B'] }, state: 'ANSWERED', timeSpentSeconds: 9, visits: 2, seq: 7 } },
    }))
    const s = useExamStore.getState()
    expect(s.answers.q2.answer).toEqual({ options: ['B'] })   // server wins: seq 7 > 2
    expect(s.answers.q3.answer).toEqual({ options: ['B'] })   // server never got q3: restored
    expect(s.dirty.q3).toBeDefined()
    expect(s.dirty.q2).toBeUndefined()
  })

  it('counts time on the current question and answered questions per section', () => {
    initExam(session())
    visit(2)
    tickCurrent()
    tickCurrent()
    expect(useExamStore.getState().answers.q3.time).toBe(2)
    setAnswer('q3', { options: ['A'] })
    expect(currentSectionAnsweredCount('s2')).toBe(1)
    expect(currentSectionAnsweredCount('s1')).toBe(0)
  })
})
