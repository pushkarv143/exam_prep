import { Suspense } from 'react'
import { Navigate, NavLink, Outlet, ScrollRestoration } from 'react-router-dom'
import { BarChart3, FileQuestion, Library, ListChecks, Users } from 'lucide-react'
import { PageLoader } from '@/components/common/States'
import { Navbar } from '@/components/layout/Navbar'
import { cn } from '@/lib/utils'
import { hasRole, useAuthStore } from '@/store/auth'

const LINKS = [
  { to: '/admin/dashboard', label: 'Dashboard', icon: BarChart3, adminOnly: true },
  { to: '/admin/questions', label: 'Question bank', icon: FileQuestion },
  { to: '/admin/tests', label: 'Tests', icon: ListChecks },
  { to: '/admin/series', label: 'Test series', icon: Library, adminOnly: true },
  { to: '/admin/users', label: 'Users', icon: Users, adminOnly: true },
]

/** Staff area: a sidebar on desktop, a scrollable tab row on mobile. */
export function AdminLayout() {
  const user = useAuthStore((s) => s.user)
  const admin = hasRole(user, 'ADMIN')
  const links = LINKS.filter((l) => !l.adminOnly || admin)
  const item = ({ isActive }: { isActive: boolean }) =>
    cn('flex shrink-0 items-center gap-2 rounded-md px-3 py-2 text-sm font-medium whitespace-nowrap',
      isActive ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-muted')

  return (
    <div className="bg-muted/30 flex min-h-screen flex-col">
      <Navbar />
      <div className="mx-auto flex w-full max-w-[1400px] flex-1 flex-col gap-6 px-4 py-6 sm:px-6 lg:flex-row">
        <aside className="lg:w-56 lg:shrink-0">
          <nav className="flex gap-1 overflow-x-auto lg:sticky lg:top-24 lg:flex-col" aria-label="Admin">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} className={item}><l.icon className="size-4" /> {l.label}</NavLink>
            ))}
          </nav>
        </aside>
        <main className="min-w-0 flex-1">
          <Suspense fallback={<PageLoader />}><Outlet /></Suspense>
        </main>
      </div>
      <ScrollRestoration />
    </div>
  )
}

/** /admin → the dashboard for admins, the question bank for teachers. */
export function AdminIndex() {
  const user = useAuthStore((s) => s.user)
  return <Navigate to={hasRole(user, 'ADMIN') ? '/admin/dashboard' : '/admin/questions'} replace />
}
