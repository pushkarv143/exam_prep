import type { QuestionSnapshot } from '@/types/admin'
import type { Language } from '@/types/exam'
import { COGNITIVE_LEVELS, LANGUAGE_LABEL, PARTIAL_RULES, SOURCE_TYPES, TYPE_LABEL, titleCase } from '../labels'

/** One human-readable field of a question version, for side-by-side comparison. */
export interface VersionField {
  key: string
  label: string
  value: string
  /** Contains Markdown/LaTeX worth rendering, not just a word. */
  rich?: boolean
}

const label = <T extends string>(list: { value: T; label: string }[], v?: T) => list.find((x) => x.value === v)?.label ?? ''

function answer(s: QuestionSnapshot): string {
  const k = s.answerKey ?? {}
  if (s.type === 'NUMERICAL') {
    if (k.min != null || k.max != null) return `${k.min} to ${k.max}`
    return k.value == null ? '' : `${k.value}${k.tolerance ? ` ± ${k.tolerance}` : ''}`
  }
  if (s.type === 'MATCH') return Object.entries(k.pairs ?? {}).map(([l, r]) => `${l} → ${r}`).join(', ')
  return (k.options ?? []).join(', ')
}

/** Flattens a snapshot into labelled fields in a stable order (primary language, then the translation). */
export function versionFields(s: QuestionSnapshot): VersionField[] {
  const c = s.content ?? { images: [], options: [], matchLeft: [], matchRight: [] }
  const out: VersionField[] = [
    { key: 'type', label: 'Type', value: TYPE_LABEL[s.type] ?? s.type },
    { key: 'difficulty', label: 'Difficulty', value: s.difficulty ? titleCase(s.difficulty) : '' },
    { key: 'language', label: 'Primary language', value: LANGUAGE_LABEL[s.language ?? 'EN'] },
    { key: 'subTopic', label: 'Sub-topic', value: s.subTopic ?? '' },
    { key: 'paragraph', label: 'Passage', value: c.paragraph ?? '', rich: true },
    { key: 'text', label: 'Question', value: c.text ?? '', rich: true },
    { key: 'images', label: 'Figures', value: (c.images ?? []).map((i) => i.url).join('\n') },
  ]
  for (const o of c.options ?? []) {
    out.push({ key: `option.${o.id}`, label: `Option ${o.id}`, rich: true,
      value: [o.text ?? '', o.image ? `[image: ${o.image}]` : '', o.pinned ? '(stays in place)' : ''].filter(Boolean).join(' ') })
  }
  for (const m of c.matchLeft ?? []) out.push({ key: `left.${m.id}`, label: `Column I · ${m.id}`, value: m.text, rich: true })
  for (const m of c.matchRight ?? []) out.push({ key: `right.${m.id}`, label: `Column II · ${m.id}`, value: m.text, rich: true })
  out.push(
    { key: 'answer', label: 'Correct answer', value: answer(s) },
    { key: 'partial', label: 'Partial marking', value: s.type === 'MULTIPLE_CORRECT' ? label(PARTIAL_RULES, s.answerKey?.partial ?? 'JEE_ADVANCED') : '' },
    { key: 'numericFormat', label: 'Answer format', value: s.type === 'NUMERICAL' ? titleCase(c.numericFormat ?? 'DECIMAL') : '' },
    { key: 'shuffle', label: 'Option shuffling', value: c.shuffleOptions === false ? 'Never shuffle' : (c.options?.length ? 'Allowed' : '') },
    { key: 'solution', label: 'Solution', value: c.solution?.text ?? '', rich: true },
    { key: 'video', label: 'Solution video', value: c.solution?.videoUrl ?? '' },
    { key: 'marks', label: 'Marks', value: s.marks == null ? '' : `+${s.marks} / −${s.negativeMarks ?? 0}` },
    { key: 'source', label: 'Source', value: [label(SOURCE_TYPES, s.sourceType), s.source, s.year, s.pyqShift].filter(Boolean).join(' · ') },
    { key: 'expected', label: 'Expected time', value: s.expectedTimeSec ? `${s.expectedTimeSec} s` : '' },
    { key: 'cognitive', label: 'Cognitive level', value: label(COGNITIVE_LEVELS, s.cognitiveLevel) },
    { key: 'tags', label: 'Tags', value: (s.tags ?? []).join(', ') },
    { key: 'concepts', label: 'Concepts', value: (s.concepts ?? []).join(', ') },
  )
  for (const [lang, t] of Object.entries(s.translations ?? {}) as [Language, NonNullable<QuestionSnapshot['translations']>[Language]][]) {
    if (!t) continue
    const name = LANGUAGE_LABEL[lang]
    out.push({ key: `${lang}.paragraph`, label: `${name} · passage`, value: t.paragraph ?? '', rich: true })
    out.push({ key: `${lang}.text`, label: `${name} · question`, value: t.text ?? '', rich: true })
    for (const o of c.options ?? []) out.push({ key: `${lang}.option.${o.id}`, label: `${name} · option ${o.id}`, value: t.options?.[o.id] ?? '', rich: true })
    for (const m of c.matchLeft ?? []) out.push({ key: `${lang}.left.${m.id}`, label: `${name} · column I · ${m.id}`, value: t.matchLeft?.[m.id] ?? '', rich: true })
    for (const m of c.matchRight ?? []) out.push({ key: `${lang}.right.${m.id}`, label: `${name} · column II · ${m.id}`, value: t.matchRight?.[m.id] ?? '', rich: true })
    out.push({ key: `${lang}.solution`, label: `${name} · solution`, value: t.solution ?? '', rich: true })
  }
  return out
}

export interface FieldChange {
  key: string
  label: string
  before: string
  after: string
  rich?: boolean
  changed: boolean
}

/** Both versions' fields aligned by key; fields empty in both are dropped. */
export function compareVersions(a: QuestionSnapshot, b: QuestionSnapshot): FieldChange[] {
  const fa = versionFields(a)
  const fb = versionFields(b)
  const byKey = new Map(fa.map((f) => [f.key, f]))
  const order = [...fa.map((f) => f.key), ...fb.map((f) => f.key).filter((k) => !byKey.has(k))]
  const bByKey = new Map(fb.map((f) => [f.key, f]))
  return order.map((key) => {
    const x = byKey.get(key)
    const y = bByKey.get(key)
    const before = x?.value ?? ''
    const after = y?.value ?? ''
    return { key, label: (y ?? x)!.label, before, after, rich: (y ?? x)!.rich, changed: before !== after }
  }).filter((f) => f.before || f.after)
}
