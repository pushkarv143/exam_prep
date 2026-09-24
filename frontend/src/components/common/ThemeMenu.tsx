import { Check, Laptop, Moon, Palette, Sun } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { ACCENTS, resolvedDark, useThemeStore, type ThemeMode } from '@/store/theme'

const MODES: { id: ThemeMode; label: string; icon: typeof Sun }[] = [
  { id: 'light', label: 'Light', icon: Sun },
  { id: 'dark', label: 'Dark', icon: Moon },
  { id: 'system', label: 'System', icon: Laptop },
]

/** One click flips light ↔ dark (leaving "system" for an explicit choice). */
export function DarkModeToggle({ variant = 'ghost', className }: { variant?: 'ghost' | 'secondary'; className?: string }) {
  const mode = useThemeStore((s) => s.mode)
  const setMode = useThemeStore((s) => s.setMode)
  const dark = resolvedDark(mode)
  return (
    <Button variant={variant} size="icon" className={className} onClick={() => setMode(dark ? 'light' : 'dark')}
            aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'} title={dark ? 'Light mode' : 'Dark mode'}>
      {dark ? <Sun /> : <Moon />}
    </Button>
  )
}

/** Appearance menu: light / dark / system, plus the brand colour. */
export function ThemeMenu({ variant = 'ghost', className }: { variant?: 'ghost' | 'secondary'; className?: string }) {
  const { mode, accent, setMode, setAccent } = useThemeStore()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant={variant} size="icon" className={className} aria-label="Appearance: theme and colour" title="Theme and colour">
          <Palette />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel>Theme</DropdownMenuLabel>
        {MODES.map((m) => (
          <DropdownMenuItem key={m.id} onSelect={(e) => { e.preventDefault(); setMode(m.id) }} aria-checked={mode === m.id} role="menuitemradio">
            <m.icon /> {m.label}
            {mode === m.id && <Check className="ml-auto" />}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuLabel>Colour</DropdownMenuLabel>
        <div className="grid grid-cols-7 gap-1 px-1 pb-1" role="group" aria-label="Brand colour">
          {ACCENTS.map((a) => (
            <DropdownMenuItem key={a.id} onSelect={(e) => { e.preventDefault(); setAccent(a.id) }}
                              role="menuitemradio" aria-checked={accent === a.id} aria-label={a.label} title={a.label}
                              className="justify-center p-1">
              <span className={cn('grid size-6 place-items-center rounded-full ring-offset-2 ring-offset-popover',
                accent === a.id && 'ring-foreground ring-2')} style={{ backgroundColor: a.swatch }}>
                {accent === a.id && <Check className="size-3.5 text-white" />}
              </span>
            </DropdownMenuItem>
          ))}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
