import { NavLink } from 'react-router-dom'
import { BookOpen, LayoutDashboard, Library, User as UserIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

const ITEMS = [
  { to: '/dashboard', label: 'Home', icon: LayoutDashboard },
  { to: '/series', label: 'Series', icon: Library },
  { to: '/my/series', label: 'My tests', icon: BookOpen },
  { to: '/profile', label: 'Profile', icon: UserIcon },
]

/**
 * Thumb-reachable bottom navigation for signed-in students on mobile. Hidden on md+
 * where the top navbar takes over. Respects the safe-area inset on notched phones.
 */
export function BottomNav() {
  return (
    <nav aria-label="Primary"
         className="bg-background/95 fixed inset-x-0 bottom-0 z-40 border-t backdrop-blur md:hidden"
         style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
      <ul className="mx-auto grid max-w-lg grid-cols-4">
        {ITEMS.map((i) => (
          <li key={i.to}>
            <NavLink to={i.to} end={i.to === '/dashboard'}
                     className={({ isActive }) =>
                       cn('flex min-h-14 flex-col items-center justify-center gap-0.5 text-[11px] font-medium transition-colors',
                         isActive ? 'text-primary' : 'text-muted-foreground hover:text-foreground')}>
              {({ isActive }) => (
                <>
                  <i.icon className={cn('size-5', isActive && 'fill-primary/10')} aria-hidden="true" />
                  <span>{i.label}</span>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
