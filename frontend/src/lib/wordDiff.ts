/** A run of text that is unchanged, added or removed between two versions. */
export interface DiffPart {
  type: 'same' | 'added' | 'removed'
  text: string
}

const MAX_CELLS = 4_000_000

/**
 * Word-level diff (longest common subsequence over words and whitespace). Question texts
 * are short, so the O(n·m) table is fine; above ~4M cells it falls back to "all removed,
 * all added" rather than freezing the tab.
 */
export function wordDiff(before: string, after: string): DiffPart[] {
  if (before === after) return before ? [{ type: 'same', text: before }] : []
  const a = tokens(before)
  const b = tokens(after)
  if (a.length * b.length > MAX_CELLS) {
    return merge([
      ...(before ? [{ type: 'removed' as const, text: before }] : []),
      ...(after ? [{ type: 'added' as const, text: after }] : []),
    ])
  }
  const cols = b.length + 1
  const lcs = new Uint32Array((a.length + 1) * cols)
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i * cols + j] = a[i] === b[j] ? lcs[(i + 1) * cols + j + 1] + 1
        : Math.max(lcs[(i + 1) * cols + j], lcs[i * cols + j + 1])
    }
  }
  const parts: DiffPart[] = []
  let i = 0
  let j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      parts.push({ type: 'same', text: a[i] })
      i++
      j++
    } else if (lcs[(i + 1) * cols + j] >= lcs[i * cols + j + 1]) {
      parts.push({ type: 'removed', text: a[i++] })
    } else {
      parts.push({ type: 'added', text: b[j++] })
    }
  }
  while (i < a.length) parts.push({ type: 'removed', text: a[i++] })
  while (j < b.length) parts.push({ type: 'added', text: b[j++] })
  return merge(parts)
}

function tokens(s: string): string[] {
  return s.split(/(\s+)/).filter((t) => t !== '')
}

/** Joins neighbouring parts of the same type. */
function merge(parts: DiffPart[]): DiffPart[] {
  const out: DiffPart[] = []
  for (const p of parts) {
    const last = out[out.length - 1]
    if (last && last.type === p.type) last.text += p.text
    else out.push({ ...p })
  }
  return out
}
