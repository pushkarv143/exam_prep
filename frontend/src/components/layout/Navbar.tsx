import { useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { BookOpen, LayoutDashboard, Library, LogOut, Menu, Settings, User as UserIcon, X } from 'lucide-react'
import { DarkModeToggle, ThemeMenu } from '@/components/common/ThemeMenu'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useLogout } from '@/hooks/useLogout'
import { initials } from '@/lib/format'
import { cn } from '@/lib/utils'
import { isStaff, useAuthStore } from '@/store/auth'
import { Logo } from './Logo'

interface NavItem {
  to: string
  label: string
  icon: typeof BookOpen
  auth?: boolean
  staff?: boolean
}

const NAV: NavItem[] = [
  { to: '/series', label: 'Test series', icon: Library },
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, auth: true },
  { to: '/my/series', label: 'My series', icon: BookOpen, auth: true },
  { to: '/admin', label: 'Admin', icon: Settings, auth: true, staff: true },
]

export function Navbar() {
  const user = useAuthStore((s) => s.user)
  const signedIn = useAuthStore((s) => s.accessToken !== null)
  const logout = useLogout()
  const [open, setOpen] = useState(false)
  const items = NAV.filter((i) => (!i.auth || signedIn) && (!i.staff || isStaff(user)))

  const link = ({ isActive }: { isActive: boolean }) =>
    cn('rounded-md px-3 py-2 text-sm font-medium transition-colors',
      isActive ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground')

  return (
    <header className="bg-background/90 sticky top-0 z-40 border-b backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Logo to={signedIn ? '/dashboard' : '/'} />
        <nav className="ml-4 hidden items-center gap-1 md:flex" aria-label="Main">
          {items.map((i) => <NavLink key={i.to} to={i.to} className={link}>{i.label}</NavLink>)}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <div className="flex items-center">
            <DarkModeToggle />
            <ThemeMenu />
          </div>
          {signedIn && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="gap-2 px-2" aria-label="Account menu">
                  <span className="bg-primary/10 text-primary grid size-8 place-items-center rounded-full text-xs font-semibold">
                    {initials(user.fullName)}
                  </span>
                  <span className="hidden max-w-32 truncate text-sm sm:inline">{user.fullName}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>
                  <div className="truncate">{user.fullName}</div>
                  <div className="text-muted-foreground truncate text-xs font-normal">{user.email}</div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild><Link to="/profile"><UserIcon /> Profile</Link></DropdownMenuItem>
                <DropdownMenuItem asChild><Link to="/my/series"><BookOpen /> My series</Link></DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" onSelect={() => void logout('/')}>
                  <LogOut /> Log out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <div className="hidden items-center gap-2 sm:flex">
              <Button variant="ghost" asChild><Link to="/login">Log in</Link></Button>
              <Button asChild><Link to="/register">Get started free</Link></Button>
            </div>
          )}
          <Button variant="ghost" size="icon" className="md:hidden" aria-label="Toggle menu" aria-expanded={open}
                  onClick={() => setOpen((o) => !o)}>
            {open ? <X /> : <Menu />}
          </Button>
        </div>
      </div>

      {open && (
        <nav className="border-t px-4 py-3 md:hidden" aria-label="Mobile">
          <div className="flex flex-col gap-1">
            {items.map((i) => (
              <NavLink key={i.to} to={i.to} className={link} onClick={() => setOpen(false)}>
                <span className="flex items-center gap-2"><i.icon className="size-4" /> {i.label}</span>
              </NavLink>
            ))}
            {!signedIn && (
              <div className="mt-2 grid grid-cols-2 gap-2">
                <Button variant="outline" asChild onClick={() => setOpen(false)}><Link to="/login">Log in</Link></Button>
                <Button asChild onClick={() => setOpen(false)}><Link to="/register">Sign up</Link></Button>
              </div>
            )}
          </div>
        </nav>
      )}
    </header>
  )
}
