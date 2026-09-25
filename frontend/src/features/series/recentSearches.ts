const KEY = 'examprep-recent-searches'
const MAX = 6

export function getRecentSearches(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(v) ? (v as string[]).filter((s) => typeof s === 'string') : []
  } catch {
    return []
  }
}

export function addRecentSearch(term: string): string[] {
  const t = term.trim()
  if (!t) return getRecentSearches()
  const next = [t, ...getRecentSearches().filter((s) => s.toLowerCase() !== t.toLowerCase())].slice(0, MAX)
  try {
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // ignore
  }
  return next
}

export function clearRecentSearches(): string[] {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // ignore
  }
  return []
}
