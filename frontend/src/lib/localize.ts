import type { Language, MatchItem, Option, StudentTranslation } from '@/types/exam'

/** Anything with a primary-language text and optional translations (paper question, review item). */
export interface Translatable {
  text?: string
  options: Option[]
  matchLeft: MatchItem[]
  matchRight: MatchItem[]
  language?: Language
  translations?: Partial<Record<Language, StudentTranslation>>
}

/**
 * The question's texts in `lang`: the translation where one exists, otherwise the
 * primary text (so a partly translated question never shows blanks). Ids never change.
 */
export function localize<T extends Translatable>(q: T, lang: Language): T & { translated: boolean } {
  if (!lang || (q.language ?? 'EN') === lang) return { ...q, translated: false }
  const t = q.translations?.[lang]
  if (!t) return { ...q, translated: false }
  return {
    ...q,
    text: t.text || q.text,
    options: q.options.map((o) => ({ ...o, text: t.options?.[o.id] || o.text })),
    matchLeft: q.matchLeft.map((m) => ({ ...m, text: t.matchLeft?.[m.id] || m.text })),
    matchRight: q.matchRight.map((m) => ({ ...m, text: t.matchRight?.[m.id] || m.text })),
    translated: true,
  }
}

/** Languages a paper offers: the primary languages of its questions plus any translation. */
export function paperLanguages(questions: Pick<Translatable, 'language' | 'translations'>[]): Language[] {
  const set = new Set<Language>()
  for (const q of questions) {
    set.add(q.language ?? 'EN')
    for (const l of Object.keys(q.translations ?? {}) as Language[]) set.add(l)
  }
  return (['EN', 'HI'] as Language[]).filter((l) => set.has(l))
}
