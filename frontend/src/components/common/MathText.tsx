import { memo, useMemo } from 'react'
import katex from 'katex'
import { cn } from '@/lib/utils'

type Segment = { kind: 'text'; value: string } | { kind: 'math'; value: string; display: boolean }

/** Splits "text $inline$ more $$display$$" into segments. "\$" escapes a literal dollar sign. */
export function parseMath(input: string): Segment[] {
  const out: Segment[] = []
  const re = /(\$\$[\s\S]+?\$\$|(?<!\\)\$(?:\\.|[^$\\])+?\$)/g
  let last = 0
  for (const m of input.matchAll(re)) {
    if (m.index! > last) out.push({ kind: 'text', value: input.slice(last, m.index) })
    const raw = m[0]
    const display = raw.startsWith('$$')
    out.push({ kind: 'math', value: display ? raw.slice(2, -2) : raw.slice(1, -1), display })
    last = m.index! + raw.length
  }
  if (last < input.length) out.push({ kind: 'text', value: input.slice(last) })
  return out.map((s) => (s.kind === 'text' ? { ...s, value: s.value.replace(/\\\$/g, '$') } : s))
}

const cache = new Map<string, string>()

function render(tex: string, display: boolean): string {
  const key = (display ? 'D' : 'I') + tex
  let html = cache.get(key)
  if (html === undefined) {
    // trust:false (the default) forbids \href, \url and raw HTML, so question content cannot inject markup.
    html = katex.renderToString(tex, { displayMode: display, throwOnError: false, strict: 'ignore', trust: false })
    if (cache.size > 2000) cache.clear()
    cache.set(key, html)
  }
  return html
}

/**
 * Renders question text with LaTeX. Plain text goes through React (escaped), and math
 * through KaTeX. Newlines are preserved. Invalid LaTeX shows in red instead of crashing.
 */
export const MathText = memo(function MathText({ text, className, as: Tag = 'div' }:
                                                 { text?: string | null; className?: string; as?: 'div' | 'span' }) {
  const segments = useMemo(() => parseMath(text ?? ''), [text])
  return (
    <Tag className={cn('math-text leading-relaxed whitespace-pre-line', className)}>
      {segments.map((s, i) =>
        s.kind === 'text'
          ? <span key={i}>{s.value}</span>
          : <span key={i} className={s.display ? 'my-2 block overflow-x-auto' : undefined}
                  dangerouslySetInnerHTML={{ __html: render(s.value, s.display) }} />)}
    </Tag>
  )
})
