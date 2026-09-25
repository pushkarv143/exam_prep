import { Suspense, useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, ScrollRestoration, useLocation, useNavigate } from 'react-router-dom'
import { CircleHelp, Search, ShieldAlert } from 'lucide-react'
import { useQueueCounts } from '@/api/content'
import { useApprovalSummary, usePermissions } from '@/api/portal'
import { PageLoader } from '@/components/common/States'
import { Navbar } from '@/components/layout/Navbar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { ADMIN_NAV, ALL_NAV_ITEMS } from './shell/adminNav'
import { Breadcrumbs } from './shell/Breadcrumbs'
import { CommandPalette } from './shell/CommandPalette'
import { HelpDrawer } from './shell/HelpDrawer'
import { markVisited, useVisitTracker } from './shell/onboarding'
import { useAuthStore } from '@/store/auth'

/**
 * Admin Portal shell: permission-aware sidebar (grouped), breadcrumbs, Ctrl+K palette,
 * "?" help drawer, "g x" shortcuts, a live approvals badge, and the 2FA gate when the
 * organisation enforces 2FA for staff.
 */
export function AdminLayout() {
  const { access, any, can, loading } = usePermissions()
  const location = useLocation()
  const navigate = useNavigate()
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)
  const summary = useApprovalSummary(!!access)
  const userId = useAuthStore((s) => s.user?.id)
  useVisitTracker()
  useEffect(() => {
    if (paletteOpen) markVisited(userId, 'palette')
  }, [paletteOpen, userId])
  const waiting = summary.data?.waitingForMe ?? 0
  const queues = useQueueCounts(!!access && any('question.review', 'question.approve', 'question.publish', 'question.create'))
  // Work waiting on this person: reviews assigned to them, approved questions to publish, their questions sent back.
  const reviewWork = queues.data ? (can('question.review') ? queues.data.assignedToMe : 0)
    + (can('question.publish') ? queues.data.approved : 0) + queues.data.changesRequested : 0
  const badges: Record<string, { n: number; label: string }> = {
    '/admin/approvals': { n: waiting, label: 'waiting for you' },
    '/admin/review': { n: reviewWork, label: 'questions need you' },
  }

  useShortcuts({
    palette: () => setPaletteOpen(true),
    help: () => setHelpOpen(true),
    go: (key) => {
      const item = ALL_NAV_ITEMS.find((i) => i.shortcut === key && (i.perms.length === 0 || any(...i.perms)))
      if (item) navigate(item.to)
    },
  })

  const groups = ADMIN_NAV.map((g) => ({ ...g, items: g.items.filter((i) => i.perms.length === 0 || any(...i.perms)) }))
    .filter((g) => g.items.length > 0)
  const item = ({ isActive }: { isActive: boolean }) =>
    cn('flex shrink-0 items-center gap-2 rounded-md px-3 py-2 text-sm font-medium whitespace-nowrap',
      isActive ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-muted')

  const mfaBlocked = !!access && access.mfaEnforced && !access.mfaVerified && location.pathname !== '/admin/security'

  return (
    <div className="bg-muted/30 flex min-h-screen flex-col">
      <Navbar />
      <div className="mx-auto flex w-full max-w-[1400px] flex-1 flex-col gap-6 px-4 py-6 sm:px-6 lg:flex-row">
        <aside className="lg:w-60 lg:shrink-0">
          <div className="mb-3 hidden gap-2 lg:flex">
            <Button variant="outline" size="sm" className="text-muted-foreground flex-1 justify-start" onClick={() => setPaletteOpen(true)}>
              <Search /> Search… <kbd className="ml-auto font-mono text-[10px]">Ctrl K</kbd>
            </Button>
            <Button variant="outline" size="icon" className="size-8" aria-label="Help" onClick={() => setHelpOpen(true)}>
              <CircleHelp />
            </Button>
          </div>
          <nav className="flex gap-1 overflow-x-auto lg:sticky lg:top-24 lg:flex-col lg:gap-4" aria-label="Admin">
            {loading && <p className="text-muted-foreground px-3 text-sm">Loading…</p>}
            {groups.map((g) => (
              <div key={g.label} className="flex gap-1 lg:flex-col">
                <p className="text-muted-foreground hidden px-3 text-[11px] font-semibold tracking-wide uppercase lg:block">{g.label}</p>
                {g.items.map((i) => (
                  <NavLink key={i.to} to={i.to} end={i.to === '/admin'} className={item}>
                    <i.icon className="size-4" /> {i.label}
                    {(badges[i.to]?.n ?? 0) > 0 && (
                      <span className="bg-destructive ml-auto rounded-full px-1.5 text-[11px] leading-5 font-semibold text-white"
                            aria-label={`${badges[i.to].n} ${badges[i.to].label}`}>{badges[i.to].n}</span>
                    )}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>
        </aside>
        <main className="min-w-0 flex-1">
          <Breadcrumbs />
          {mfaBlocked ? <MfaGate /> : <Suspense fallback={<PageLoader />}><Outlet /></Suspense>}
        </main>
      </div>
      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
      <HelpDrawer open={helpOpen} onOpenChange={setHelpOpen} />
      <ScrollRestoration />
    </div>
  )
}

function MfaGate() {
  return (
    <Card className="mx-auto mt-10 max-w-lg">
      <CardContent className="space-y-4 py-6 text-center">
        <ShieldAlert className="text-warning mx-auto size-10" />
        <h1 className="text-xl font-semibold">Two-factor authentication is required</h1>
        <p className="text-muted-foreground text-sm">
          Your organisation requires 2FA for everyone using the admin portal. Set it up once with an authenticator
          app (Google Authenticator, Microsoft Authenticator, Authy…). It takes about a minute.
        </p>
        <Button asChild><Link to="/admin/security">Set up two-factor authentication</Link></Button>
      </CardContent>
    </Card>
  )
}

/** Global admin shortcuts. Ignored while typing in inputs, except Ctrl/⌘+K. */
function useShortcuts(h: { palette: () => void; help: () => void; go: (key: string) => void }) {
  const pendingG = useRef(0)
  const handlers = useRef(h)
  handlers.current = h
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        handlers.current.palette()
        return
      }
      const t = e.target as HTMLElement | null
      if (e.ctrlKey || e.metaKey || e.altKey || (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName)))) {
        return
      }
      if (document.querySelector('[role="dialog"]')) {
        return   // a dialog or drawer is open
      }
      if (e.key === '?') {
        e.preventDefault()
        handlers.current.help()
      } else if (e.key === 'g') {
        pendingG.current = Date.now()
      } else if (Date.now() - pendingG.current < 1000 && /^[a-z]$/.test(e.key)) {
        pendingG.current = 0
        handlers.current.go(e.key)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
}
