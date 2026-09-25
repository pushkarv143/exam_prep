import { Link, useMatches } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'

/** Routes declare `handle: { crumb: 'Tests' }`; this renders Admin › Tests › Builder. */
export interface CrumbHandle {
  crumb?: string
}

export function Breadcrumbs() {
  const matches = useMatches()
  const crumbs = matches
    .filter((m) => (m.handle as CrumbHandle | undefined)?.crumb)
    .map((m) => ({ path: m.pathname, label: (m.handle as CrumbHandle).crumb! }))
  if (crumbs.length <= 1) {
    return null
  }
  return (
    <nav aria-label="Breadcrumb" className="text-muted-foreground mb-3 text-sm">
      <ol className="flex flex-wrap items-center gap-1">
        {crumbs.map((c, i) => (
          <li key={c.path + i} className="flex items-center gap-1">
            {i > 0 && <ChevronRight className="size-3.5" aria-hidden />}
            {i === crumbs.length - 1
              ? <span aria-current="page" className="text-foreground">{c.label}</span>
              : <Link to={c.path} className="hover:text-foreground hover:underline">{c.label}</Link>}
          </li>
        ))}
      </ol>
    </nav>
  )
}
