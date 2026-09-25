import { z } from 'zod'
import type { Question, QuestionRequest, QuestionSnapshot } from '@/types/admin'
import type { AnswerKey, Language, Media, QuestionTranslation } from '@/types/exam'

/**
 * Form model of the content studio's question editor, converted to and from the API's
 * QuestionRequest. The editor always works with two languages: the question's primary
 * language and the other one ("second"), whose texts are kept in `tr` and sent as a
 * translation when `bilingual` is on.
 */
const media = z.object({ url: z.string(), alt: z.string().optional() })
const item = z.object({ id: z.string().trim().min(1).max(5), text: z.string() })
const decimal = /^-?\d+(\.\d+)?$/
const whole = /^-?\d+$/

export const questionSchema = z.object({
  type: z.enum(['SINGLE_CORRECT', 'MULTIPLE_CORRECT', 'NUMERICAL', 'MATCH', 'PARAGRAPH']),
  difficulty: z.enum(['EASY', 'MEDIUM', 'HARD', '']),
  language: z.enum(['EN', 'HI']),
  examId: z.string(),
  subjectId: z.string(),
  chapterId: z.string(),
  topicId: z.string().min(1, 'Choose a topic'),
  subTopic: z.string().max(120),
  parentId: z.string().trim().refine((v) => v === '' || /^[0-9a-f-]{36}$/i.test(v), 'Enter a question id (UUID)'),
  text: z.string().max(20_000),
  paragraph: z.string().max(20_000),
  images: z.array(media).max(10),
  options: z.array(z.object({ id: z.string(), text: z.string().max(5000), image: z.string(), pinned: z.boolean() })).max(6),
  correct: z.array(z.string()),
  shuffleOptions: z.boolean(),
  partialRule: z.enum(['JEE_ADVANCED', 'PROPORTIONAL', 'NONE']),
  numericFormat: z.enum(['DECIMAL', 'INTEGER']),
  numMode: z.enum(['EXACT', 'RANGE']),
  numValue: z.string().trim(),
  tolerance: z.string().trim(),
  numMin: z.string().trim(),
  numMax: z.string().trim(),
  matchLeft: z.array(item).max(10),
  matchRight: z.array(item).max(10),
  pairs: z.record(z.string(), z.string()),
  marks: z.string().trim(),
  negativeMarks: z.string().trim(),
  sourceType: z.enum(['', 'PYQ', 'COACHING', 'BOOK', 'ORIGINAL']),
  source: z.string().max(200),
  year: z.string().trim().refine((v) => v === '' || (/^\d{4}$/.test(v) && +v >= 1950 && +v <= 2100), 'Enter a year from 1950 to 2100'),
  pyqShift: z.string().max(60),
  expectedTimeSec: z.string().trim().refine((v) => v === '' || (whole.test(v) && +v >= 5 && +v <= 3600), 'Enter 5 to 3600 seconds'),
  cognitiveLevel: z.enum(['', 'RECALL', 'APPLY', 'ANALYSE']),
  tags: z.string().refine((v) => splitTags(v).length <= 10, 'At most 10 tags').refine((v) => splitTags(v).every((t) => t.length <= 50), 'A tag can have at most 50 characters'),
  concepts: z.string().refine((v) => splitTags(v).length <= 15, 'At most 15 concepts').refine((v) => splitTags(v).every((t) => t.length <= 80), 'A concept can have at most 80 characters'),
  solutionText: z.string().max(20_000),
  solutionVideo: z.string().trim().refine((v) => v === '' || /^https?:\/\//.test(v), 'Enter an http(s) URL'),
  solutionImages: z.array(media).max(10),
  bilingual: z.boolean(),
  tr: z.object({
    text: z.string().max(20_000),
    paragraph: z.string().max(20_000),
    options: z.record(z.string(), z.string()),
    matchLeft: z.record(z.string(), z.string()),
    matchRight: z.record(z.string(), z.string()),
    solution: z.string().max(20_000),
  }),
  changeNote: z.string().max(500),
}).superRefine((v, ctx) => {
  const issue = (path: string, message: string) => ctx.addIssue({ code: 'custom', path: [path], message })
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
    const integer = v.numericFormat === 'INTEGER'
    const ok = (s: string) => (integer ? whole : decimal).test(s)
    if (v.numMode === 'EXACT') {
      if (!ok(v.numValue)) issue('numValue', integer ? 'Enter the correct whole number' : 'Enter the correct numeric answer')
      if (v.tolerance && integer) issue('tolerance', 'Integer answers are exact; leave the tolerance empty')
      else if (v.tolerance && !(decimal.test(v.tolerance) && +v.tolerance > 0)) issue('tolerance', 'Tolerance must be a positive number')
    } else {
      if (!ok(v.numMin)) issue('numMin', integer ? 'Enter a whole number' : 'Enter the smallest accepted value')
      if (!ok(v.numMax)) issue('numMax', integer ? 'Enter a whole number' : 'Enter the largest accepted value')
      if (ok(v.numMin) && ok(v.numMax) && +v.numMin > +v.numMax) issue('numMax', 'Must not be smaller than the minimum')
    }
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
export type TranslationForm = QuestionFormValues['tr']

export const OPTION_IDS = ['A', 'B', 'C', 'D', 'E', 'F']

export function splitTags(v: string): string[] {
  return [...new Set(v.split(',').map((t) => t.trim()).filter(Boolean))]
}

export const secondLanguage = (primary: Language): Language => (primary === 'EN' ? 'HI' : 'EN')

const emptyTranslation = (): TranslationForm => ({ text: '', paragraph: '', options: {}, matchLeft: {}, matchRight: {}, solution: '' })

export function emptyQuestion(): QuestionFormValues {
  return {
    type: 'SINGLE_CORRECT', difficulty: 'MEDIUM', language: 'EN',
    examId: '', subjectId: '', chapterId: '', topicId: '', subTopic: '', parentId: '',
    text: '', paragraph: '', images: [],
    options: OPTION_IDS.slice(0, 4).map((id) => ({ id, text: '', image: '', pinned: false })), correct: [],
    shuffleOptions: true, partialRule: 'JEE_ADVANCED',
    numericFormat: 'DECIMAL', numMode: 'EXACT', numValue: '', tolerance: '', numMin: '', numMax: '',
    matchLeft: ['P', 'Q', 'R', 'S'].map((id) => ({ id, text: '' })),
    matchRight: ['1', '2', '3', '4'].map((id) => ({ id, text: '' })), pairs: {},
    marks: '', negativeMarks: '', sourceType: '', source: '', year: '', pyqShift: '', expectedTimeSec: '',
    cognitiveLevel: '', tags: '', concepts: '',
    solutionText: '', solutionVideo: '', solutionImages: [],
    bilingual: false, tr: emptyTranslation(), changeNote: '',
  }
}

const str = (n: number | undefined | null) => (n == null ? '' : String(n))

/** Form values from a saved question, or from an old version's snapshot (for restores and diffs). */
export function toFormValues(q: Question | (QuestionSnapshot & { topic?: Question['topic'] })): QuestionFormValues {
  const base = emptyQuestion()
  const c = q.content
  const key: AnswerKey = q.answerKey ?? {}
  const language = q.language ?? 'EN'
  const t: QuestionTranslation | undefined = q.translations?.[secondLanguage(language)]
  const topic = 'topic' in q && q.topic ? q.topic : undefined
  return {
    ...base,
    type: q.type,
    difficulty: q.difficulty ?? '',
    language,
    examId: topic?.examId ?? '', subjectId: topic?.subjectId ?? '', chapterId: topic?.chapterId ?? '',
    topicId: topic?.topicId ?? ('topicId' in q ? q.topicId ?? '' : ''),
    subTopic: q.subTopic ?? '',
    parentId: q.parentId ?? '',
    text: c.text ?? '',
    paragraph: c.paragraph ?? '',
    images: c.images ?? [],
    options: c.options?.length ? c.options.map((o) => ({ id: o.id, text: o.text ?? '', image: o.image ?? '', pinned: !!o.pinned })) : base.options,
    correct: key.options ?? [],
    shuffleOptions: c.shuffleOptions !== false,
    partialRule: key.partial ?? 'JEE_ADVANCED',
    numericFormat: c.numericFormat ?? 'DECIMAL',
    numMode: key.min != null || key.max != null ? 'RANGE' : 'EXACT',
    numValue: str(key.value),
    tolerance: str(key.tolerance),
    numMin: str(key.min),
    numMax: str(key.max),
    matchLeft: c.matchLeft?.length ? c.matchLeft : base.matchLeft,
    matchRight: c.matchRight?.length ? c.matchRight : base.matchRight,
    pairs: key.pairs ?? {},
    marks: str(q.marks),
    negativeMarks: str(q.negativeMarks),
    sourceType: q.sourceType ?? '',
    source: q.source ?? '',
    year: q.year ? String(q.year) : '',
    pyqShift: q.pyqShift ?? '',
    expectedTimeSec: str(q.expectedTimeSec),
    cognitiveLevel: q.cognitiveLevel ?? '',
    tags: (q.tags ?? []).join(', '),
    concepts: (q.concepts ?? []).join(', '),
    solutionText: c.solution?.text ?? '',
    solutionVideo: c.solution?.videoUrl ?? '',
    solutionImages: c.solution?.images ?? [],
    bilingual: !!t,
    tr: t ? {
      text: t.text ?? '', paragraph: t.paragraph ?? '', options: { ...t.options }, matchLeft: { ...t.matchLeft },
      matchRight: { ...t.matchRight }, solution: t.solution ?? '',
    } : emptyTranslation(),
  }
}

const num = (s: string) => (s ? Number(s) : undefined)
const nonEmpty = (m: Record<string, string>, ids: string[]) =>
  Object.fromEntries(ids.filter((id) => m[id]?.trim()).map((id) => [id, m[id]]))

/** Only the fields relevant to the chosen type are sent, so the server's type checks pass. */
export function toRequest(v: QuestionFormValues, baseVersion?: number): QuestionRequest {
  const choice = v.type === 'SINGLE_CORRECT' || v.type === 'MULTIPLE_CORRECT'
  const images: Media[] = v.images
  const solution = v.solutionText.trim() || v.solutionVideo || v.solutionImages.length
    ? { text: v.solutionText.trim() || undefined, videoUrl: v.solutionVideo || undefined, images: v.solutionImages }
    : undefined
  const answerKey: AnswerKey | undefined = v.type === 'PARAGRAPH' ? undefined
    : choice ? { options: v.correct, partial: v.type === 'MULTIPLE_CORRECT' && v.partialRule !== 'JEE_ADVANCED' ? v.partialRule : undefined }
      : v.type === 'NUMERICAL'
        ? v.numMode === 'RANGE' ? { min: num(v.numMin), max: num(v.numMax) }
          : { value: Number(v.numValue), tolerance: v.numericFormat === 'INTEGER' ? undefined : num(v.tolerance) }
        : { pairs: Object.fromEntries(v.matchLeft.map((l) => [l.id, v.pairs[l.id]])) }
  const tr: QuestionTranslation = {
    text: v.type === 'PARAGRAPH' ? (v.tr.text.trim() || undefined) : v.tr.text.trim() || undefined,
    paragraph: v.type === 'PARAGRAPH' ? v.tr.paragraph.trim() || undefined : undefined,
    options: choice ? nonEmpty(v.tr.options, v.options.map((o) => o.id)) : {},
    matchLeft: v.type === 'MATCH' ? nonEmpty(v.tr.matchLeft, v.matchLeft.map((l) => l.id)) : {},
    matchRight: v.type === 'MATCH' ? nonEmpty(v.tr.matchRight, v.matchRight.map((r) => r.id)) : {},
    solution: v.type === 'PARAGRAPH' ? undefined : v.tr.solution.trim() || undefined,
  }
  return {
    type: v.type,
    difficulty: v.difficulty || undefined,
    language: v.language,
    topicId: v.topicId,
    subTopic: v.subTopic.trim() || null,
    parentId: v.type === 'PARAGRAPH' ? null : v.parentId || null,
    content: {
      text: v.type === 'PARAGRAPH' ? (v.text.trim() || undefined) : v.text,
      paragraph: v.type === 'PARAGRAPH' ? v.paragraph : undefined,
      images,
      options: choice ? v.options.map((o) => ({ id: o.id, text: o.text || undefined, image: o.image || undefined, pinned: o.pinned || undefined })) : [],
      matchLeft: v.type === 'MATCH' ? v.matchLeft : [],
      matchRight: v.type === 'MATCH' ? v.matchRight : [],
      solution,
      shuffleOptions: choice && !v.shuffleOptions ? false : undefined,
      numericFormat: v.type === 'NUMERICAL' ? v.numericFormat : undefined,
    },
    answerKey,
    translations: v.bilingual ? { [secondLanguage(v.language)]: tr } : {},
    marks: v.marks ? Number(v.marks) : null,
    negativeMarks: v.negativeMarks ? Number(v.negativeMarks) : null,
    sourceType: v.sourceType || null,
    source: v.source.trim() || null,
    year: v.year ? Number(v.year) : null,
    pyqShift: v.sourceType === 'PYQ' ? v.pyqShift.trim() || null : null,
    expectedTimeSec: v.expectedTimeSec ? Number(v.expectedTimeSec) : null,
    cognitiveLevel: v.cognitiveLevel || null,
    tags: splitTags(v.tags),
    concepts: splitTags(v.concepts),
    baseVersion,
    changeNote: v.changeNote.trim() || undefined,
  }
}

/** Texts of the second language still empty, in the same notation as the server ("options.C"). */
export function missingTranslation(v: QuestionFormValues): string[] {
  if (!v.bilingual) return []
  const out: string[] = []
  if (v.text.trim() && !v.tr.text.trim()) out.push('text')
  if (v.type === 'PARAGRAPH' && v.paragraph.trim() && !v.tr.paragraph.trim()) out.push('paragraph')
  if (v.type === 'SINGLE_CORRECT' || v.type === 'MULTIPLE_CORRECT') {
    v.options.forEach((o) => { if (o.text.trim() && !v.tr.options[o.id]?.trim()) out.push(`options.${o.id}`) })
  }
  if (v.type === 'MATCH') {
    v.matchLeft.forEach((l) => { if (!v.tr.matchLeft[l.id]?.trim()) out.push(`matchLeft.${l.id}`) })
    v.matchRight.forEach((r) => { if (!v.tr.matchRight[r.id]?.trim()) out.push(`matchRight.${r.id}`) })
  }
  if (v.type !== 'PARAGRAPH' && v.solutionText.trim() && !v.tr.solution.trim()) out.push('solution')
  return out
}
