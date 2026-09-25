/** Shared result/solution colours and query keys, kept separate so leaf components can import
 * them without pulling in the ResultPage module (avoids circular imports). */

export const OUTCOME_COLORS = {
  correct: 'var(--success)',
  incorrect: 'var(--destructive)',
  partial: 'var(--warning)',
  unattempted: 'var(--muted-foreground)',
}

export const resultKeys = {
  result: (id: string) => ['result', id] as const,
  comparison: (id: string) => ['comparison', id] as const,
  solutions: (id: string) => ['solutions', id] as const,
}
