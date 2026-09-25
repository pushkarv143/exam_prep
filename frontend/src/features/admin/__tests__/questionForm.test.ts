import { describe, expect, it } from 'vitest'
import { parseMath } from '@/components/common/MathText'
import { emptyQuestion, questionSchema, splitTags, toRequest } from '../questionForm'

const base = () => ({ ...emptyQuestion(), examId: 'e', subjectId: 's', chapterId: 'c', topicId: 't' })

describe('question form', () => {
  it('requires a correct option for choice questions', () => {
    const v = { ...base(), text: 'What is 2+2?', options: [{ id: 'A', text: '3', image: '', pinned: false }, { id: 'B', text: '4', image: '', pinned: false }] }
    const r = questionSchema.safeParse(v)
    expect(r.success).toBe(false)
    expect(r.error?.issues.some((i) => i.path[0] === 'correct')).toBe(true)
    expect(questionSchema.safeParse({ ...v, correct: ['B'] }).success).toBe(true)
  })

  it('allows exactly one correct option for single-correct', () => {
    const v = { ...base(), text: 'x', options: [{ id: 'A', text: '1', image: '', pinned: false }, { id: 'B', text: '2', image: '', pinned: false }], correct: ['A', 'B'] }
    expect(questionSchema.safeParse(v).success).toBe(false)
    expect(questionSchema.safeParse({ ...v, type: 'MULTIPLE_CORRECT' }).success).toBe(true)
  })

  it('sends only the fields of the chosen type', () => {
    const numerical = toRequest({ ...base(), type: 'NUMERICAL', text: 'g?', numValue: '9.8', tolerance: '0.1', tags: 'pyq, pyq, mechanics' })
    expect(numerical.content.options).toEqual([])
    expect(numerical.answerKey).toEqual({ value: 9.8, tolerance: 0.1 })
    expect(numerical.tags).toEqual(['pyq', 'mechanics'])
    expect(numerical.marks).toBeNull()

    const match = toRequest({
      ...base(), type: 'MATCH', text: 'Match', matchLeft: [{ id: 'P', text: 'a' }], matchRight: [{ id: '1', text: 'b' }], pairs: { P: '1', X: '2' },
    })
    expect(match.answerKey).toEqual({ pairs: { P: '1' } })
    expect(match.content.matchLeft).toHaveLength(1)

    const paragraph = toRequest({ ...base(), type: 'PARAGRAPH', paragraph: 'Passage', parentId: 'ignored' })
    expect(paragraph.answerKey).toBeUndefined()
    expect(paragraph.parentId).toBeNull()
    expect(paragraph.content.paragraph).toBe('Passage')
  })

  it('validates match pairs', () => {
    const v = { ...base(), type: 'MATCH' as const, text: 'Match', matchLeft: [{ id: 'P', text: 'a' }], matchRight: [{ id: '1', text: 'b' }], pairs: {} }
    expect(questionSchema.safeParse(v).success).toBe(false)
    expect(questionSchema.safeParse({ ...v, pairs: { P: '1' } }).success).toBe(true)
  })

  it('splits tags', () => {
    expect(splitTags(' a, ,b , a')).toEqual(['a', 'b'])
  })
})

describe('parseMath', () => {
  it('splits inline and display math and keeps escaped dollars', () => {
    expect(parseMath('Costs \\$5 and $x^2$ then $$\\int f$$ end')).toEqual([
      { kind: 'text', value: 'Costs $5 and ' },
      { kind: 'math', value: 'x^2', display: false },
      { kind: 'text', value: ' then ' },
      { kind: 'math', value: '\\int f', display: true },
      { kind: 'text', value: ' end' },
    ])
  })

  it('leaves text without math untouched', () => {
    expect(parseMath('plain')).toEqual([{ kind: 'text', value: 'plain' }])
  })
})
