import { describe, expect, it } from 'vitest'
import { localize, paperLanguages } from '@/lib/localize'
import { wordDiff } from '@/lib/wordDiff'
import type { Question, QuestionSnapshot } from '@/types/admin'
import { compareVersions } from '../components/versionFields'
import { emptyQuestion, missingTranslation, questionSchema, toFormValues, toRequest } from '../questionForm'

const base = () => ({ ...emptyQuestion(), examId: 'e', subjectId: 's', chapterId: 'c', topicId: 't' })
const abcd = () => ['A', 'B', 'C', 'D'].map((id) => ({ id, text: `opt ${id}`, image: '', pinned: id === 'D' }))

describe('question form (content studio)', () => {
  it('sends a numerical range and an integer format', () => {
    const v = { ...base(), type: 'NUMERICAL' as const, text: 't?', numMode: 'RANGE' as const, numMin: '2.00', numMax: '2.05' }
    expect(questionSchema.safeParse(v).success).toBe(true)
    const r = toRequest(v)
    expect(r.answerKey).toEqual({ min: 2, max: 2.05 })
    expect(r.content.numericFormat).toBe('DECIMAL')

    const int = { ...base(), type: 'NUMERICAL' as const, text: 'n?', numericFormat: 'INTEGER' as const, numValue: '2.5' }
    expect(questionSchema.safeParse(int).success).toBe(false)
    expect(toRequest({ ...int, numValue: '20', tolerance: '1' }).answerKey).toEqual({ value: 20, tolerance: undefined })
    expect(questionSchema.safeParse({ ...v, numMin: '3', numMax: '2' }).success).toBe(false)
  })

  it('sends partial rules, pinned options and the shuffle switch only for choice questions', () => {
    const v = { ...base(), type: 'MULTIPLE_CORRECT' as const, text: 'Pick', options: abcd(), correct: ['A', 'B'],
      partialRule: 'PROPORTIONAL' as const, shuffleOptions: false }
    const r = toRequest(v)
    expect(r.answerKey).toEqual({ options: ['A', 'B'], partial: 'PROPORTIONAL' })
    expect(r.content.options.find((o) => o.id === 'D')?.pinned).toBe(true)
    expect(r.content.shuffleOptions).toBe(false)
    expect(toRequest({ ...v, partialRule: 'JEE_ADVANCED' }).answerKey?.partial).toBeUndefined()   // default is not sent
    expect(toRequest({ ...v, type: 'SINGLE_CORRECT', correct: ['A'] }).answerKey?.partial).toBeUndefined()
  })

  it('sends the second language only when bilingual, without empty texts', () => {
    const v = { ...base(), text: 'Q', options: abcd(), correct: ['A'], solutionText: 'Because',
      tr: { text: 'प्रश्न', paragraph: '', options: { A: 'एक', B: '', X: 'stale' }, matchLeft: {}, matchRight: {}, solution: '' } }
    expect(toRequest(v).translations).toEqual({})
    const r = toRequest({ ...v, bilingual: true })
    expect(r.translations?.HI).toEqual({ text: 'प्रश्न', options: { A: 'एक' }, matchLeft: {}, matchRight: {},
      paragraph: undefined, solution: undefined })
    expect(missingTranslation({ ...v, bilingual: true })).toEqual(['options.B', 'options.C', 'options.D', 'solution'])
    // A Hindi-first question translates into English.
    expect(Object.keys(toRequest({ ...v, bilingual: true, language: 'HI' }).translations ?? {})).toEqual(['EN'])
  })

  it('round-trips a saved question', () => {
    const q = {
      id: '1', type: 'NUMERICAL', difficulty: 'HARD', language: 'EN', subTopic: 'Free fall', parentId: undefined,
      topic: { examId: 'e', subjectId: 's', chapterId: 'c', topicId: 't' },
      content: { text: 'drop', images: [], options: [], matchLeft: [], matchRight: [], numericFormat: 'DECIMAL' },
      answerKey: { min: 2, max: 2.05 }, translations: { HI: { text: 'गिराना' } }, marks: 4, negativeMarks: 0,
      status: 'DRAFT', sourceType: 'PYQ', source: 'JEE Main', year: 2023, pyqShift: 'Shift 2', expectedTimeSec: 90,
      cognitiveLevel: 'APPLY', tags: ['a'], concepts: ['free fall'], currentVersion: 3, openComments: 0,
      usedInPublishedTests: 0, createdAt: '', updatedAt: '', actions: [],
    } as unknown as Question
    const back = toRequest(toFormValues(q), 3)
    expect(back.answerKey).toEqual({ min: 2, max: 2.05 })
    expect(back.translations?.HI?.text).toBe('गिराना')
    expect(back).toMatchObject({ subTopic: 'Free fall', sourceType: 'PYQ', pyqShift: 'Shift 2', expectedTimeSec: 90,
      cognitiveLevel: 'APPLY', concepts: ['free fall'], baseVersion: 3 })
  })
})

describe('wordDiff', () => {
  it('marks removed and added words and keeps the rest', () => {
    const parts = wordDiff('A car starts from rest', 'A bus starts from rest quickly')
    expect(parts.filter((p) => p.type === 'removed').map((p) => p.text.trim())).toEqual(['car'])
    expect(parts.filter((p) => p.type === 'added').map((p) => p.text.trim()).join(' ')).toContain('bus')
    expect(parts.map((p) => (p.type === 'removed' ? '' : p.text)).join('')).toBe('A bus starts from rest quickly')
    expect(wordDiff('same', 'same')).toEqual([{ type: 'same', text: 'same' }])
    expect(wordDiff('', 'new')).toEqual([{ type: 'added', text: 'new' }])
  })
})

describe('localize', () => {
  const q = {
    text: 'Q', language: 'EN' as const,
    options: [{ id: 'A', text: 'one' }, { id: 'B', text: 'two' }], matchLeft: [], matchRight: [],
    translations: { HI: { text: 'प्रश्न', options: { A: 'एक' } } },
  }
  it('uses the translation and falls back per text', () => {
    const hi = localize(q, 'HI')
    expect(hi.text).toBe('प्रश्न')
    expect(hi.options.map((o) => o.text)).toEqual(['एक', 'two'])
    expect(hi.options.map((o) => o.id)).toEqual(['A', 'B'])
    expect(localize(q, 'EN').text).toBe('Q')
  })
  it('lists the languages a paper offers', () => {
    expect(paperLanguages([q, { language: 'EN' }])).toEqual(['EN', 'HI'])
    expect(paperLanguages([{ language: 'EN' }])).toEqual(['EN'])
  })
})

describe('compareVersions', () => {
  const snap = (text: string, key: string[], hi?: string): QuestionSnapshot => ({
    type: 'SINGLE_CORRECT', difficulty: 'EASY', language: 'EN',
    content: { text, images: [], options: [{ id: 'A', text: '1' }, { id: 'B', text: '2' }], matchLeft: [], matchRight: [] },
    answerKey: { options: key }, translations: hi ? { HI: { text: hi } } : undefined, marks: 4, negativeMarks: 1, tags: [],
  })
  it('aligns fields and flags only real changes', () => {
    const rows = compareVersions(snap('Old', ['A']), snap('New', ['B'], 'नया'))
    const changed = rows.filter((r) => r.changed).map((r) => r.key)
    expect(changed).toEqual(expect.arrayContaining(['text', 'answer', 'HI.text']))
    expect(changed).not.toContain('option.A')
    expect(rows.find((r) => r.key === 'answer')).toMatchObject({ before: 'A', after: 'B' })
  })
})
