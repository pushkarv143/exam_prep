import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, Circle, Command, Stamp } from 'lucide-react'
import { useApprovalSummary, usePermissions } from '@/api/portal'
import { PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/auth'
import { ADMIN_NAV } from './shell/adminNav'
import { readVisited } from './shell/onboarding'

interface Step {
  id: string
  title: string
  detail: string
  to: string
  perms: string[]
  done: (ctx: { visited: Set<string>; mfaEnabled: boolean }) => boolean
}

const STEPS: Step[] = [
  { id: 'mfa', title: 'Turn on two-factor authentication', detail: 'Protect your account with a code from your phone.',
    to: '/admin/security', perms: [], done: (c) => c.mfaEnabled },
  { id: 'roles', title: 'Review roles & permissions', detail: 'Check who can publish tests, change results or see payments.',
    to: '/admin/roles', perms: ['role.view'], done: (c) => c.visited.has('/admin/roles') },
  { id: 'policies', title: 'Choose which actions need a second person', detail: 'Maker-checker for publishing, final ranks, refunds…',
    to: '/admin/security#policies', perms: ['security.manage'], done: (c) => c.visited.has('/admin/security') },
  { id: 'audit', title: 'Look at the audit log', detail: 'See who changed what, with before/after differences.',
    to: '/admin/audit', perms: ['audit.view'], done: (c) => c.visited.has('/admin/audit') },
  { id: 'content', title: 'Add questions to the bank', detail: 'Write one in the editor or bulk-import a sheet.',
    to: '/admin/questions', perms: ['question.create'], done: (c) => c.visited.has('/admin/questions') },
  { id: 'palette', title: 'Try the command palette', detail: 'Press Ctrl+K anywhere to jump to a page or find a record.',
    to: '/admin', perms: [], done: (c) => c.visited.has('palette') },
]

export default function AdminHomePage() {
  const user = useAuthStore((s) => s.user)
  const { access, any, loading } = usePermissions()
  const summary = useApprovalSummary(!!access)
  const [visited, setVisited] = useState<Set<string>>(new Set())
  useEffect(() => setVisited(readVisited(user?.id)), [user?.id])

  if (loading || !access) return <PageLoader />
  const steps = STEPS.filter((s) => s.perms.length === 0 || any(...s.perms))
  const ctx = { visited, mfaEnabled: access.mfaEnabled }
  const done = steps.filter((s) => s.done(ctx)).length
  const waiting = summary.data?.waitingForMe ?? 0

  return (
    <>
      <PageHeader title={`Welcome, ${user?.fullName.split(' ')[0] ?? ''}`}
                  description={`Signed in as ${access.roles.map((r) => r.toLowerCase().replace(/_/g, ' ')).join(', ')}`} />
      {waiting > 0 && (
        <Card className="border-warning/50 bg-warning/10 mb-6 py-4">
          <CardContent className="flex flex-wrap items-center gap-3">
            <Stamp className="size-5" />
            <p className="flex-1 font-medium">{waiting} request{waiting > 1 ? 's are' : ' is'} waiting for your approval.</p>
            <Button asChild size="sm"><Link to="/admin/approvals">Review now</Link></Button>
          </CardContent>
        </Card>
      )}
      <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
        <Card>
          <CardHeader>
            <CardTitle>Getting started</CardTitle>
            <CardDescription>{done} of {steps.length} done</CardDescription>
            <div className="bg-muted mt-2 h-1.5 overflow-hidden rounded-full">
              <div className="bg-primary h-full transition-all" style={{ width: `${(done / Math.max(1, steps.length)) * 100}%` }} />
            </div>
          </CardHeader>
          <CardContent>
            <ul className="divide-y">
              {steps.map((s) => {
                const ok = s.done(ctx)
                return (
                  <li key={s.id} className="flex items-start gap-3 py-3">
                    {ok ? <CheckCircle2 className="text-success mt-0.5 size-5 shrink-0" /> : <Circle className="text-muted-foreground mt-0.5 size-5 shrink-0" />}
                    <div className="min-w-0 flex-1">
                      <p className={cn('font-medium', ok && 'text-muted-foreground line-through')}>{s.title}</p>
                      <p className="text-muted-foreground text-sm">{s.detail}</p>
                    </div>
                    {!ok && s.id !== 'palette' && <Button asChild size="sm" variant="outline"><Link to={s.to}>Open</Link></Button>}
                    {!ok && s.id === 'palette' && <span className="text-muted-foreground flex items-center gap-1 text-xs"><Command className="size-3.5" /> K</span>}
                  </li>
                )
              })}
            </ul>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Your areas</CardTitle></CardHeader>
          <CardContent>
            <ul className="space-y-1">
              {ADMIN_NAV.flatMap((g) => g.items).filter((i) => i.to !== '/admin' && (i.perms.length === 0 || any(...i.perms))).map((i) => (
                <li key={i.to}>
                  <Link to={i.to} className="hover:bg-muted flex items-center gap-3 rounded-md px-2 py-2 text-sm">
                    <i.icon className="text-muted-foreground size-4" /> <span className="flex-1">{i.label}</span>
                    {i.shortcut && <kbd className="text-muted-foreground font-mono text-[11px]">g {i.shortcut}</kbd>}
                  </Link>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
