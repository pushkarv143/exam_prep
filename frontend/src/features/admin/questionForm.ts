import { z } from 'zod'
import type { Question, QuestionRequest } from '@/types/admin'
import type { Media } from '@/types/exam'

/** Form model for the question editor, converted to and from the API's QuestionRequest. */
const media = z.object({ url: z.string(), alt: z.string().optional() })
const item = z.object({ id: z.string().trim().min(1).max(5), text: z.string() })

export const questionSchema = z.object({
  type: z.enum(['SINGLE_CORRECT', 'MULTIPLE_CORRECT', 'NUMERICAL', 'MATCH', 'PARAGRAPH']),
  difficulty: z.enum(['EASY', 'MEDIUM', 'HARD', '']),
  language: z.enum(['EN', 'HI']),
  status: z.enum(['DRAFT', 'ACTIVE', 'ARCHIVED']),
  examId: z.string(),
  subjectId: z.string(),
  chapterId: z.string(),
  topicId: z.string().min(1, 'Choose a topic'),
  parentId: z.string().trim().refine((v) => v === '' || /^[0-9a-f-]{36}$/i.test(v), 'Enter a question id (UUID)'),
  text: z.string().max(20_000),
  paragraph: z.string().max(20_000),
  images: z.array(media).max(10),
  options: z.array(z.object({ id: z.string(), text: z.string().max(5000), image: z.string() })).max(6),
  correct: z.array(z.string()),
  numValue: z.string().trim(),
  tolerance: z.string().trim(),
  matchLeft: z.array(item).max(10),
  matchRight: z.array(item).max(10),
  pairs: z.record(z.string(), z.string()),
  marks: z.string().trim(),
  negativeMarks: z.string().trim(),
  source: z.string().max(200),
  year: z.string().trim().refine((v) => v === '' || (/^\d{4}$/.test(v) && +v >= 1950 && +v <= 2100), 'Enter a year from 1950 to 2100'),
  tags: z.string().refine((v) => splitTags(v).length <= 10, 'At most 10 tags').refine((v) => splitTags(v).every((t) => t.length <= 50), 'A tag can have at most 50 characters'),
  solutionText: z.string().max(20_000),
  solutionVideo: z.string().trim().refine((v) => v === '' || /^https?:\/\//.test(v), 'Enter an http(s) URL'),
  solutionImages: z.array(media).max(10),
}).superRefine((v, ctx) => {
  const issue = (path: string, message: string) => ctx.addIssue({ code: 'custom', path: [path], message })
  const decimal = /^-?\d+(\.\d+)?$/
  if (v.marks && !(decimal.test(v.marks) && +v.marks >= 0 && +v.marks <= 100)) issue('marks', 'Enter 0 to 100')
  if (v.negativeMarks && !(decimal.test(v.negativeMarks) && +v.negativeMarks >= 0 && +v.negativeMarks <= 100)) issue('negativeMarks', 'Enter 0 to 100')

  if (v.type === 'PARAGRAPH') {
    if (!v.paragraph.trim()) issue('paragraph', 'Enter the passage')
    return
  }
  if (!v.text.trim()) issue('text', 'Enter the question text')
  if (v.type === 'SINGLE_CORRECT' || v.type === 'MULTIPLE_CORRECT') {
    if (v.options.length < 2) issue('options', 'Add at least 2 options')
    v.options.forEach((o, i) => {
      if (!o.text.trim() && !o.image) ctx.addIssue({ code: 'custom', path: ['options', i, 'text'], message: 'Enter text or add an image' })
    })
    if (v.correct.length === 0) issue('correct', 'Mark the correct option')
    else if (v.type === 'SINGLE_CORRECT' && v.correct.length !== 1) issue('correct', 'Mark exactly one correct option')
  }
  if (v.type === 'NUMERICAL') {
    if (!decimal.test(v.numValue)) issue('numValue', 'Enter the correct numeric answer')
    if (v.tolerance && !(decimal.test(v.tolerance) && +v.tolerance > 0)) issue('tolerance', 'Tolerance must be a positive number')
  }
  if (v.type === 'MATCH') {
    if (v.matchLeft.length === 0 || v.matchRight.length === 0) issue('matchLeft', 'Add items to both columns')
    const left = v.matchLeft.map((l) => l.id.toUpperCase())
    const right = v.matchRight.map((r) => r.id.toUpperCase())
    if (new Set(left).size !== left.length || new Set(right).size !== right.length) issue('matchLeft', 'Item ids must be unique in each column')
    if (v.matchLeft.some((l) => !l.text.trim()) || v.matchRight.some((r) => !r.text.trim())) issue('matchLeft', 'Every item needs text')
    if (v.matchLeft.some((l) => !v.pairs[l.id] || !right.includes(v.pairs[l.id].toUpperCase()))) issue('pairs', 'Choose a match for every item in column I')
  }
})

export type QuestionFormValues = z.infer<typeof questionSchema>

export const OPTION_IDS = ['A', 'B', 'C', 'D', 'E', 'F']

export function splitTags(v: string): string[] {
  return [...new Set(v.split(',').map((t) => t.trim()).filter(Boolean))]
}

export function emptyQuestion(): QuestionFormValues {
  return {
    type: 'SINGLE_CORRECT', difficulty: 'MEDIUM', language: 'EN', status: 'ACTIVE',
    examId: '', subjectId: '', chapterId: '', topicId: '', parentId: '',
    text: '', paragraph: '', images: [],
    options: OPTION_IDS.slice(0, 4).map((id) => ({ id, text: '', image: '' })), correct: [],
    numValue: '', tolerance: '',
    matchLeft: ['P', 'Q', 'R', 'S'].map((id) => ({ id, text: '' })),
    matchRight: ['1', '2', '3', '4'].map((id) => ({ id, text: '' })), pairs: {},
    marks: '', negativeMarks: '', source: '', year: '', tags: '',
    solutionText: '', solutionVideo: '', solutionImages: [],
  }
}

export function toFormValues(q: Question): QuestionFormValues {
  const base = emptyQuestion()
  const c = q.content
  return {
    ...base,
    type: q.type,
    difficulty: q.difficulty ?? '',
    language: q.language ?? 'EN',
    status: q.status,
    examId: q.topic.examId, subjectId: q.topic.subjectId, chapterId: q.topic.chapterId, topicId: q.topic.topicId,
    parentId: q.parentId ?? '',
    text: c.text ?? '',
    paragraph: c.paragraph ?? '',
    images: c.images ?? [],
    options: c.options?.length ? c.options.map((o) => ({ id: o.id, text: o.text ?? '', image: o.image ?? '' })) : base.options,
    correct: q.answerKey.options ?? [],
    numValue: q.answerKey.value != null ? String(q.answerKey.value) : '',
    tolerance: q.answerKey.tolerance != null ? String(q.answerKey.tolerance) : '',
    matchLeft: c.matchLeft?.length ? c.matchLeft : base.matchLeft,
    matchRight: c.matchRight?.length ? c.matchRight : base.matchRight,
    pairs: q.answerKey.pairs ?? {},
    marks: String(q.marks ?? ''),
    negativeMarks: String(q.negativeMarks ?? ''),
    source: q.source ?? '',
    year: q.year ? String(q.year) : '',
    tags: (q.tags ?? []).join(', '),
    solutionText: c.solution?.text ?? '',
    solutionVideo: c.solution?.videoUrl ?? '',
    solutionImages: c.solution?.images ?? [],
  }
}

/** Only the fields relevant to the chosen type are sent, so the server's type checks pass. */
export function toRequest(v: QuestionFormValues): QuestionRequest {
  const choice = v.type === 'SINGLE_CORRECT' || v.type === 'MULTIPLE_CORRECT'
  const images: Media[] = v.images
  const solution = v.solutionText.trim() || v.solutionVideo || v.solutionImages.length
    ? { text: v.solutionText.trim() || undefined, videoUrl: v.solutionVideo || undefined, images: v.solutionImages }
    : undefined
  return {
    type: v.type,
    difficulty: v.difficulty || undefined,
    language: v.language,
    status: v.status,
    topicId: v.topicId,
    parentId: v.type === 'PARAGRAPH' ? null : v.parentId || null,
    content: {
      text: v.type === 'PARAGRAPH' ? (v.text.trim() || undefined) : v.text,
      paragraph: v.type === 'PARAGRAPH' ? v.paragraph : undefined,
      images,
      options: choice ? v.options.map((o) => ({ id: o.id, text: o.text || undefined, image: o.image || undefined })) : [],
      matchLeft: v.type === 'MATCH' ? v.matchLeft : [],
      matchRight: v.type === 'MATCH' ? v.matchRight : [],
      solution,
    },
    answerKey: v.type === 'PARAGRAPH' ? undefined
      : choice ? { options: v.correct }
        : v.type === 'NUMERICAL' ? { value: Number(v.numValue), tolerance: v.tolerance ? Number(v.tolerance) : undefined }
          : { pairs: Object.fromEntries(v.matchLeft.map((l) => [l.id, v.pairs[l.id]])) },
    marks: v.marks ? Number(v.marks) : null,
    negativeMarks: v.negativeMarks ? Number(v.negativeMarks) : null,
    source: v.source.trim() || null,
    year: v.year ? Number(v.year) : null,
    tags: splitTags(v.tags),
  }
}
